# Example: Catena‑X Batch v3.0.0 Mapping

This example maps a Batch payload to an AAS v3 Submodel conforming to Catena‑X Batch 3.0.0. It highlights semantic IDs and value types for better interoperability.

## Input Payload (JSON)

```json
{
  "batch": {
    "id": "BATCH-2024-09-02-01",
    "production": { "date": "2024-09-02T10:15:30+02:00", "country": "DE" },
    "material": { "customerPartId": "CUST-MAT-0815", "supplierPartId": "SUPP-4711-A" },
    "manufacturer": { "bpn": "BPNL00000003CML1" }
  }
}
```

## Mapping Config (YAML)

```yaml
apiVersion: "aasx.map/v1"
name: "CatenaX_Batch_v3_0_0"
model: { uploadId: "u_demo" }

submodel:
  createIfMissing: true
  idStrategy: { kind: "uuidv4" }
  idShort: "Batch"
  kind: "INSTANCE"
  semanticId: "urn:bamm:io.catenax.batch:3.0.0#Batch"
  administration: { version: "3.0.0", revision: "0" }
  elementDefaults: { unitPolicy: "allow-missing", language: "en" }
  initialElements:
    - { path: "batchId",                  type: "Property", valueType: "string",   semanticId: "urn:bamm:io.catenax.batch:3.0.0#batchId" }
    - { path: "manufacturingDate",        type: "Property", valueType: "dateTime", semanticId: "urn:bamm:io.catenax.batch:3.0.0#manufacturingDate" }
    - { path: "manufacturingCountry",     type: "Property", valueType: "string",   semanticId: "urn:bamm:io.catenax.batch:3.0.0#manufacturingCountry" }
    - { path: "materialNumberCustomer",   type: "Property", valueType: "string",   semanticId: "urn:bamm:io.catenax.batch:3.0.0#materialNumberCustomer" }
    - { path: "materialNumberSupplier",   type: "Property", valueType: "string",   semanticId: "urn:bamm:io.catenax.batch:3.0.0#materialNumberSupplier" }
    - { path: "manufacturerId",           type: "Property", valueType: "string",   semanticId: "urn:bamm:io.catenax.batch:3.0.0#manufacturerId" }

variables:
  batchId:                { jsonPath: "$.batch.id" }
  manufacturingDate:      { jsonPath: "$.batch.production.date" }
  manufacturingCountry:   { jsonPath: "$.batch.production.country" }
  materialNumberCustomer: { jsonPath: "$.batch.material.customerPartId" }
  materialNumberSupplier: { jsonPath: "$.batch.material.supplierPartId" }
  manufacturerId:         { jsonPath: "$.batch.manufacturer.bpn" }

rules:
  - target: "batchId"
    source: { var: "batchId" }
    transform: [ { op: "trim" } ]
    constraints: [ { kind: "maxLength", value: 128 } ]

  - target: "manufacturingDate"
    source: { var: "manufacturingDate" }
    transform: [ { op: "parseDateTime" }, { op: "toZoned", zone: "UTC" } ]

  - target: "manufacturingCountry"
    source: { var: "manufacturingCountry" }
    transform: [ { op: "trim" } ]
    constraints: [ { kind: "regex", pattern: "^[A-Z]{2}$" } ]

  - target: "materialNumberCustomer"
    source: { var: "materialNumberCustomer" }
    transform: [ { op: "trim" } ]
    constraints: [ { kind: "maxLength", value: 128 } ]

  - target: "materialNumberSupplier"
    source: { var: "materialNumberSupplier" }
    transform: [ { op: "trim" } ]
    constraints: [ { kind: "maxLength", value: 128 } ]

  - target: "manufacturerId"
    source: { var: "manufacturerId" }
    constraints: [ { kind: "regex", pattern: "^BPNL[0-9A-Z]{12}$" } ]
```

## Explanation

- Submodel header:
  - `idStrategy: uuidv4` creates a new URN id for each run.
  - `semanticId` anchors the Submodel to the Catena‑X Batch concept.
  - `initialElements` declare the element identities and types, and attach semantic IDs.
- Variables:
  - Keep rules readable; update selectors in one place.
- Rules:
  - `manufacturingDate` normalizes timezone to UTC for consistent transport.
  - Regex constraints ensure basic format compliance (BPNL manufacturer, two‑letter country).

## Running the Example

PowerShell:

```
mvn -q -DskipTests exec:java "-Dexec.args=--config path/to/cx-batch-3.0.0.config.yaml --payload path/to/cx-batch.payload.json --outdir out"
```

Bash/CMD:

```
mvn -q -DskipTests exec:java -Dexec.args="--config path/to/cx-batch-3.0.0.config.yaml --payload path/to/cx-batch.payload.json --outdir out"
```

The engine outputs a valid AAS v3 Submodel JSON using AAS4J with typed, semantically annotated elements.
