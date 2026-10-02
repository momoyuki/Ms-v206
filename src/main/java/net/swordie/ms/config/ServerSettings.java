package net.swordie.ms.config;

import net.swordie.ms.enums.WorldId;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;

public record ServerSettings(String serverName, String serverMessage, WorldId worldId, int channelCount,
                             int mobExpRate, int mobMesoRate, int mobDropRate) {
    public static ServerSettings load(Path configPath, Map<String, String> environment) throws IOException {
        Properties properties = new Properties();
        if (Files.exists(configPath)) {
            try (Reader reader = Files.newBufferedReader(configPath, StandardCharsets.UTF_8)) {
                properties.load(reader);
            }
        }
        return new ServerSettings(
                value(properties, environment, "server.name", "SERVER_NAME", "v206"),
                value(properties, environment, "server.message", "SERVER_MESSAGE", "v206"),
                WorldId.valueOf(value(properties, environment, "world.id", "WORLD_ID", "Bera")),
                positiveInt(properties, environment, "channels", "CHANNEL_COUNT", 10),
                positiveInt(properties, environment, "rate.mob.exp", "MOB_EXP_RATE", 50),
                positiveInt(properties, environment, "rate.mob.meso", "MOB_MESO_RATE", 2),
                positiveInt(properties, environment, "rate.mob.drop", "MOB_DROP_RATE", 1)
        );
    }

    private static String value(Properties properties, Map<String, String> environment, String propertyName,
                                String environmentName, String defaultValue) {
        return environment.getOrDefault(environmentName, properties.getProperty(propertyName, defaultValue)).trim();
    }

    private static int positiveInt(Properties properties, Map<String, String> environment, String propertyName,
                                   String environmentName, int defaultValue) {
        String value = value(properties, environment, propertyName, environmentName, Integer.toString(defaultValue));
        try {
            int parsed = Integer.parseInt(value);
            if (parsed < 1) {
                throw new IllegalArgumentException(propertyName + " must be at least 1.");
            }
            return parsed;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(propertyName + " must be a positive integer.", exception);
        }
    }
}
