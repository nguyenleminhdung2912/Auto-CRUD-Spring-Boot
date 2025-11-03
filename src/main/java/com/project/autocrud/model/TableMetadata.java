package com.project.autocrud.model;

import lombok.Data;
import java.util.ArrayList;
import java.util.List;

@Data
public class TableMetadata {
    private String tableName;
    private String className;
    private String endpointPath;
    private List<ColumnMetadata> columns = new ArrayList<>();
    private List<String> primaryKeyColumns = new ArrayList<>();
    private List<Relationship> relationships = new ArrayList<>();
    private List<String> warnings = new ArrayList<>();
    private boolean hasSoftDelete = false;
    private boolean hasAudit = false;
    private boolean compositePrimaryKey = false;
    private boolean joinTable = false;
    private boolean joinTablePure = false;
    public boolean isJoinTable() { return joinTable; }
    public void setJoinTable(boolean joinTable) { this.joinTable = joinTable; }
    public boolean isJoinTablePure() { return joinTablePure; }
    public void setJoinTablePure(boolean joinTablePure) { this.joinTablePure = joinTablePure; }
}
