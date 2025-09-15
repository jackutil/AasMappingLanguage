# Example: Advanced Mapping Features

This example demonstrates advanced AML features: variables with indirection, fallbacks, predicates (`when`), and a wide range of transforms (concat, lookup, regexExtract, unitConvert, parseDateTime, toZoned, clamp, defaultIfEmpty). It outputs an AAS Submodel, but the same rules work for plain JSON if you omit the `submodel` header.

## Input Payload (JSON)

```json
{
  "order": { "id": "ORD-777", "priority": "high" },
  "env": { "tz": "Europe/Berlin" },
  "line": { "temps": { "celsius": " 180.49 ", "fahrenheit": " 356.882 " }, "lengthMm": 1234.5, "speed": null },
  "meta": { "batch": "  B42  ", "tags": ["A", "B"], "mode": "AUTO", "category": "blue" },
  "date": "2024-09-02T10:15:30+02:00",
  "user": { "first": "  Jane", "last": "Doe  " },
  "text": "Part SN: SN-001-ABC code=42",
  "lookupKey": "DE",
  "optional": {}
}
```

## Mapping Config (YAML)

```yaml
apiVersion: "aasx.map/v1"
name: "EdgeCases_v1"
model: { uploadId: "u_demo" }

submodel:
  createIfMissing: true
  idStrategy: { kind: "uuidv4" }
  idShort: "EdgeCases"
  kind: "INSTANCE"
  semanticId: "urn:example:edge:submodel:1#EdgeCases"
  elementDefaults: { unitPolicy: "allow-missing", language: "en" }
  initialElements:
    - { path: "Process/TemperatureC",      type: "Property", valueType: "double" }
    - { path: "Process/TemperatureF",      type: "Property", valueType: "double" }
    - { path: "Dimensions/LengthM",        type: "Property", valueType: "double", unit: "m" }
    - { path: "Order/Id",                   type: "Property", valueType: "string" }
    - { path: "Order/Priority",             type: "Property", valueType: "string" }
    - { path: "Order/Customer/FullName",    type: "Property", valueType: "string" }
    - { path: "Derived/Serial",             type: "Property", valueType: "string" }
    - { path: "Lookup/CountryName",         type: "Property", valueType: "string" }
    - { path: "Speed/Normalized",           type: "Property", valueType: "double" }
    - { path: "Date/UTC",                   type: "Property", valueType: "dateTime" }
    - { path: "Meta/Mode",                  type: "Property", valueType: "string" }
    - { path: "Meta/Category",              type: "Property", valueType: "string" }
    - { path: "Optional/TZ",                type: "Property", valueType: "string" }
    - { path: "Unsafe/CycledVar",           type: "Property", valueType: "string" }

variables:
  tempC:       { jsonPath: "$.line.temps.celsius" }
  tempF:       { var: "tempC" }           # indirection (no-op here)
  firstName:   { jsonPath: "$.user.first" }
  lastName:    { jsonPath: "$.user.last" }
  countryCode: { jsonPath: "$.lookupKey" }
  selfCycle:   { var: "selfCycle" }       # intentional cycle to test detection

rules:
  - target: "Process/TemperatureC"
    source:
      var: "tempC"
      transform: [ { op: "trim" }, { op: "toNumber" }, { op: "round", places: 1 } ]
    constraints: [ { kind: "range", min: 0, max: 1000 } ]

  - target: "Process/TemperatureF"
    source:
      var: "tempC"
      transform: [ { op: "trim" }, { op: "toNumber" }, { op: "unitConvert", from: "C", to: "F" }, { op: "round", places: 1 } ]

  - target: "Dimensions/LengthM"
    source:
      jsonPath: "$.line.lengthMm"
      transform: [ { op: "toNumber" }, { op: "unitConvert", from: "mm", to: "m" } ]

  - target: "Order/Id"
    source: { jsonPath: "$.order.id" }
    transform: [ { op: "trim" } ]
    constraints: [ { kind: "regex", pattern: "^[A-Z]{3}-[0-9]+$" } ]

  - target: "Order/Priority"
    source: { jsonPath: "$.order.priority" }
    when: { any: [ { jsonPath: "$.order.priority", equals: "high" } ] }

  - target: "Order/Customer/FullName"
    source:
      constant: ""
      transform:
        - op: "concat"
          separator: " "
          parts: [ { var: "firstName" }, { var: "lastName" } ]
        - { op: "trim" }
    constraints: [ { kind: "maxLength", value: 100 } ]

  - target: "Derived/Serial"
    source: { jsonPath: "$.text" }
    transform: [ { op: "regexExtract", pattern: "SN-([A-Z0-9-]+)", group: 1 } ]
    constraints: [ { kind: "maxLength", value: 32 } ]

  - target: "Lookup/CountryName"
    source:
      var: "countryCode"
    transform:
      - op: "lookup"
        table: { DE: "Germany", AT: "Austria", CH: "Switzerland" }
      - { op: "defaultIfEmpty", value: "Unknown" }

  - target: "Speed/Normalized"
    source: { jsonPath: "$.line.speed" }
    fallback:
      - { jsonPath: "$.line.speedBackup" }
      - { constant: 250 }
    transform: [ { op: "toNumber" }, { op: "clamp", min: 0, max: 200 } ]

  - target: "Date/UTC"
    source: { jsonPath: "$.date" }
    transform: [ { op: "parseDateTime" }, { op: "toZoned", zone: "UTC" } ]

  - target: "Meta/Mode"
    source: { jsonPath: "$.meta.mode" }
    constraints: { kind: "enum", values: [ "AUTO", "MANUAL" ] }

  - target: "Meta/Category"
    source: { jsonPath: "$.meta.category" }
    constraints: { kind: "enum", values: [ "red", "green", "blue" ] }

  - target: "Optional/TZ"
    source: { jsonPath: "$.env.tz" }
    transform: [ { op: "defaultIfEmpty", value: "UTC" } ]

  - target: "Unsafe/CycledVar"
    source: { var: "selfCycle" }
```

## What This Shows

- Variables & indirection: `tempF` references another variable; cycles are safely detected (`selfCycle`).
- Fallback chain: `Speed/Normalized` falls back to `speedBackup` then a constant.
- Predicates (`when`): `Order/Priority` is written only if priority is `high`.
- Transforms: trimming, numeric conversion, rounding, unit conversion, regex extraction, concatenation, lookup tables, defaulting, clamping, date parsing and timezone conversion.
- Constraints: range, regex, enum, and maxLength validation after transforms.
- Nested targets: building collections like `Order/Customer/FullName`.

## Running the Example

PowerShell:

```
mvn -q -DskipTests exec:java "-Dexec.args=--config path/to/edge-cases.config.yaml --payload path/to/edge-cases.payload.json --outdir out"
```

Bash/CMD:

```
mvn -q -DskipTests exec:java -Dexec.args="--config path/to/edge-cases.config.yaml --payload path/to/edge-cases.payload.json --outdir out"
```

Tip: You can copy the example payload/config from this doc into files or adapt the ones under `src/test/resources/examples/`.
