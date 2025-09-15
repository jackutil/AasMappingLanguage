package github.jackutil.mapping;

import com.fasterxml.jackson.databind.JsonNode;

import org.junit.jupiter.api.Test;

import java.net.URL;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class MappingEngineBatchTest {
    private Path resourcePath(String name) throws Exception {
        URL url = getClass().getResource("/examples/" + name);
        assertNotNull(url, "Missing test resource: " + name);
        return Path.of(url.toURI());
    }

    @Test
    void buildsBatchSubmodelWithSemanticIds() throws Exception {
        JsonNode cfg = ConfigLoader.readConfig(resourcePath("cx-batch-3.0.0.config.yaml"));
        JsonNode payload = ConfigLoader.readJson(resourcePath("cx-batch.payload.json"));

        MappingEngine engine = new MappingEngine();
        MappingResult res = engine.map(cfg, payload);
        JsonNode sm = res.getOutput();

        org.eclipse.digitaltwin.aas4j.v3.dataformat.json.JsonDeserializer deser = new org.eclipse.digitaltwin.aas4j.v3.dataformat.json.JsonDeserializer();
        org.eclipse.digitaltwin.aas4j.v3.model.Submodel submodel = deser.read(sm.toString(), org.eclipse.digitaltwin.aas4j.v3.model.Submodel.class);

        assertEquals("Batch", submodel.getIdShort());
        assertNotNull(submodel.getSubmodelElements());
        boolean hasSem = submodel.getSubmodelElements().stream().anyMatch(se -> se.getSemanticId() != null);
        assertTrue(hasSem, "expected element semanticIds");
    }
}
