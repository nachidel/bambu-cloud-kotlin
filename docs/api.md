# Public API overview

This page describes the public concepts exposed by `bambu-cloud-kotlin`.

The project intentionally keeps protocol parsing and cloud/MQTT implementation details behind a smaller
public model.

## `BambuCloudClient`

`BambuCloudClient` is the main entry point.

Typical responsibilities:

```text
authentication
printer discovery
cloud MQTT connection
event stream
disconnect / close
```

Typical use:

```kotlin
val bambu =
    BambuCloudClient {
        accessToken =
            AccessToken(token)
    }

val printers =
    bambu.printers()

bambu.connect()

// collect bambu.events

bambu.disconnect()
bambu.close()
```

The exact configuration surface may evolve while the SDK is still experimental. Prefer public types from
`com.nachidel.bambu.*` packages and avoid depending on `internal` implementation classes.

## Authentication API

The client exposes:

```kotlin
suspend fun login(
    email: String,
    password: String
): AuthenticationResult
```

and:

```kotlin
suspend fun verifyCode(
    email: String,
    code: String
): AuthenticationResult
```

`AuthenticationResult` represents:

```text
Authenticated
VerificationCodeRequired
Rejected
```

See [authentication.md](authentication.md).

## Printer discovery

```kotlin
val printers =
    bambu.printers()
```

A printer is represented by a `Printer` model containing its serial number, display name, type and current
normalized state.

Do not log device credentials returned by lower-level cloud responses.

## `PrinterType`

Known model identifiers represented by the SDK include:

```text
H2D
H2C
X1
X1C
P1P
P1S
A1
A1_MINI
UNKNOWN
```

This list describes model identification in the SDK. It does **not** mean every cloud behaviour has been
validated on every model.

Most empirical lifecycle/diagnostic work in this repository has been performed on H2C.

## `PrinterState`

The normalized state model contains:

```text
UNKNOWN
OFFLINE
IDLE
PREPARING
PRINTING
PAUSED
FINISHED
FAILED
ERROR
```

Observed Bambu `gcode_state` values are normalized approximately as:

```text
PREPARE  -> PREPARING
RUNNING  -> PRINTING
PAUSE    -> PAUSED
FINISH   -> FINISHED
FAILED   -> FAILED
other    -> UNKNOWN
```

Not every enum value necessarily corresponds one-to-one with a currently observed `gcode_state`.

## `PrinterSnapshot`

`PrinterSnapshot` is the latest merged view of status information.

Fields currently used by the SDK include:

```text
state
rawGcodeState
jobId
subtaskName
percent
currentLayer
totalLayers
remainingTime
diagnostics
```

The snapshot is built from incremental/partial MQTT messages. The SDK therefore preserves previous values
when a later packet omits a field, except when a new-job boundary requires stale job data to be reset.

Do not assume:

- `percent` only increases;
- `remainingTime == 0` means finished;
- every field is present at the beginning of a print.

## `BambuEvent`

`BambuEvent` is the high-level asynchronous API exposed as a Kotlin `Flow`.

Lifecycle/status events carry a `PrinterSnapshot`.

Important event types:

```text
PrinterConnected
PrinterDisconnected
PrinterStatusChanged
PrinterDiagnosticsChanged
PrinterPreparing
PrinterStarted
PrinterPaused
PrinterResumed
PrinterFinished
PrinterFailed
```

See [events.md](events.md).

## `PrinterDiagnostics`

`PrinterDiagnostics` intentionally retains low-level values because not every Bambu diagnostic field has a
verified semantic meaning.

Current fields include values corresponding to:

```text
stageCurrent
machineStage
printStage
printSubStage
machineAction
gcodeAction
jobState
machineState
messageCode
printErrorCode
machinePrintErrorCode
failReason
errorCode
secondaryErrorCode
xcamStatus
hms
```

Do not treat a raw field as a stable public protocol specification.

See [diagnostics.md](diagnostics.md).

## `BambuErrorCode`

`BambuErrorCode` is the normalized representation used for known diagnostic codes.

Diagnostic fields received as decimal strings can be normalized into their hexadecimal representation.
Catalog/HMS codes are handled as hexadecimal data.

The distinction matters because a digit-only HMS code must not automatically be interpreted as a decimal
diagnostic value.

## `PrinterIssue`

`PrinterIssue` is intentionally small and semantic. It is not intended to reproduce every HMS message as a
Kotlin subclass.

Currently curated issues include:

```text
FilamentRunout
ForeignObjectOnBuildPlate
NozzleClogDetected
PrintCancelled
Unknown
```

The issue attached to a pause may be `null` when no cause is identified.

## Diagnostic catalog API

`PrinterDiagnosticCodeResolver` extracts normalized active diagnostic codes while preserving their source:

```text
PRINT_ERROR
MACHINE_PRINT_ERROR
FAIL_REASON
```

`PrinterDiagnosticCatalogResolver` then resolves those normalized codes against the bundled HMS catalog.

A catalog match can have:

```text
zero entries        -> unknown code
one message         -> unambiguousMessage is available
multiple messages   -> family/catalog ambiguity must be preserved
```

Never silently pick an arbitrary catalog-family message.

## Internal APIs

Classes under implementation packages such as MQTT clients, HTTP factories, parsers and status trackers are
implementation details.

Applications should normally build on:

```text
BambuCloudClient
BambuEvent
Printer
PrinterSnapshot
PrinterState
PrinterIssue
PrinterDiagnosticCatalogResolver
```

rather than directly coupling themselves to the raw MQTT client.
