package net.swordie.ms.config;

import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DatabaseSettingsTest {

    @Test
    void appliesEnvironmentConnectionProperties() {
        DatabaseSettings settings = DatabaseSettings.fromEnvironment(Map.of(
                "DB_HOST", "db",
                "DB_PORT", "3307",
                "DB_NAME", "v206_test",
                "DB_USER", "maplestory",
                "DB_PASSWORD", "secret"
        ));
        Configuration configuration = new Configuration();

        settings.applyTo(configuration);

        assertEquals(
                "jdbc:mysql://db:3307/v206_test?autoReconnect=true&useSSL=false&allowPublicKeyRetrieval=true&useJDBCCompliantTimezoneShift=true&useLegacyDatetimeCode=false&serverTimezone=UTC",
                configuration.getProperty("hibernate.connection.url")
        );
        assertEquals("maplestory", configuration.getProperty("hibernate.connection.username"));
        assertEquals("secret", configuration.getProperty("hibernate.connection.password"));
    }
}
