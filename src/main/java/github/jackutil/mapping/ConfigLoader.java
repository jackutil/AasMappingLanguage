package github.jackutil.mapping;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

public final class ConfigLoader {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory());

    private ConfigLoader() {}

    public static JsonNode readConfig(Path path) throws IOException {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);

        byte[] bytes = Files.readAllBytes(path);

        if (name.endsWith(".yaml") || name.endsWith(".yml")) {
            return YAML.readTree(bytes);
        }

        return JSON.readTree(bytes);
    }

    public static JsonNode readJson(Path path) throws IOException {
        return JSON.readTree(Files.readAllBytes(path));
    }
}

