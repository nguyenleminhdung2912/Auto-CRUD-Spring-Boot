package com.project.autocrud.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class OverrideConfig {
    private Map<String, TableOverride> tableOverrides;
    private Map<String, Map<String, ColumnOverride>> columnOverrides;
    private GlobalOverride global;

    @Data
    public static class TableOverride {
        private String[] primaryKey;
        private String endpointPath;
        private Boolean softDelete;
        private Boolean audit;
        private String className;
        private List<String> uniqueColumns;  // ← THÊM ĐỂ SỬA LỖI List vs Set
    }

    @Data
    public static class ColumnOverride {
        private String javaType;
        private Boolean nullable;
        private Boolean unique;
        // FK RELATIONSHIP OVERRIDES
        private String relationshipType;   // "OneToOne", "ManyToOne"
        private String cascade;            // "ALL", "PERSIST", ...
        private String fieldName;          // "userProfile"
        private String mappedBy;           // "userProfile"
        private Boolean orphanRemoval;
    }

    @Data
    public static class GlobalOverride {
        private String packageName;
        private Boolean useLombok = true;
    }
}