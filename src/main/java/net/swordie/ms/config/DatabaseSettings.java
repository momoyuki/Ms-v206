package net.swordie.ms.config;

import org.hibernate.cfg.Configuration;

import java.util.Map;

public record DatabaseSettings(String host, int port, String database, String username, String password) {
    private static final String URL_OPTIONS = "?autoReconnect=true&useSSL=false&allowPublicKeyRetrieval=true"
            + "&useJDBCCompliantTimezoneShift=true&useLegacyDatetimeCode=false&serverTimezone=UTC";

    public static DatabaseSettings fromEnvironment(Map<String, String> environment) {
        return new DatabaseSettings(
                environment.getOrDefault("DB_HOST", "127.0.0.1"),
                parsePort(environment.getOrDefault("DB_PORT", "3306")),
                environment.getOrDefault("DB_NAME", "v206"),
                environment.getOrDefault("DB_USER", "root"),
                environment.getOrDefault("DB_PASSWORD", "")
        );
    }

    public void applyTo(Configuration configuration) {
        configuration.setProperty("hibernate.connection.url", jdbcUrl());
        configuration.setProperty("hibernate.connection.username", username);
        configuration.setProperty("hibernate.connection.password", password);
    }

    private String jdbcUrl() {
        return "jdbc:mysql://" + host + ":" + port + "/" + database + URL_OPTIONS;
    }

    private static int parsePort(String value) {
        try {
            int port = Integer.parseInt(value);
            if (port < 1 || port > 65535) {
                throw new IllegalArgumentException("DB_PORT must be between 1 and 65535.");
            }
            return port;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("DB_PORT must be a valid port number.", exception);
        }
    }
}
