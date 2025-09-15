**Overview**
- CLI runs the mapping engine against a config and payload.
- Outputs pretty JSON to stdout or writes to a directory.

**Requirements**
- Java 21 (matches `pom.xml` compiler target).
- Maven 3.8+.

**Build & Test**
- Build: `mvn -q -DskipTests package`
- Tests: `mvn -q -DskipTests=false test`

**Usage**
- Map a payload:
  - Command: `mvn -q -DskipTests exec:java -Dexec.args="map --config <file> --payload <file> [--outdir <dir>]"`
  - PowerShell: `mvn -q -DskipTests exec:java "-Dexec.args=map --config <file> --payload <file> --outdir <dir>"`
- Export expected input JSON Schema:
  - Command: `mvn -q -DskipTests exec:java -Dexec.args="schema --config <file> [--out <schema.json>]"`
  - PowerShell: `mvn -q -DskipTests exec:java "-Dexec.args=schema --config <file> --out <schema.json>"`
- Common arguments:
  - `--config`: Mapping config file (`.yaml`, `.yml`, or `.json`).
- map arguments:
  - `--payload`: Input payload JSON.
  - `--outdir`: Optional output directory; writes `<name>.json` using `config.name` or `submodel.idShort`.
- schema arguments:
  - `--out`: Writes the derived JSON Schema to a file (prints to stdout if omitted).

**Examples**
- Demo mapping: `config.yaml` + `payload.json`
  - `mvn -q -DskipTests exec:java -Dexec.args="--config path/to/config.yaml --payload path/to/payload.json --outdir out"`
- Catena‑X SerialPart:
  - `mvn -q -DskipTests exec:java -Dexec.args="--config examples/cx-serial-part.config.yaml --payload examples/cx-serial-part.payload.json --outdir out"`
- Batch 3.0.0:
  - `mvn -q -DskipTests exec:java -Dexec.args="--config examples/cx-batch-3.0.0.config.yaml --payload examples/cx-batch.payload.json --outdir out"`

Note: Sample files are stored under `src/test/resources/examples`. Copy to a working directory or pass absolute paths.

**Output**
- Mapping without `submodel`: Nested plain JSON with targets as path segments.
- Mapping with `submodel`: AAS v3 Submodel JSON generated via AAS4J, containing `submodelElements` and meta.
- Schema export: JSON Schema draft 2020‑12 describing input fields referenced by jsonPath selectors; includes best‑effort constraints (minimum/maximum, pattern, enum, maxLength) and `x-aml-constraints` for full detail.

**Quoting Tips (Windows PowerShell)**
- Quote entire `-Dexec.args=...` to avoid treating args as lifecycle phases:
  - `mvn -q -DskipTests exec:java "-Dexec.args=--config ... --payload ..."`
- Raw mode: `mvn --% -q -DskipTests exec:java -Dexec.args="--config ... --payload ..."`

**Exit Codes**
- `0`: Success.
- `2`: Missing `--config` or `--payload`.

**Logs**
- SLF4J: `slf4j-simple` is included (runtime) to suppress NOP warnings from AAS4J JSON serializer.

**Validation (Optional)**
- The engine uses AAS4J to serialize Submodels; you can independently validate by deserializing with `JsonDeserializer.read(json, Submodel.class)`.
