package github.jackutil.cli;

import com.fasterxml.jackson.databind.JsonNode;

import github.jackutil.mapping.ConfigLoader;
import github.jackutil.mapping.MappingEngine;
import github.jackutil.mapping.MappingResult;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

public class App {
    public static void main(String[] args) throws Exception {
        if (args.length == 0 || Arrays.asList(args).contains("--help")) {
            printHelp();
            return;
        }

        String command = args[0];
        switch (command) {
            case "map" -> runMap(Arrays.copyOfRange(args, 1, args.length));
            case "schema" -> runSchema(Arrays.copyOfRange(args, 1, args.length));
            default -> {
                System.err.println("Unknown command: " + command);
                printHelp();
                System.exit(2);
            }
        }
    }

    private static void runMap(String[] args) throws Exception {
        Path configPath = null;
        Path payloadPath = null;
        Path outDir = null;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--config" -> configPath = Path.of(args[++i]);
                case "--payload" -> payloadPath = Path.of(args[++i]);
                case "--outdir" -> outDir = Path.of(args[++i]);
                case "--help" -> { printHelp(); return; }
            }
        }
        if (configPath == null || payloadPath == null) {
            System.err.println("map: missing --config or --payload");
            System.exit(2);
            return;
        }
        JsonNode config = ConfigLoader.readConfig(configPath);
        JsonNode payload = ConfigLoader.readJson(payloadPath);

        MappingEngine engine = new MappingEngine();
        MappingResult result = engine.map(config, payload);

        String pretty = engine.toPrettyJson(result.getOutput());
        if (outDir != null) {
            Files.createDirectories(outDir);
            String base = deriveBaseName(config);
            Path outFile = outDir.resolve(base + ".json");
            Files.writeString(outFile, pretty, StandardCharsets.UTF_8);
            System.out.println("Wrote: " + outFile.toAbsolutePath());
        } else {
            System.out.println(pretty);
        }
        if (!result.getErrors().isEmpty()) {
            System.err.println("Errors:");
            for (String e : result.getErrors()) System.err.println(" - " + e);
        }
    }

    private static void runSchema(String[] args) throws Exception {
        Path configPath = null;
        Path outFile = null;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--config" -> configPath = Path.of(args[++i]);
                case "--out" -> outFile = Path.of(args[++i]);
                case "--help" -> { printHelp(); return; }
            }
        }
        if (configPath == null) {
            System.err.println("schema: missing --config");
            System.exit(2);
            return;
        }
        JsonNode config = ConfigLoader.readConfig(configPath);
        github.jackutil.mapping.SchemaExporter exporter = new github.jackutil.mapping.SchemaExporter();
        String json = new com.fasterxml.jackson.databind.ObjectMapper()
                .writerWithDefaultPrettyPrinter()
                .writeValueAsString(exporter.exportPayloadSchema(config));
        if (outFile != null) {
            if (outFile.getParent() != null) Files.createDirectories(outFile.toAbsolutePath().getParent());
            Files.writeString(outFile, json, StandardCharsets.UTF_8);
            System.out.println("Wrote schema: " + outFile.toAbsolutePath());
        } else {
            System.out.println(json);
        }
    }

    private static void printHelp() {
        System.out.println("AAS Mapping Language (AML) CLI\n" +
                "\nCommands:\n" +
                "  map    --config <config.(json|yaml|yml)> --payload <payload.json> [--outdir <dir>]\n" +
                "  schema --config <config.(json|yaml|yml)> [--out <schema.json>]\n" +
                "\nNotes:\n" +
                "  - map: reads mapping config and payload; prints or writes mapped JSON (plain or AAS Submodel).\n" +
                "  - schema: derives a best-effort JSON Schema for input data referenced by the config.\n");
    }

    private static String deriveBaseName(JsonNode config) {
        String base = null;
        if (config.hasNonNull("name")) {
            base = config.get("name").asText();
        } else if (config.path("submodel").hasNonNull("idShort")) {
            base = config.path("submodel").get("idShort").asText();
        }
        if (base == null || base.isBlank()) base = "mapping-output";
        // Sanitize filename: allow letters, digits, dash, underscore, dot; replace others with underscore
        base = base.replaceAll("[^A-Za-z0-9._-]", "_");
        if (base.equals(".") || base.equals("..")) base = "mapping-output";
        return base;
    }
}
