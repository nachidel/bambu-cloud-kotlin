# Diagnostics and HMS

The diagnostic layer intentionally separates raw printer data, normalized codes, curated automation meaning
and human-readable catalog messages.

## Architecture

```text
MQTT status
    |
    v
PrinterDiagnostics
    |
    v
PrinterDiagnosticCodeResolver
    |
    v
PrinterDiagnosticCode
    |
    +----------------------------+
    |                            |
    v                            v
PrinterIssueDetector   PrinterDiagnosticCatalogResolver
    |                            |
    v                            v
PrinterIssue               HmsCatalogEntry[]
```

The two right-hand interpretations serve different purposes.

`PrinterIssue` is curated business semantics.

The HMS catalog is broad descriptive data.

## Raw diagnostics

`PrinterDiagnostics` keeps low-level values such as:

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

These fields are retained because their exact semantics are not all verified.

Do not attach a meaning to an unknown raw field merely because its numeric value looks familiar.

## Normalized diagnostic sources

`PrinterDiagnosticCodeResolver` currently normalizes these sources:

```text
PRINT_ERROR
MACHINE_PRINT_ERROR
FAIL_REASON
```

It ignores zero/no-error values.

Other raw fields remain available in `PrinterDiagnostics`, but are not forced into the same code model until
their encoding is sufficiently understood.

## `BambuErrorCode`

Observed diagnostic fields may carry decimal values even when the useful representation is hexadecimal.

Example:

```text
117473297 decimal -> 07008011
```

For printer diagnostic fields, `BambuErrorCode.fromDiagnostic(...)` attempts the observed decimal
normalization.

Catalog/HMS `ecode` values are treated as hexadecimal by `BambuErrorCode.fromHex(...)`.

This distinction is important: a digit-only HMS/catalog code must not automatically be interpreted as a
decimal diagnostic value.

## Curated issues

The semantic `PrinterIssue` layer currently recognizes a small number of useful conditions:

```text
FilamentRunout
ForeignObjectOnBuildPlate
NozzleClogDetected
PrintCancelled
Unknown
```

The goal is **not** to create one Kotlin class for every HMS code.

## Empirically observed codes

The following codes were observed during H2C testing and/or matched against the bundled French catalog.

| Normalized code | Observed interpretation | Typical source | Notes |
|---|---|---|---|
| `07008011` | Filament runout | `PRINT_ERROR` | Active print error observed during pause |
| `0500806E` | Foreign object detected on build plate | `FAIL_REASON` | `fail_reason` can persist historically |
| `0C00803F` | Nozzle clog / AI nozzle obstruction detection | `PRINT_ERROR`, later `FAIL_REASON` | Can remain after transition to failure |
| `0300400C` | Print task cancelled | `PRINT_ERROR` | May arrive after state already became `FAILED` |

These are empirical/reverse-engineered observations, not an official Bambu Lab diagnostic specification.

### Related machine-level values

During testing, related machine error values such as:

```text
00008011
0000400C
```

were also observed.

They are preserved as diagnostic data even when no unambiguous bundled catalog message exists.

## Sticky `fail_reason`

`fail_reason` must not automatically be interpreted as the currently active cause.

Testing showed that a previous value can persist into later packets.

Example pattern:

```text
previous incident -> FAIL_REASON = 0500806E

later filament runout:
PRINT_ERROR = 07008011
FAIL_REASON  = 0500806E
```

In this situation, the active `PRINT_ERROR` is the stronger evidence for the current issue.

The sample output labels `FAIL_REASON` as potentially historical.

## Cancellation can arrive late

A real cancellation sequence showed:

```text
PAUSE caused by nozzle issue
        |
        v
FAILED with print_error = 0
        |
        v
later packet while still FAILED
print_error = 0300400C
```

Therefore:

- `PrinterFailed` remains a lifecycle event;
- cancellation is diagnostic refinement;
- diagnostic updates must remain observable after a state transition.

## HMS entries vs catalog `ecode`

Raw MQTT HMS entries contain an `attr` and `code` pair.

`HmsEntry.fullCode` can represent the concatenated values as a longer hexadecimal code.

Bundled catalog entries often use shorter `ecode` values.

Do not assume a direct conversion between every MQTT `attr/code` pair and an 8-character catalog `ecode`.
That mapping has not been established generally.

Keep the representations separate until evidence supports a conversion.

## Bundled French catalogs

The library bundles French HMS catalog data for these catalog families:

```text
093
094
20P
22E
239
26A
31B
```

A family name is preserved in each `HmsCatalogEntry`.

Do **not** infer a printer-model-to-family mapping merely from:

- a serial prefix;
- one overlapping diagnostic code;
- similar messages.

No verified general H2C-to-catalog-family mapping is assumed by the resolver.

## Ambiguous catalog messages

The same code can exist in several families and may have different French wording.

Therefore:

```kotlin
match.unambiguousMessage
```

is only non-null when all resolved catalog entries collapse to one distinct message.

Otherwise use:

```kotlin
match.messages
```

and preserve the alternatives.

Do not silently choose the first family.

## Unknown codes are useful

An unknown code is not discarded.

A `PrinterDiagnosticCatalogMatch` can contain:

```text
diagnostic = known normalized code
entries    = empty
messages   = empty
```

This is deliberate. New printer firmware can introduce codes before the bundled catalog is updated.

Applications can log or report the code without inventing a meaning.

## Example

```kotlin
val resolver =
    PrinterDiagnosticCatalogResolver()

val matches =
    resolver.resolve(
        snapshot.diagnostics
    )

matches.forEach { match ->

    println(
        "${match.diagnostic.source}: " +
            match.diagnostic.code.value
    )

    when {
        match.unambiguousMessage != null -> {
            println(
                match.unambiguousMessage
            )
        }

        match.messages.isNotEmpty() -> {
            match.messages.forEach(::println)
        }

        else -> {
            println("No known catalog message")
        }
    }
}
```
