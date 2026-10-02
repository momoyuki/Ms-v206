package net.swordie.ms.config;

import net.swordie.ms.ServerConfig;
import net.swordie.ms.ServerConstants;
import net.swordie.ms.constants.GameConstants;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServerSettingsTest {

    @TempDir
    Path tempDir;

    @AfterEach
    void resetRuntimeSettings() {
        ServerSettings defaults = ServerSettings.defaults();
        ServerConfig.apply(defaults);
        ServerConstants.apply(defaults);
        GameConstants.apply(defaults);
    }

    @Test
    void usesCurrentDefaultsWhenConfigurationFileIsMissing() throws IOException {
        ServerSettings settings = ServerSettings.load(tempDir.resolve("missing.yaml"));

        assertEquals("v206", settings.serverName());
        assertEquals(10, settings.channelCount());
        assertEquals(50, settings.mobExpRate());
        assertEquals(2, settings.mobMesoRate());
        assertEquals(1, settings.mobDropRate());
        assertEquals(8484, settings.loginPort());
        assertEquals(8483, settings.apiPort());
    }

    @Test
    void loadsConfiguredOperatorValues() throws IOException {
        Path config = tempDir.resolve("config.yaml");
        Files.writeString(config, """
                server: { name: Friends, message: Welcome, worldId: Bera, channels: 3, userLimit: 20, maxCharacters: 30 }
                network: { loginPort: 9000, apiPort: 9001 }
                rates: { mobExp: 5, mobMeso: 6, mobDrop: 7 }
                gameplay: { hideGmOnLogin: false, startMap: 4000011, hubMap: 100000000 }
                drops: { remainSeconds: 120, ownershipSeconds: 30 }
                events: { randomPortalChance: 0, runeRespawnMinutes: 10, runeCooldownMinutes: 0 }
                """);

        ServerSettings settings = ServerSettings.load(config);

        assertEquals("Friends", settings.serverName());
        assertEquals("Welcome", settings.serverMessage());
        assertEquals(3, settings.channelCount());
        assertEquals(5, settings.mobExpRate());
        assertEquals(6, settings.mobMesoRate());
        assertEquals(7, settings.mobDropRate());
        assertEquals(9000, settings.loginPort());
        assertEquals(30, settings.maxCharacters());
    }

    @Test
    void rejectsChannelCountBelowOne() throws IOException {
        Path config = tempDir.resolve("config.yaml");
        Files.writeString(config, "server: { channels: 0 }\n");

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> ServerSettings.load(config)
        );

        assertTrue(error.getMessage().contains("channels"));
    }

    @Test
    void rejectsUnknownYamlProperty() throws IOException {
        Path config = tempDir.resolve("config.yaml");
        Files.writeString(config, "rates: { mobExpp: 2 }\n");

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> ServerSettings.load(config)
        );

        assertTrue(error.getMessage().contains("rates.mobExpp"));
    }

    @Test
    void rejectsMalformedYaml() throws IOException {
        Path config = tempDir.resolve("config.yaml");
        Files.writeString(config, "server: [channels: 2\n");

        assertThrows(RuntimeException.class, () -> ServerSettings.load(config));
    }

    @Test
    void rejectsInvalidWorldId() throws IOException {
        Path config = tempDir.resolve("config.yaml");
        Files.writeString(config, "server: { worldId: UnknownWorld }\n");

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> ServerSettings.load(config)
        );

        assertTrue(error.getMessage().contains("server.worldId"));
    }

    @Test
    void rejectsInvalidRangesAndNegativeMapIds() throws IOException {
        Path config = tempDir.resolve("config.yaml");
        Files.writeString(config, "events: { randomPortalChance: 1001 }\n");
        assertThrows(IllegalArgumentException.class, () -> ServerSettings.load(config));

        Files.writeString(config, "gameplay: { startMap: -1 }\n");
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> ServerSettings.load(config)
        );

        assertTrue(error.getMessage().contains("gameplay.startMap"));
    }

    @Test
    void appliesOperatorSettingsToRuntimeConstants() {
        ServerSettings settings = new ServerSettings(
                "Friends", "Welcome", net.swordie.ms.enums.WorldId.Bera, 3,
                20, 30, 9000, 9001, 5, 6, 7, false,
                4000011, 100000000, 120, 30, 0, 10, 0
        );

        ServerConfig.apply(settings);
        ServerConstants.apply(settings);
        GameConstants.apply(settings);

        assertEquals("Friends", ServerConfig.SERVER_NAME);
        assertEquals(20, ServerConfig.USER_LIMIT);
        assertEquals(30, ServerConstants.MAX_CHARACTERS);
        assertEquals(9000, ServerConstants.LOGIN_PORT);
        assertEquals(9001, ServerConstants.API_PORT);
        assertEquals(3, GameConstants.CHANNELS_PER_WORLD);
        assertEquals(5, GameConstants.MOB_EXP_RATE);
        assertEquals(6, GameConstants.MOB_MESO_RATE);
        assertEquals(7, GameConstants.MOB_DROP_RATE);
        assertEquals(4000011, GameConstants.PLAYER_START_MAP);
        assertEquals(100000000, GameConstants.PLAYER_HUB_MAP);
        assertEquals(120, GameConstants.DROP_REMAIN_ON_GROUND_TIME);
        assertEquals(30, GameConstants.DROP_REMOVE_OWNERSHIP_TIME);
        assertEquals(0, GameConstants.RANDOM_PORTAL_SPAWN_CHANCE);
        assertEquals(10, GameConstants.RUNE_RESPAWN_TIME);
        assertEquals(0, GameConstants.RUNE_COOLDOWN_TIME);
    }
}
