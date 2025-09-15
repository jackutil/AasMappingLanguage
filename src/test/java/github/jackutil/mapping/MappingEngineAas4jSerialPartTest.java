package github.jackutil.mapping;

import com.fasterxml.jackson.databind.JsonNode;

import org.junit.jupiter.api.Test;

import java.net.URL;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class MappingEngineAas4jSerialPartTest {
    private Path resourcePath(String name) throws Exception {
        URL url = getClass().getResource("/examples/" + name);
        assertNotNull(url, "Missing test resource: " + name);
        return Path.of(url.toURI());
    }

    @Test
    void buildsAas4jSubmodel() throws Exception {
        JsonNode cfg = ConfigLoader.readConfig(resourcePath("cx-serial-part.config.yaml"));
        JsonNode payload = ConfigLoader.readJson(resourcePath("cx-serial-part.payload.json"));

        MappingEngine engine = new MappingEngine();
        MappingResult res = engine.map(cfg, payload);
        JsonNode sm = res.getOutput();

        // Deserialize with AAS4J to validate shape independently of serializer details
        org.eclipse.digitaltwin.aas4j.v3.dataformat.json.JsonDeserializer deser = new org.eclipse.digitaltwin.aas4j.v3.dataformat.json.JsonDeserializer();
        org.eclipse.digitaltwin.aas4j.v3.model.Submodel submodel = deser.read(sm.toString(), org.eclipse.digitaltwin.aas4j.v3.model.Submodel.class);

        assertNotNull(submodel.getId());
        assertEquals("SerialPart", submodel.getIdShort());
        assertNotNull(submodel.getSubmodelElements());
        boolean hasSerial = submodel.getSubmodelElements().stream().anyMatch(e -> "serialNumber".equals(e.getIdShort()));
        assertTrue(hasSerial, "serialNumber element present");
    }

    // old JSON-walking helpers removed; using AAS4J deserializer for sturdiness
}
