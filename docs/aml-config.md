# AML Config Language Guide

This guide teaches you how to write an AML (AAS Mapping Language) configuration to transform an input JSON payload into either plain JSON or an AAS v3 Submodel. It’s written for first‑time users and includes step‑by‑step examples and troubleshooting tips.

## What Is AML?

AML is a declarative mapping language. You describe:

- Where values come from in your input JSON (selectors)
- How to clean/transform them (transforms)
- Where they should go in the output (targets)
- Optional rules for when to map and how to validate (predicates, constraints)

The engine reads your config (YAML/JSON), evaluates the rules against an input payload, and produces:

- Nested plain JSON by default, or
- A fully formed AAS v3 Submodel JSON (if you provide a `submodel` header), built with AAS4J

The formal JSON Schema for AML is packaged with the app at `schema/Aas_Mapping_Language_v1.json`.

## Quick Start

1) Create a file `mapping.yaml` with the minimal structure below.
2) Run the CLI with your config and a JSON payload (see `docs/cli.md`).

Minimal YAML:

```yaml
apiVersion: "aasx.map/v1"
name: "Demo Mapping"
model: { uploadId: "u_demo" }
rules:
  - target: "Process/BatchId"
    source: { jsonPath: "$.meta.batch" }
    transform: [ { op: "trim" } ]
  - target: "Process/Duration"
    source: { jsonPath: "$.line.durationSec" }
    transform: [ { op: "toInteger" } ]
```

Payload:

```json
{ "meta": { "batch": "  B42  " }, "line": { "durationSec": "120" } }
```

Output:

```json
{ "Process": { "BatchId": "B42", "Duration": 120 } }
```

## File Structure (Top‑Level Fields)

- `apiVersion`: must be `aasx.map/v1`
- `name` / `description`: metadata for the mapping
- `model`: `{ uploadId, submodelId?, templateSubmodelId? }` (only `uploadId` is used by the engine today)
- `submodel`: optional AAS Submodel header (see next section). If omitted, output is plain JSON
- `defaults`: optional preferences like timezone/units (advisory in this engine version)
- `variables`: reusable selectors by name
- `rules`: array of mapping rules (required)

## Generating an AAS Submodel (Optional)

Add a `submodel` section to generate a proper AAS v3 Submodel via AAS4J.

```yaml
submodel:
  createIfMissing: true
  idStrategy: { kind: "uuidv4" } # or explicit/urn
  idShort: "SerialPart"
  kind: "INSTANCE"              # or TEMPLATE
  semanticId: "urn:bamm:...#SerialPart"
  administration: { version: "1.1.0", revision: "0" }
  displayName: [ { lang: "en", text: "Catena-X Serial Part" } ]
  description: [ { lang: "en", text: "Traceability submodel" } ]
  elementDefaults: { unitPolicy: "allow-missing", language: "en" }
  initialElements:
    - { path: "manufacturerId", type: "Property", valueType: "string" }
    - { path: "serialNumber",    type: "Property", valueType: "string" }
```

Notes:

- In AAS mode, each `target` path is split on `/` and becomes either a `SubmodelElementCollection` (for intermediate segments) or a `Property` (leaf)
- `initialElements` provide metadata (type, valueType, unit, semanticId) so elements serialize with the right AAS semantics
- The Submodel `id` comes from `idStrategy` (UUID v4 by default)

## Rules (Your Mapping Steps)

Each rule describes a single target value and how to obtain it.

Required:

- `target`: path to write (e.g., `Process/Temperature`)
- `source`: where to read from (or use `project` for composite values)

Optional:

- `fallback`: array of alternative sources if the main `source` is empty
- `transform`: array of transforms applied to the value
- `constraints`: validation rules (if they fail, the rule is skipped and an error is recorded)
- `when`: predicate controlling whether the rule applies (see Predicates)
- `project`: build an object from multiple selectors (for composite AAS types)

### Targets and Paths

- Use `/` for nested structure: `Order/Customer/FullName`
- In plain JSON mode, this creates nested objects; in AAS mode, collections

### Sources

One of the following (all support an inline `transform` list):

- `{ jsonPath: "$.path.to.field" }`: select from the payload
- `{ constant: <any JSON value> }`: use a literal
- `{ var: "name" }`: use a named selector defined under `variables`

### Variables (Reusable Selectors)

Define once and reference with `var: "name"`:

```yaml
variables:
  partId:   { jsonPath: "$.part.id" }
  country:  { jsonPath: "$.meta.country" }
  fallback: { constant: "UNKNOWN" }
```

Variables can reference other variables (chains). Cycles are detected and yield null.

### Fallbacks

```yaml
fallback:
  - { jsonPath: "$.primary" }
  - { jsonPath: "$.backup" }
  - { constant: "N/A" }
```

Tried in order when the main `source` evaluates to an empty value.

### Transforms (Applied in Order)

