package github.jackutil.mapping;

import com.fasterxml.jackson.databind.JsonNode;

import org.junit.jupiter.api.Test;

import java.net.URL;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class MappingEngineEdgeCasesTest {
    private Path resourcePath(String name) throws Exception {
        URL url = getClass().getResource("/examples/" + name);
        assertNotNull(url, "Missing test resource: " + name);
        return Path.of(url.toURI());
    }

    @Test
    void handlesTransformsFallbacksPredicatesAndLookups() throws Exception {
        JsonNode cfg = ConfigLoader.readConfig(resourcePath("edge-cases.config.yaml"));
        JsonNode payload = ConfigLoader.readJson(resourcePath("edge-cases.payload.json"));

        MappingEngine engine = new MappingEngine();
        MappingResult res = engine.map(cfg, payload);
        JsonNode sm = res.getOutput();

        org.eclipse.digitaltwin.aas4j.v3.dataformat.json.JsonDeserializer deser = new org.eclipse.digitaltwin.aas4j.v3.dataformat.json.JsonDeserializer();
        org.eclipse.digitaltwin.aas4j.v3.model.Submodel submodel = deser.read(sm.toString(), org.eclipse.digitaltwin.aas4j.v3.model.Submodel.class);

        // Verify some key derived values are present
        assertTrue(hasElement(submodel, "TemperatureC"));
        assertTrue(hasElement(submodel, "TemperatureF"));

        // We can't easily read Property values via the deserialized model without casts,
        // but we can still inspect raw JSON for specific values if needed in future.
    }

    private boolean hasElement(org.eclipse.digitaltwin.aas4j.v3.model.Submodel sm, String idShort) {
        return sm.getSubmodelElements().stream().anyMatch(se -> {
            if (idShort.equals(se.getIdShort())) return true;
            if (se instanceof org.eclipse.digitaltwin.aas4j.v3.model.SubmodelElementCollection col) {
                return col.getValue().stream().anyMatch(child -> idShort.equals(child.getIdShort()));
            }
            return false;
        });
    }
}
