package com.project.autocrud.util;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.*;
import java.util.UUID;

public class TypeMapper {
    public static Class<?> map(String sqlType, boolean isAutoIncrement) {
        return switch (sqlType.toUpperCase()) {
            case "VARCHAR", "CHAR", "TEXT", "STRING" -> String.class;
            case "INT", "INTEGER", "SMALLINT" -> isAutoIncrement ? Long.class : Integer.class;
            case "BIGINT", "BIGSERIAL" -> Long.class;
            case "DECIMAL", "NUMERIC" -> BigDecimal.class;
            case "DOUBLE", "FLOAT", "REAL" -> Double.class;
            case "BOOLEAN", "BIT", "TINYINT" -> Boolean.class;
            case "DATE" -> LocalDate.class;
            case "TIME" -> LocalTime.class;
            case "TIMESTAMP", "DATETIME" -> LocalDateTime.class;
            case "UUID" -> UUID.class;
            case "JSON", "JSONB" -> JsonNode.class;
            case "BYTEA", "BLOB" -> byte[].class;
            default -> Object.class;
        };
    }
}
