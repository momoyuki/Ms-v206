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
        ServerSettings settings = ServerSettings.load(tempDir.resolve("missing.properties"), Map.of());

        assertEquals("v206", settings.serverName());
        assertEquals(10, settings.channelCount());
        assertEquals(50, settings.mobExpRate());
        assertEquals(2, settings.mobMesoRate());
        assertEquals(1, settings.mobDropRate());
    }

    @Test
    void loadsConfiguredOperatorValues() throws IOException {
        Path config = tempDir.resolve("server.properties");
        Files.writeString(config, String.join("\n",
                "server.name=Friends",
                "server.message=Welcome",
                "world.id=Bera",
                "channels=3",
                "rate.mob.exp=5",
                "rate.mob.meso=6",
                "rate.mob.drop=7"
        ));

        ServerSettings settings = ServerSettings.load(config, Map.of());

        assertEquals("Friends", settings.serverName());
        assertEquals("Welcome", settings.serverMessage());
        assertEquals(3, settings.channelCount());
        assertEquals(5, settings.mobExpRate());
        assertEquals(6, settings.mobMesoRate());
        assertEquals(7, settings.mobDropRate());
    }

    @Test
    void rejectsChannelCountBelowOne() throws IOException {
        Path config = tempDir.resolve("server.properties");
        Files.writeString(config, "channels=0\n");

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> ServerSettings.load(config, Map.of())
        );

        assertTrue(error.getMessage().contains("channels"));
    }
}
