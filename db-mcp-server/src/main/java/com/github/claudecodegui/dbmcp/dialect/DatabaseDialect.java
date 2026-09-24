package com.github.claudecodegui.dbmcp.dialect;

public enum DatabaseDialect {
    POSTGRESQL,
    MYSQL,
    ORACLE,
    DAMENG;

    public static DatabaseDialect from(String configuredValue, String jdbcUrl) {
        if (configuredValue != null && !configuredValue.isBlank()) {
            String normalized = configuredValue.trim().toLowerCase();
            return switch (normalized) {
                case "postgres", "postgresql" -> POSTGRESQL;
                case "mysql" -> MYSQL;
                case "oracle" -> ORACLE;
                case "dm", "dameng" -> DAMENG;
                default -> throw new IllegalArgumentException("Unsupported dialect: " + configuredValue);
            };
        }

        if (jdbcUrl != null) {
            String normalizedUrl = jdbcUrl.toLowerCase();
            if (normalizedUrl.startsWith("jdbc:postgresql:")) {
                return POSTGRESQL;
            }
            if (normalizedUrl.startsWith("jdbc:mysql:")) {
                return MYSQL;
            }
            if (normalizedUrl.startsWith("jdbc:oracle:")) {
                return ORACLE;
            }
            if (normalizedUrl.startsWith("jdbc:dm:")) {
                return DAMENG;
            }
        }

        throw new IllegalArgumentException("Unable to infer dialect from configuration");
    }
}
