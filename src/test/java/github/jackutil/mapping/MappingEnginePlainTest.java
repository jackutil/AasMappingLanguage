package github.jackutil.mapping;

import com.fasterxml.jackson.databind.JsonNode;

import org.junit.jupiter.api.Test;

import java.net.URL;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class MappingEnginePlainTest {
    private Path resourcePath(String name) throws Exception {
        URL url = getClass().getResource("/examples/" + name);
        assertNotNull(url, "Missing test resource: " + name);
        return Path.of(url.toURI());
    }

    @Test
    void mapsDemoConfigToPlainJson() throws Exception {
        JsonNode cfg = ConfigLoader.readConfig(resourcePath("config.yaml"));
        JsonNode payload = ConfigLoader.readJson(resourcePath("payload.json"));

        MappingEngine engine = new MappingEngine();
        MappingResult res = engine.map(cfg, payload);
        JsonNode out = res.getOutput();

        assertTrue(out.has("Process"));
        assertEquals(120, out.at("/Process/Duration").asInt());
        assertEquals("B42", out.at("/Process/BatchId").asText());

        double temp = out.at("/Process/Temperature").asDouble();
        assertTrue(temp >= 30 && temp <= 300, "Temperature within constraint range");
    }
}

