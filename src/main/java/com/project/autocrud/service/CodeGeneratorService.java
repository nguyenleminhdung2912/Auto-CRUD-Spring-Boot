package com.project.autocrud.service;

import com.project.autocrud.model.TableMetadata;
import freemarker.template.Configuration;
import freemarker.template.Template;
import org.springframework.stereotype.Service;

import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class CodeGeneratorService {

    private final Configuration freemarker;

    public CodeGeneratorService() {
        freemarker = new Configuration(Configuration.VERSION_2_3_34);
        freemarker.setClassForTemplateLoading(this.getClass(), "/templates");
        freemarker.setDefaultEncoding("UTF-8");
    }

    public String generateEntity(TableMetadata table, String packageName) throws Exception {
        Template template = freemarker.getTemplate("entity.java.ftl");
        Map<String, Object> data = new HashMap<>();
        data.put("packageName", packageName);
        data.put("table", table);
        data.put("hasJson", table.getColumns().stream().anyMatch(c -> c.isJson()));
        data.put("hasUUID", table.getColumns().stream().anyMatch(c -> c.getJavaType().equals(java.util.UUID.class)));
        data.put("hasTime", table.getColumns().stream().anyMatch(c -> java.time.temporal.Temporal.class.isAssignableFrom(c.getJavaType())));
        data.put("hasBigDecimal", table.getColumns().stream().anyMatch(c -> c.getJavaType().equals(java.math.BigDecimal.class)));

        StringWriter writer = new StringWriter();
        template.process(data, writer);
        return writer.toString();
    }

    public String generateRepository(TableMetadata table, String packageName) throws Exception {
        Template template = freemarker.getTemplate("repository.java.ftl");
        Map<String, Object> data = new HashMap<>();
        data.put("packageName", packageName);
        data.put("table", table);
        data.put("hasSoftDelete", table.isHasSoftDelete());

        String pkType;
        if (table.isCompositePrimaryKey() && !table.isJoinTable()) {
            pkType = table.getClassName() + "Id";
        } else {
            pkType = table.getColumns().stream()
                    .filter(c -> table.getPrimaryKeyColumns().contains(c.getFieldName()))
                    .findFirst()
                    .map(c -> c.getJavaType().getSimpleName())
                    .orElse("Long");
        }

        data.put("pkType", pkType);

        StringWriter writer = new StringWriter();
        template.process(data, writer);
        return writer.toString();
    }

    public String generateService(TableMetadata table, String packageName) throws Exception {
        Template template = freemarker.getTemplate("service.java.ftl");
        Map<String, Object> data = new HashMap<>();
        data.put("packageName", packageName);
        data.put("table", table);
        // determine pk type and field name
        String servicePkType;
        String pkFieldName;
        if (table.isCompositePrimaryKey() && !table.isJoinTable()) {
            servicePkType = table.getClassName() + "Id";
            pkFieldName = "id";
        } else {
            var pkColOpt = table.getColumns().stream().filter(c -> table.getPrimaryKeyColumns().contains(c.getFieldName())).findFirst();
            servicePkType = pkColOpt.map(c -> c.getJavaType().getSimpleName()).orElse("Long");
            pkFieldName = pkColOpt.map(c -> c.getFieldName()).orElse("id");
        }
        data.put("pkType", servicePkType);
        data.put("pkFieldName", pkFieldName);

        StringWriter writer = new StringWriter();
        template.process(data, writer);
        return writer.toString();
    }

    public String generateController(TableMetadata table, String packageName) throws Exception {
        Template template = freemarker.getTemplate("controller.java.ftl");
        Map<String, Object> data = new HashMap<>();
        data.put("packageName", packageName);
        data.put("table", table);
        // controller pkType same as repository/service
        String controllerPkType;
        if (table.isCompositePrimaryKey() && !table.isJoinTable()) {
            controllerPkType = table.getClassName() + "Id";
        } else {
            controllerPkType = table.getColumns().stream()
                    .filter(c -> table.getPrimaryKeyColumns().contains(c.getFieldName()))
                    .findFirst()
                    .map(c -> c.getJavaType().getSimpleName())
                    .orElse("Long");
        }
        data.put("pkType", controllerPkType);

        StringWriter writer = new StringWriter();
        template.process(data, writer);
        return writer.toString();
    }

    private List<GeneratedFile> generateAllFiles(List<TableMetadata> tables, String packageName, String projectName) throws Exception {
        List<GeneratedFile> files = new ArrayList<>();

        for (TableMetadata table : tables) {
            // Entity
            files.add(new GeneratedFile(
                    "src/main/java/" + packageName.replace(".", "/") + "/entity/" + table.getClassName() + ".java",
                    generateEntity(table, packageName)
            ));

            // If composite primary key (and not a pure join table), generate embeddable Id class
            if (table.isCompositePrimaryKey() && !table.isJoinTable()) {
                String idClassPath = "src/main/java/" + packageName.replace(".", "/") + "/entity/" + table.getClassName() + "Id.java";
                StringBuilder idSrc = new StringBuilder();
                idSrc.append("package ").append(packageName).append(".entity;\n\n");
                idSrc.append("import jakarta.persistence.*;\n");
                idSrc.append("import lombok.*;\n");
                idSrc.append("import java.io.Serializable;\n");
                // conditional imports
                boolean needBigDecimal = table.getColumns().stream()
                        .filter(c -> table.getPrimaryKeyColumns().contains(c.getFieldName()))
                        .anyMatch(c -> c.getJavaType() != null && c.getJavaType().equals(java.math.BigDecimal.class));
                boolean needTime = table.getColumns().stream()
                        .filter(c -> table.getPrimaryKeyColumns().contains(c.getFieldName()))
                        .anyMatch(c -> c.getJavaType() != null && java.time.temporal.Temporal.class.isAssignableFrom(c.getJavaType()));
                boolean needUUID = table.getColumns().stream()
                        .filter(c -> table.getPrimaryKeyColumns().contains(c.getFieldName()))
                        .anyMatch(c -> c.getJavaType() != null && c.getJavaType().equals(java.util.UUID.class));
                if (needBigDecimal) idSrc.append("import java.math.BigDecimal;\n");
                if (needTime) idSrc.append("import java.time.*;\n");
                if (needUUID) idSrc.append("import java.util.UUID;\n");

                idSrc.append("\n@Getter @Setter @NoArgsConstructor @AllArgsConstructor @EqualsAndHashCode\n");
                idSrc.append("@Embeddable\n");
                idSrc.append("public class ").append(table.getClassName()).append("Id implements Serializable {\n\n");
                idSrc.append("    private static final long serialVersionUID = 1L;\n\n");

                for (String pkField : table.getPrimaryKeyColumns()) {
                    table.getColumns().stream()
                            .filter(c -> c.getFieldName().equals(pkField))
                            .findFirst()
                            .ifPresent(c -> {
                                String typeName = c.getJavaType() != null ? c.getJavaType().getSimpleName() : "Long";
                                idSrc.append("    @Column(name = \"").append(c.getName()).append("\")\n");
                                idSrc.append("    private ").append(typeName).append(" ").append(c.getFieldName()).append(";\n\n");
                            });
                }

                idSrc.append("}\n");

                files.add(new GeneratedFile(idClassPath, idSrc.toString()));
            }
        }

        return files;
    }
}
