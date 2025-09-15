# Example: Catena‑X SerialPart Mapping

This example shows how to turn a simple input JSON into an AAS v3 Submodel for Catena‑X SerialPart (v1.1.0). It explains each part of the config so you can adapt it to your own data.

## Input Payload (JSON)

```json
{
  "part": {
    "manufacturer": {
      "bpn": "BPNL00000003CML1",
      "partId": "9AK12345678",
      "name": "Drive Motor 3kW"
    },
    "customer": { "partId": "CUST-423-A" },
    "serial": "SN-2024-00001234",
    "production": { "date": "2024-09-02T10:15:30+02:00", "country": "DE" },
    "instanceId": "urn:uuid:550e8400-e29b-41d4-a716-446655440000"
  }
}
```

## Mapping Config (YAML)

```yaml
apiVersion: "aasx.map/v1"
name: "CatenaX_SerialPart_v1"
model:
  uploadId: "u_demo"

submodel:
  createIfMissing: true
  idStrategy: { kind: "uuidv4" }
  idShort: "SerialPart"
  kind: "INSTANCE"
  semanticId: "urn:bamm:io.catenax.serial_part:1.1.0#SerialPart"
  administration: { version: "1.1.0", revision: "0" }
  elementDefaults: { unitPolicy: "allow-missing", language: "en" }
  initialElements:
    - { path: "manufacturerId",       type: "Property", valueType: "string" }
    - { path: "manufacturerPartId",    type: "Property", valueType: "string" }
    - { path: "customerPartId",        type: "Property", valueType: "string" }
    - { path: "serialNumber",          type: "Property", valueType: "string" }
    - { path: "manufacturingDate",     type: "Property", valueType: "dateTime" }
    - { path: "manufacturingCountry",  type: "Property", valueType: "string" }
    - { path: "catenaXId",             type: "Property", valueType: "string" }

variables:
  manufacturerId:       { jsonPath: "$.part.manufacturer.bpn" }
  manufacturerPartId:   { jsonPath: "$.part.manufacturer.partId" }
  customerPartId:       { jsonPath: "$.part.customer.partId" }
  serialNumber:         { jsonPath: "$.part.serial" }
  manufacturingDate:    { jsonPath: "$.part.production.date" }
  manufacturingCountry: { jsonPath: "$.part.production.country" }
  partInstanceId:       { jsonPath: "$.part.instanceId" }

rules:
  - target: "manufacturerId"
    source: { var: "manufacturerId" }
    constraints: [ { kind: "regex", pattern: "^BPNL[0-9A-Z]{12}$" } ]

  - target: "manufacturerPartId"
    source: { var: "manufacturerPartId" }
    transform: [ { op: "trim" } ]
    constraints: [ { kind: "maxLength", value: 128 } ]

  - target: "customerPartId"
    source: { var: "customerPartId" }
    transform: [ { op: "trim" } ]
    constraints: [ { kind: "maxLength", value: 128 } ]

  - target: "serialNumber"
    source: { var: "serialNumber" }
    transform: [ { op: "trim" } ]
    constraints: [ { kind: "maxLength", value: 128 } ]

  - target: "manufacturingDate"
    source: { var: "manufacturingDate" }
    transform: [ { op: "parseDateTime" }, { op: "toZoned", zone: "UTC" } ]

  - target: "manufacturingCountry"
    source: { var: "manufacturingCountry" }
    transform: [ { op: "trim" } ]
    constraints: [ { kind: "regex", pattern: "^[A-Z]{2}$" } ]

  - target: "catenaXId"
    source: { var: "partInstanceId" }
    fallback:
      - jsonPath: "$.part.serial"
        transform:
          - op: "concat"
            parts: [ "urn:cx:serial:", { var: "serialNumber" } ]
```

## Explanation

- Top‑level:
  - `apiVersion`: Must be `aasx.map/v1`.
  - `name`: Free‑text label for this mapping.
  - `model`: `uploadId` is a logical context for your run.
- `submodel`:
  - `idStrategy`: AAS4J will generate a v4 UUID URN for the submodel `id`.
  - `idShort`: Human‑readable key; will be set on the Submodel.
  - `kind`: `INSTANCE` for produced instances; use `TEMPLATE` for templates.
  - `semanticId`: Global reference to the SerialPart concept.
  - `administration`: Versioning metadata.
  - `initialElements`: Hints describing each element (type, valueType) so AAS output gets proper typing.
- `variables`:
  - Reusable selectors to keep rules clean.
- `rules`:
  - Map one element at a time. Use `transform` to clean values and `constraints` to validate.
  - `catenaXId` has a `fallback`: if `partInstanceId` is missing, derive a URN from the serial number.

## Running the Example

PowerShell:

```
mvn -q -DskipTests exec:java "-Dexec.args=--config path/to/cx-serial-part.config.yaml --payload path/to/cx-serial-part.payload.json --outdir out"
```

Bash/CMD:

```
mvn -q -DskipTests exec:java -Dexec.args="--config path/to/cx-serial-part.config.yaml --payload path/to/cx-serial-part.payload.json --outdir out"
```

The engine outputs a valid AAS v3 Submodel JSON using AAS4J.
