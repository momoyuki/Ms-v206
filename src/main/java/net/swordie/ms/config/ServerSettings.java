package net.swordie.ms.config;

import net.swordie.ms.enums.WorldId;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

public record ServerSettings(
        String serverName, String serverMessage, WorldId worldId, int channelCount,
        int userLimit, int maxCharacters, int loginPort, int apiPort,
        int mobExpRate, int mobMesoRate, int mobDropRate, boolean hideGmOnLogin,
        int startMap, int hubMap, int remainSeconds, int ownershipSeconds,
        int randomPortalChance, int runeRespawnMinutes, int runeCooldownMinutes
) {
    private static final Map<String, Set<String>> SCHEMA = Map.of(
            "server", Set.of("name", "message", "worldId", "channels", "userLimit", "maxCharacters"),
            "network", Set.of("loginPort", "apiPort"),
            "rates", Set.of("mobExp", "mobMeso", "mobDrop"),
            "gameplay", Set.of("hideGmOnLogin", "startMap", "hubMap"),
            "drops", Set.of("remainSeconds", "ownershipSeconds"),
            "events", Set.of("randomPortalChance", "runeRespawnMinutes", "runeCooldownMinutes")
    );

    public static ServerSettings load(Path path) throws IOException {
        if (!Files.exists(path)) {
            return defaults();
        }
        LoaderOptions options = new LoaderOptions();
        options.setAllowDuplicateKeys(false);
        Object parsed;
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            parsed = new Yaml(new SafeConstructor(options)).load(reader);
        }
        if (parsed == null) {
            return defaults();
        }
        if (!(parsed instanceof Map<?, ?> root)) {
            throw new IllegalArgumentException("config must be a YAML mapping.");
        }
        validateKeys(root);
        return new ServerSettings(
                string(root, "server", "name", "v206"), string(root, "server", "message", "v206"), world(root),
                positive(root, "server", "channels", 10), positive(root, "server", "userLimit", 20),
                positive(root, "server", "maxCharacters", 30), port(root, "network", "loginPort", 8484),
                port(root, "network", "apiPort", 8483), positive(root, "rates", "mobExp", 50),
                positive(root, "rates", "mobMeso", 2), positive(root, "rates", "mobDrop", 1),
                bool(root, "gameplay", "hideGmOnLogin", false), nonNegative(root, "gameplay", "startMap", 4000011),
                nonNegative(root, "gameplay", "hubMap", 100000000), positive(root, "drops", "remainSeconds", 120),
                positive(root, "drops", "ownershipSeconds", 30), chance(root),
                positive(root, "events", "runeRespawnMinutes", 10),
                nonNegative(root, "events", "runeCooldownMinutes", 0)
        );
    }

    public static ServerSettings defaults() {
        return new ServerSettings("v206", "v206", WorldId.Bera, 10, 20, 30, 8484, 8483,
                50, 2, 1, false, 4000011, 100000000, 120, 30, 0, 10, 0);
    }

    private static void validateKeys(Map<?, ?> root) {
        for (Object sectionKey : root.keySet()) {
            if (!(sectionKey instanceof String section) || !SCHEMA.containsKey(section)) {
                throw new IllegalArgumentException(sectionKey + " is unknown.");
            }
            Map<?, ?> values = section(root, section);
            for (Object key : values.keySet()) {
                if (!(key instanceof String name) || !SCHEMA.get(section).contains(name)) {
                    throw new IllegalArgumentException(section + "." + key + " is unknown.");
                }
            }
        }
    }

    private static Map<?, ?> section(Map<?, ?> root, String name) {
        Object value = root.get(name);
        if (value == null) return Map.of();
        if (value instanceof Map<?, ?> map) return map;
        throw new IllegalArgumentException(name + " must be a mapping.");
    }

    private static Object value(Map<?, ?> root, String section, String key, Object defaultValue) {
        Map<?, ?> values = section(root, section);
        return values.containsKey(key) ? values.get(key) : defaultValue;
    }

    private static String string(Map<?, ?> root, String section, String key, String defaultValue) {
        return String.valueOf(value(root, section, key, defaultValue)).trim();
    }

    private static int integer(Map<?, ?> root, String section, String key, int defaultValue) {
        try {
            return Integer.parseInt(String.valueOf(value(root, section, key, defaultValue)));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(section + "." + key + " must be an integer.", exception);
        }
    }

    private static int positive(Map<?, ?> root, String section, String key, int defaultValue) {
        int number = integer(root, section, key, defaultValue);
        if (number < 1) throw new IllegalArgumentException(section + "." + key + " must be at least 1.");
        return number;
    }

    private static int nonNegative(Map<?, ?> root, String section, String key, int defaultValue) {
        int number = integer(root, section, key, defaultValue);
        if (number < 0) throw new IllegalArgumentException(section + "." + key + " must be non-negative.");
        return number;
    }

    private static int port(Map<?, ?> root, String section, String key, int defaultValue) {
        int number = positive(root, section, key, defaultValue);
        if (number > 65535) throw new IllegalArgumentException(section + "." + key + " must be at most 65535.");
        return number;
    }

    private static boolean bool(Map<?, ?> root, String section, String key, boolean defaultValue) {
        Object raw = value(root, section, key, defaultValue);
        if (raw instanceof Boolean result) return result;
        throw new IllegalArgumentException(section + "." + key + " must be a boolean.");
    }

    private static WorldId world(Map<?, ?> root) {
        String value = string(root, "server", "worldId", "Bera");
        try {
            return WorldId.valueOf(value);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("server.worldId is invalid: " + value, exception);
        }
    }

    private static int chance(Map<?, ?> root) {
        int value = nonNegative(root, "events", "randomPortalChance", 0);
        if (value > 1000) throw new IllegalArgumentException("events.randomPortalChance must be at most 1000.");
        return value;
    }
}
