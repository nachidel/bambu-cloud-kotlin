# Cloud MQTT observations

This document describes behaviour observed while implementing and testing `bambu-cloud-kotlin`.

> **This is not an official Bambu Lab MQTT protocol specification.**
>
> Field meanings and cloud behaviour may change with firmware or cloud-service updates.

## Current transport

The current implementation uses MQTT over TLS.

The implementation observed/uses:

```text
MQTT version: 3.1.1
TLS port:     8883
```

The current code targets:

```text
ssl://us.mqtt.bambulab.com:8883
```

This must not be assumed to be the correct universal broker for every account/region. Regional broker
selection remains an area that should be treated carefully.

## Authentication

The MQTT connection uses values derived from the authenticated Bambu Cloud account.

Observed pattern:

```text
username: u_<user id>
password: <access token>
```

Never log or publish the real access token.

## Topics

For a selected printer/device:

```text
device/<device id>/report
device/<device id>/request
```

The client subscribes to the report topic and uses the request topic for full-status requests.

Printer/device IDs must be anonymized in public logs.

## Full-status request

The observed full-status command is:

```json
{
  "pushing": {
    "sequence_id": "0",
    "command": "pushall",
    "version": 1,
    "push_target": 1
  }
}
```

Empirical tests showed that requesting `pushall` immediately after connection could fail to produce the
expected response.

The current client therefore waits approximately two seconds before its initial request.

## Polling/reconciliation

Spontaneous MQTT updates cannot be assumed to contain every important transition.

The client also requests full status periodically.

Current implementation interval:

```text
10 seconds
```

This polling is a reconciliation mechanism, not a replacement for the spontaneous MQTT stream.

## Partial packets

Many MQTT messages contain only changed fields.

Example conceptual sequence:

```json
{
  "print": {
    "gcode_state": "RUNNING",
    "job_id": "job-1",
    "percent": 50,
    "layer_num": 75
  }
}
```

followed by:

```json
{
  "print": {
    "percent": 51
  }
}
```

The second packet does not mean `job_id` or `layer_num` became null.

`PrinterStatusTracker` applies packets as patches over the last snapshot.

## New-job reset

Patch semantics create a risk: values from the previous job can survive into a new job if the boundary is
not detected.

Testing observed startup packets where metadata from the new job arrived while some values still belonged
to the previous finished print.

The tracker therefore resets stale job-specific data when a new-job boundary is detected, including entry
into a new preparation phase.

Do not build your own consumer by blindly merging all fields forever.

## Observed lifecycle states

Real H2C testing observed:

```text
PREPARE
RUNNING
PAUSE
FINISH
FAILED
```

Normalized SDK states:

```text
PREPARE -> PREPARING
RUNNING -> PRINTING
PAUSE   -> PAUSED
FINISH  -> FINISHED
FAILED  -> FAILED
```

## `PREPARE` is useful but not guaranteed

One normal print showed:

```text
FINISHED(previous job)
PREPARE
RUNNING
```

Another real print showed:

```text
FINISHED(previous job)
RUNNING(new job)
```

with no observed `PREPARE`.

Therefore:

- `PREPARE` is an excellent early signal when available;
- consumers must not require it before accepting a real `RUNNING` start;
- polling timing and cloud packet ordering can affect what is observed.

## Initial state

When first connecting, the printer may already be:

```text
FINISH
RUNNING
PAUSE
...
```

The first snapshot is synchronization, not proof that a new transition happened at connection time.

This matters for automation after application restart.

## Manual pause

A manual H2C pause was observed with:

```text
gcode_state = PAUSE
print_error = 0
machine print error = 0
fail_reason = 0
```

Therefore a pause can have no identified diagnostic issue.

The SDK permits:

```text
PrinterPaused(issue = null)
```

## Filament runout

Observed during a pause:

```text
PRINT_ERROR -> 07008011
```

A machine-level related value and an HMS entry were also present.

A previous `FAIL_REASON` from another incident could remain in the snapshot, demonstrating that historical
diagnostic data must be handled carefully.

## Foreign object on build plate

Observed during a pause:

```text
FAIL_REASON -> 0500806E
PRINT_ERROR -> 00000000
```

The catalog and physical test scenario both supported the foreign-object interpretation.

The `FAIL_REASON` value later demonstrated sticky/historical behaviour.

## Nozzle clog and cancellation

Observed sequence:

```text
PAUSE
PRINT_ERROR -> 0C00803F

then user cancellation

FAILED
PRINT_ERROR -> 0

then later, still FAILED
PRINT_ERROR -> 0300400C
```

This is why diagnostics are tracked independently from lifecycle state transitions.

## Progress is not guaranteed monotonic

A real print produced a small progress regression:

```text
89%
88%
```

Applications should not enforce strictly increasing progress.

## Remaining time is not a terminal condition

A real print reported:

```text
remainingTime = 0
```

while the printer was still actively printing.

Do not use `remainingTime == 0` as a substitute for `FINISH`.

The unit/meaning of the raw remaining-time field should also not be generalized beyond what the SDK
currently exposes without further verification.

## Diagnostic/HMS persistence

Observed diagnostic fields and HMS entries can persist across packets.

Absence of a new packet does not necessarily mean a condition was cleared, and presence of a historical
`fail_reason` does not necessarily mean that condition is currently active.

Prefer:

```text
active print error
diagnostic deltas
state transition timing
curated issue detector
```

over a single raw field in isolation.

## Logging raw MQTT safely

Raw packets can expose:

```text
account/user IDs
device IDs
printer serial numbers
access-related data
job identifiers
cloud metadata
```

Before publishing a packet or attaching it to an issue:

1. remove access tokens and passwords;
2. anonymize user/account IDs;
3. anonymize serial numbers/device IDs;
4. remove device access codes and live-view credentials;
5. review URLs for signatures or temporary credentials.

## Compatibility philosophy

The SDK should prefer:

```text
preserve unknown data
normalize only understood encodings
emit stable high-level events
document empirical behaviour
avoid invented protocol semantics
```

When a new firmware or printer exposes a new field/code, preserving it as unknown is preferable to assigning
a speculative meaning.