- `toInteger`: convert to integer
- `toNumber`: convert to number (true→1, false→0; strings parsed when possible)
- `round {places}`: decimal rounding (0–10)
- `trim`: trim string whitespace
- `defaultIfEmpty {value}`: use a default when value is empty/null/blank
- `unitConvert {from,to}`: simple units (C↔F; m↔mm/cm/km)
- `parseDateTime {format=iso8601}`: parse ISO‑8601 timestamps
- `toZoned {zone}`: convert to a specific timezone (outputs ISO string)
- `clamp {min,max}`: clamp numeric range
- `lookup {table:{k:v}}`: map codes
- `regexExtract {pattern,group}`: capture regex group
- `concat {parts:[string|selector], separator?}`: concatenate parts

Tip: Constraints run after transforms. Define ranges/patterns in the final unit/format.

### Constraints

- `range {min,max}`: numeric range (inclusive)
- `regex {pattern}`: full string match
- `enum {values:[...]}`: must equal one of the values
- `maxLength {value}`: string length limit

On failure, the rule is skipped and an error is recorded (others continue).

### Predicates (`when`)

Control whether a rule runs using any/all/not and simple atoms:

```yaml
when:
  any:
    - { jsonPath: "$.mode", equals: "PROD" }
    - { jsonPath: "$.priority", equals: "high" }
```

An atom evaluates a JsonPath and checks JSON equality with `equals`.

## JSONPath Quick Primer

- Root is `$`
- `$.a.b.c` for nested fields
- `$.items[0].name` for arrays
- The engine expects scalar values; arrays are allowed but are generally stringified unless you project/decompose them

## Value Types (AAS)

When generating AAS, set `valueType` in `initialElements`:

- string, boolean, integer, double/number, date, dateTime, time
- Unknown → defaults to string

## Worked Examples

### 1) Minimal Plain Mapping (YAML + Payload + Output)

See the Quick Start snippet at the top of this guide.

### 2) Variables, Concat, Unit Conversion

```yaml
apiVersion: "aasx.map/v1"
name: "VarsAndTransforms"
model: { uploadId: "u_demo" }
variables:
  tempC: { jsonPath: "$.line.tempC" }
  first: { jsonPath: "$.user.first" }
  last:  { jsonPath: "$.user.last" }
rules:
  - target: "Process/TemperatureF"
    source:
      var: "tempC"
      transform:
        - { op: "toNumber" }
        - { op: "unitConvert", from: "C", to: "F" }
        - { op: "round", places: 1 }
  - target: "User/FullName"
    source:
      constant: ""
      transform:
        - op: "concat"
          separator: " "
          parts: [ { var: "first" }, { var: "last" } ]
        - { op: "trim" }
```

Payload:

```json
{ "line": { "tempC": " 180.49 " }, "user": { "first": " Jane", "last": "Doe  " } }
```

Output (excerpt):

```json
{ "Process": { "TemperatureF": 356.9 }, "User": { "FullName": "Jane Doe" } }
```

### 3) AAS Submodel Example (Catena‑X SerialPart, excerpt)

```yaml
apiVersion: "aasx.map/v1"
name: "CatenaX_SerialPart_v1"
model: { uploadId: "u_demo" }
submodel:
  createIfMissing: true
  idStrategy: { kind: "uuidv4" }
  idShort: "SerialPart"
  kind: "INSTANCE"
  semanticId: "urn:bamm:io.catenax.serial_part:1.1.0#SerialPart"
  initialElements:
    - { path: "manufacturerId", type: "Property", valueType: "string" }
    - { path: "serialNumber",    type: "Property", valueType: "string" }
variables:
  manufacturerId: { jsonPath: "$.part.manufacturer.bpn" }
  serialNumber:   { jsonPath: "$.part.serial" }
rules:
  - target: "manufacturerId"
    source: { var: "manufacturerId" }
  - target: "serialNumber"
    source: { var: "serialNumber" }
```

Output is a valid AAS Submodel (AAS4J JSON) with `submodelElements` for `manufacturerId` and `serialNumber`.

### 4) Constraints and Predicates

```yaml
- target: "Process/TemperatureC"
  source: { jsonPath: "$.line.temp" }
  transform: [ { op: "toNumber" } ]
  constraints: [ { kind: "range", min: 0, max: 500 } ]
  when:
    any: [ { jsonPath: "$.mode", equals: "PROD" } ]
```

## Best Practices

- Put selector logic into `variables`, apply transforms in rules → more reusable configs
- Always normalize units before validating ranges (constraints run last)
- Provide `semanticId`/`valueType` in `initialElements` for AAS outputs
- Prefer descriptive, stable target paths (e.g., `Order/Customer/FullName`)
- Use `project` if you need to assemble composite AAS element values

## Troubleshooting

- “Unsupported or missing apiVersion”: Add `apiVersion: "aasx.map/v1"`
- Missing values: Check JsonPath (missing leaves become null). Use `defaultIfEmpty` if appropriate
- Constraint failures: The rule is skipped. Verify ranges/regex and ensure transforms (unitConvert/trim) run first
- Variable cycles: Engine returns null; break the cycle or use constants
- Validate AAS output: Use AAS4J `JsonDeserializer.read(json, Submodel.class)`

## Reference

- JSON Schema: `schema/Aas_Mapping_Language_v1.json`
- Code:
  - `MappingEngine`: rule evaluation, transforms, constraints, predicates
  - `JsonUtils`: helper functions (round, trim, dates, units, regex)
  - `Aas4jSubmodelFactory`: builds/serializes the AAS Submodel with AAS4J

