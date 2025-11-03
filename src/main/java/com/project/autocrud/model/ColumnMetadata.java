package com.project.autocrud.model;

import lombok.Data;

@Data
public class ColumnMetadata {
    private String name;
    private String fieldName;
    private Class<?> javaType;
    private boolean nullable = true;
    private boolean unique = false;
    private boolean autoIncrement = false;
    private boolean primaryKey = false;
    private boolean foreignKey = false;
    private boolean notNull = false;
    private String defaultValue;
    private Integer length;
    private Integer precision;
    private Integer scale;
    private String referencedTable;     // e.g., "user_profiles"
    private String referencedColumn;    // e.g., "id"
    private String relationshipType;    // "OneToOne", "ManyToOne", "OneToMany"
    private String cascade;             // "ALL", "PERSIST", ...
    private String mappedBy;            // for bidirectional
    private boolean orphanRemoval;

    // Audit
    private boolean createdAt = false;
    private boolean updatedAt = false;
    private boolean deletedAt = false;
    public boolean isCreatedAt() { return createdAt; }
    public boolean isUpdatedAt() { return updatedAt; }
    public boolean isDeletedAt() { return deletedAt; }

    public void setCreatedAt(boolean createdAt) { this.createdAt = createdAt; }
    public void setUpdatedAt(boolean updatedAt) { this.updatedAt = updatedAt; }
    public void setDeletedAt(boolean deletedAt) { this.deletedAt = deletedAt; }

    // Enum
    private boolean isEnum = false;
    private String enumName;
    private String[] enumValues;

    // SQL type
    private String sqlType;
    public String getSqlType() { return sqlType; }
    public void setSqlType(String sqlType) { this.sqlType = sqlType; }

    // JSON flag
    private boolean isJson = false;
    public boolean isJson() { return isJson; }
    public void setJson(boolean json) { isJson = json; }
}
