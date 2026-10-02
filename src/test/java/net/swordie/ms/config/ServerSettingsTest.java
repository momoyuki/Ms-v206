package net.swordie.ms.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServerSettingsTest {

    @TempDir
    Path tempDir;

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
}
