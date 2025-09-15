package github.jackutil.mapping;

import com.fasterxml.jackson.databind.JsonNode;

import org.junit.jupiter.api.Test;

import java.net.URL;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class ConfigLoaderTest {
    private Path resourcePath(String name) throws Exception {
        URL url = getClass().getResource("/examples/" + name);
        assertNotNull(url, "Missing test resource: " + name);
        return Path.of(url.toURI());
    }

    @Test
    void loadsYamlConfig() throws Exception {
        JsonNode cfg = ConfigLoader.readConfig(resourcePath("config.yaml"));
        assertEquals("aasx.map/v1", cfg.get("apiVersion").asText());
        assertTrue(cfg.has("rules"));
    }

    @Test
    void loadsJsonPayload() throws Exception {
        JsonNode payload = ConfigLoader.readJson(resourcePath("payload.json"));
        assertTrue(payload.has("line"));
    }
}

