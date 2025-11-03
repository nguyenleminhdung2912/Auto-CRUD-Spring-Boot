package com.project.autocrud.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.autocrud.config.OverrideConfig;
import com.project.autocrud.model.*;
import com.project.autocrud.parser.SqlParser;
import com.project.autocrud.util.NameUtils;
import com.project.autocrud.util.TypeMapper;
import net.sf.jsqlparser.statement.create.table.*;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class SchemaAnalyzerService {

    private static final Pattern NEXTVAL = Pattern.compile("nextval\\('(.+?)'\\)");
    private final ObjectMapper objectMapper = new ObjectMapper();

    public List<TableMetadata> analyze(String sql, String overrideJson) throws Exception {
        List<CreateTable> tables = SqlParser.parse(sql);
        OverrideConfig overrides = parseOverrides(overrideJson);
        List<TableMetadata> result = new ArrayList<>();

        // First pass: process all tables
        Map<String, TableMetadata> tableMap = new HashMap<>();
        for (CreateTable table : tables) {
            TableMetadata tm = processTable(table, tables, overrides);
            result.add(tm);
            tableMap.put(tm.getTableName().toLowerCase(), tm);
        }

        // Second pass: detect and enrich relationships
        for (TableMetadata tm : result) {
            detectForeignKeys(tm, tables, tableMap, overrides);
        }

        // Third pass: mark join tables (composite PK where PK cols are all FKs)
        markJoinTables(result);

        return result;
    }

    private OverrideConfig parseOverrides(String json) {
        if (json == null || json.trim().isEmpty()) return new OverrideConfig();
        try {
            return objectMapper.readValue(json, OverrideConfig.class);
        } catch (Exception e) {
            System.err.println("Failed to parse overrides: " + e.getMessage());
            return new OverrideConfig();
        }
    }

    private TableMetadata processTable(CreateTable table, List<CreateTable> allTables, OverrideConfig overrides) {
        String tableName = table.getTable().getName();
        TableMetadata tm = new TableMetadata();
        tm.setTableName(tableName);
        tm.setClassName(NameUtils.toPascalCase(tableName));
        tm.setEndpointPath(NameUtils.toKebabCase(tableName));

        // Apply table-level overrides
        OverrideConfig.TableOverride tableOverride = overrides.getTableOverrides() != null
                ? overrides.getTableOverrides().get(tableName) : null;

        if (tableOverride != null) {
            if (tableOverride.getPrimaryKey() != null) {
                tm.getPrimaryKeyColumns().addAll(Arrays.asList(tableOverride.getPrimaryKey()));
            }
            if (tableOverride.getEndpointPath() != null) tm.setEndpointPath(tableOverride.getEndpointPath());
            if (tableOverride.getClassName() != null) tm.setClassName(tableOverride.getClassName());
            if (Boolean.TRUE.equals(tableOverride.getSoftDelete())) tm.setHasSoftDelete(true);
            if (Boolean.TRUE.equals(tableOverride.getAudit())) tm.setHasAudit(true);
        }

        List<ColumnMetadata> cols = new ArrayList<>();
        for (ColumnDefinition colDef : table.getColumnDefinitions()) {
            cols.add(processColumn(colDef, tableName, overrides));
        }

        detectPrimaryKey(tm, cols, table);
        // Ensure PK columns have concrete Java types (avoid Object)
        adjustPrimaryKeyTypes(tm, cols);
        detectAuditFields(cols);
        detectUniqueIndexes(table, cols);
        tm.setColumns(cols);
        tm.setRelationships(new ArrayList<>()); // Initialize
        return tm;
    }

    private ColumnMetadata processColumn(ColumnDefinition colDef, String tableName, OverrideConfig overrides) {
        ColumnMetadata cm = new ColumnMetadata();
        cm.setName(colDef.getColumnName());
        cm.setFieldName(NameUtils.toCamelCase(colDef.getColumnName()));

        String type = colDef.getColDataType().getDataType();
        cm.setSqlType(type);
        boolean isAutoInc = isAutoIncrement(colDef, type);
        cm.setAutoIncrement(isAutoInc);
        Class<?> mapped = TypeMapper.map(type, isAutoInc);
        // If JSON mapped to JsonNode, we'll treat it as String in generated entities
        if (mapped != null && mapped.getSimpleName().equals("JsonNode")) {
            cm.setJavaType(String.class);
            cm.setJson(true);
        } else if (mapped != null && !mapped.equals(Object.class)) {
            cm.setJavaType(mapped);
        } else {
            // Unknown mapping: try heuristics
            if (isAutoInc) {
                cm.setJavaType(Long.class);
            } else {
                String lower = colDef.getColumnName().toLowerCase();
                if ("id".equals(lower) || lower.endsWith("_id")) {
                    cm.setJavaType(Long.class);
                } else {
                    cm.setJavaType(Object.class);
                }
            }
        }

        List<String> specs = colDef.getColumnSpecs();
        cm.setNullable(specs == null || !specs.contains("NOT NULL"));
        cm.setNotNull(!cm.isNullable());

        String def = specs != null ? specs.stream()
                .filter(s -> s.toUpperCase().startsWith("DEFAULT"))
                .map(s -> s.substring(7).trim())
                .findFirst().orElse(null) : null;
        cm.setDefaultValue(def);

        // Column override
        Map<String, OverrideConfig.ColumnOverride> colOverrides = overrides.getColumnOverrides() != null
                ? overrides.getColumnOverrides().get(tableName) : null;
        if (colOverrides != null && colOverrides.containsKey(colDef.getColumnName())) {
            OverrideConfig.ColumnOverride co = colOverrides.get(colDef.getColumnName());
            if (co.getJavaType() != null) cm.setJavaType(resolveType(co.getJavaType()));
            if (co.getNullable() != null) {
                cm.setNullable(co.getNullable());
                cm.setNotNull(!co.getNullable());
            }
            if (co.getUnique() != null) cm.setUnique(co.getUnique());
        }

        return cm;
    }

    private Class<?> resolveType(String type) {
        return switch (type.toLowerCase()) {
            case "string" -> String.class;
            case "long" -> Long.class;
            case "int" -> Integer.class;
            case "boolean" -> Boolean.class;
            case "localdate" -> java.time.LocalDate.class;
            case "localdatetime" -> java.time.LocalDateTime.class;
            case "bigdecimal" -> java.math.BigDecimal.class;
            case "jsonnode" -> com.fasterxml.jackson.databind.JsonNode.class;
            default -> Object.class;
        };
    }

    private boolean isAutoIncrement(ColumnDefinition col, String type) {
        List<String> specs = col.getColumnSpecs();
        if (specs != null && specs.contains("IDENTITY")) return true;
        if (type.toUpperCase().contains("SERIAL")) return true;
        if (specs != null) {
            String def = specs.stream()
                    .filter(s -> s.toUpperCase().startsWith("DEFAULT"))
                    .map(s -> s.substring(7).trim())
                    .findFirst().orElse("");
            return NEXTVAL.matcher(def).find();
        }
        return false;
    }

    private void detectPrimaryKey(TableMetadata tm, List<ColumnMetadata> cols, CreateTable table) {
        List<Index> indexes = table.getIndexes();
        if (indexes != null) {
            for (Index index : indexes) {
                if ("PRIMARY KEY".equalsIgnoreCase(index.getType())) {
                    List<String> pkCols = index.getColumnsNames();
                    pkCols.forEach(name -> {
                        cols.stream()
                                .filter(c -> c.getName().equals(name))
                                .findFirst()
                                .ifPresent(c -> {
                                    c.setPrimaryKey(true);
                                    tm.getPrimaryKeyColumns().add(c.getFieldName());
                                });
                    });

                    // ĐÁNH DẤU COMPOSITE PK
                    if (pkCols.size() > 1) {
                        tm.setCompositePrimaryKey(true);  // ĐÃ CÓ METHOD
                    }
                    return;
                }
            }
        }

        List<ColumnMetadata> autoInc = cols.stream().filter(ColumnMetadata::isAutoIncrement).toList();
        if (autoInc.size() == 1) {
            autoInc.get(0).setPrimaryKey(true);
            tm.getPrimaryKeyColumns().add(autoInc.get(0).getFieldName());
            return;
        }

        List<ColumnMetadata> candidates = cols.stream()
                .filter(c -> "id".equalsIgnoreCase(c.getName()) || c.getName().toLowerCase().endsWith("_id"))
                .toList();

        if (candidates.size() == 1) {
            candidates.get(0).setPrimaryKey(true);
            tm.getPrimaryKeyColumns().add(candidates.get(0).getFieldName());
        } else if (candidates.isEmpty()) {
            tm.getWarnings().add("NO_PK: No primary key found for " + tm.getTableName());
        } else {
            tm.getWarnings().add("AMBIGUOUS_PK: Multiple candidates: " +
                    candidates.stream().map(ColumnMetadata::getName).collect(Collectors.toList()));
        }
    }

    private void adjustPrimaryKeyTypes(TableMetadata tm, List<ColumnMetadata> cols) {
        for (String pkField : tm.getPrimaryKeyColumns()) {
            cols.stream()
                    .filter(c -> c.getFieldName().equals(pkField))
                    .findFirst()
                    .ifPresent(c -> {
                        Class<?> jt = c.getJavaType();
                        if (jt == null || jt.equals(Object.class)) {
                            String sql = c.getSqlType() != null ? c.getSqlType().toUpperCase() : "";
                            if (c.isAutoIncrement() || sql.contains("BIG") || sql.contains("SERIAL")) {
                                c.setJavaType(Long.class);
                            } else if (sql.contains("INT") || sql.contains("INTEGER") || sql.contains("SMALLINT")) {
                                c.setJavaType(Integer.class);
                            } else if (sql.contains("BIGINT")) {
                                c.setJavaType(Long.class);
                            } else {
                                // default to Long for IDs
                                c.setJavaType(Long.class);
                            }
                        }
                    });
        }
    }

    private void detectForeignKeys(TableMetadata tm, List<CreateTable> allTables, Map<String, TableMetadata> tableMap, OverrideConfig overrides) {
        List<ForeignKeyIndex> explicitFks = extractExplicitForeignKeys(tm.getTableName(), allTables);
        Set<String> targetTables = tableMap.keySet();

        for (ColumnMetadata col : tm.getColumns()) {
            String colName = col.getName();

            if (tm.getRelationships().stream().anyMatch(r -> r.getFkColumn() != null && r.getFkColumn().equals(colName))) {
                continue;
            }

            // 1. EXPLICIT FK
            Optional<ForeignKeyIndex> explicitFk = explicitFks.stream()
                    .filter(fk -> fk.getColumnsNames().contains(colName))
                    .findFirst();

            if (explicitFk.isPresent()) {
                ForeignKeyIndex fk = explicitFk.get();
                String targetTable = fk.getTable().getName().toLowerCase();
                TableMetadata target = tableMap.get(targetTable);
                if (target == null) continue;

                Relationship rel = createRelationship(col, target, true, overrides, tm.getTableName());
                rel.setInferred(false);
                tm.getRelationships().add(rel);
                col.setForeignKey(true);

                continue;
            }

            // 2. INFERENCE: _id → target table
            if (colName.toLowerCase().endsWith("_id")) {
                String base = colName.substring(0, colName.length() - 3);
                String candidate = targetTables.stream()
                        .filter(t -> t.equals(base) || t.equals(base + "s") || t.equals(base + "es"))
                        .findFirst().orElse(null);

                if (candidate != null) {
                    TableMetadata target = tableMap.get(candidate);
                    if (target != null && !target.getTableName().equals(tm.getTableName())) {
                        Relationship rel = createRelationship(col, target, false, overrides, tm.getTableName());
                        rel.setInferred(true);
                        tm.getRelationships().add(rel);
                        col.setForeignKey(true);
                    }
                }
            }
        }

        // Mark FK columns in column list
        tm.getRelationships().forEach(rel -> {
            if (rel.getFkColumn() == null) return;
            tm.getColumns().stream()
                    .filter(c -> c.getName().equals(rel.getFkColumn()))
                    .findFirst()
                    .ifPresent(c -> c.setForeignKey(true));
        });
    }

    private void markJoinTables(List<TableMetadata> tables) {
        // Detect pure join tables (only PK columns exist and they are exactly two FKs) and create ManyToMany on targets
        for (TableMetadata t : tables) {
            if (!t.isCompositePrimaryKey()) continue;
            // count columns and pk columns: pure join table must have only PK columns (no extra payload)
            if (t.getColumns().size() != t.getPrimaryKeyColumns().size()) continue; // has extra columns -> not pure
            if (t.getPrimaryKeyColumns().size() != 2) continue; // only handle 2-column join tables

            // collect the two FK relationships
            List<Relationship> rels = t.getRelationships().stream().filter(r -> r.getFkColumn() != null).toList();
            if (rels.size() != 2) continue;

            Relationship r1 = rels.get(0);
            Relationship r2 = rels.get(1);
            // ensure they reference different target tables
            if (r1.getTargetTable().equalsIgnoreCase(r2.getTargetTable())) continue;

            // mark as pure join table
            t.setJoinTablePure(true);
            t.setJoinTable(true);

            // find target TableMetadata objects
            Optional<TableMetadata> optA = tables.stream().filter(x -> x.getTableName().equalsIgnoreCase(r1.getTargetTable())).findFirst();
            Optional<TableMetadata> optB = tables.stream().filter(x -> x.getTableName().equalsIgnoreCase(r2.getTargetTable())).findFirst();
            if (optA.isEmpty() || optB.isEmpty()) continue;
            TableMetadata A = optA.get();
            TableMetadata B = optB.get();

            // create ManyToMany on A owning side, and mappedBy on B
            String fieldA = NameUtils.pluralize(NameUtils.toCamelCase(B.getTableName()));
            String fieldB = NameUtils.pluralize(NameUtils.toCamelCase(A.getTableName()));

            // avoid duplicates
            boolean existsA = A.getRelationships().stream().anyMatch(r -> r.getFieldName() != null && r.getFieldName().equals(fieldA));
            boolean existsB = B.getRelationships().stream().anyMatch(r -> r.getFieldName() != null && r.getFieldName().equals(fieldB));

            if (!existsA) {
                Relationship ma = new Relationship();
                ma.setRelationshipType("ManyToMany");
                ma.setTargetTable(B.getTableName());
                ma.setTargetClass(B.getClassName());
                ma.setFieldName(fieldA);
                ma.setJoinTableName(t.getTableName());
                ma.setJoinColumn(r1.getFkColumn());
                ma.setInverseJoinColumn(r2.getFkColumn());
                // owning side
                A.getRelationships().add(ma);
            }

            if (!existsB) {
                Relationship mb = new Relationship();
                mb.setRelationshipType("ManyToMany");
                mb.setTargetTable(A.getTableName());
                mb.setTargetClass(A.getClassName());
                mb.setFieldName(fieldB);
                mb.setMappedBy(fieldA); // mappedBy refers to A's field name
                B.getRelationships().add(mb);
            }
        }
    }

    private List<ForeignKeyIndex> extractExplicitForeignKeys(String tableName, List<CreateTable> allTables) {
        return allTables.stream()
                .filter(t -> t.getTable().getName().equalsIgnoreCase(tableName))
                .findFirst()
                .map(CreateTable::getIndexes)
                .orElse(Collections.emptyList())
                .stream()
                .filter(i -> "FOREIGN KEY".equalsIgnoreCase(i.getType()))
                .map(i -> (ForeignKeyIndex) i)
                .toList();
    }

    private Relationship createRelationship(ColumnMetadata col, TableMetadata target, boolean explicit, OverrideConfig overrides, String sourceTable) {
        Relationship rel = new Relationship();
        rel.setFkColumn(col.getName());
        rel.setTargetTable(target.getTableName());
        rel.setTargetClass(target.getClassName());
        rel.setFieldName(NameUtils.toCamelCase(target.getTableName()));
        rel.setNullable(col.isNullable());
        rel.setInferred(!explicit);

        // Determine relationship type
        boolean isUnique = col.isUnique() || hasUniqueConstraint(sourceTable, col.getName(), overrides);
        rel.setRelationshipType(isUnique ? "OneToOne" : "ManyToOne");

        // Apply column-level override
        OverrideConfig.ColumnOverride colOverride = getColumnOverride(overrides, sourceTable, col.getName());
        if (colOverride != null) {
            if (colOverride.getRelationshipType() != null) {
                rel.setRelationshipType(colOverride.getRelationshipType());
            }
            if (colOverride.getCascade() != null) {
                rel.setCascade(colOverride.getCascade());
            }
            if (colOverride.getFieldName() != null) {
                rel.setFieldName(colOverride.getFieldName());
            }
            if (colOverride.getMappedBy() != null) {
                rel.setMappedBy(colOverride.getMappedBy());
            }
            if (colOverride.getOrphanRemoval() != null) {
                rel.setOrphanRemoval(colOverride.getOrphanRemoval());
            }
        }

        return rel;
    }

    private boolean hasUniqueConstraint(String tableName, String colName, OverrideConfig overrides) {
        // Check UNIQUE index
        OverrideConfig.TableOverride tableOverride = overrides.getTableOverrides() != null
                ? overrides.getTableOverrides().get(tableName) : null;
        if (tableOverride != null && tableOverride.getUniqueColumns() != null) {
            return tableOverride.getUniqueColumns().contains(colName);
        }
        return false;
    }

    private OverrideConfig.ColumnOverride getColumnOverride(OverrideConfig overrides, String tableName, String colName) {
        if (overrides.getColumnOverrides() == null) return null;
        Map<String, OverrideConfig.ColumnOverride> map = overrides.getColumnOverrides().get(tableName);
        return map != null ? map.get(colName) : null;
    }

    private void detectAuditFields(List<ColumnMetadata> cols) {
        cols.forEach(c -> {
            String name = c.getName().toLowerCase();
            if (name.matches("created.?at|createdat|create_at")) c.setCreatedAt(true);
            if (name.matches("updated.?at|updatedat|update_at")) c.setUpdatedAt(true);
            if (name.matches("deleted.?at|deletedat|delete_at")) c.setDeletedAt(true);
        });
    }

    private void detectUniqueIndexes(CreateTable table, List<ColumnMetadata> cols) {
        List<Index> indexes = table.getIndexes();
        if (indexes != null) {
            for (Index index : indexes) {
                if ("UNIQUE".equalsIgnoreCase(index.getType()) && index.getColumnsNames().size() == 1) {
                    String colName = index.getColumnsNames().get(0);
                    cols.stream().filter(c -> c.getName().equals(colName))
                            .findFirst().ifPresent(c -> c.setUnique(true));
                }
            }
        }
    }
}

