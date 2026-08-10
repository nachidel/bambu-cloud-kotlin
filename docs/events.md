# Events and print lifecycle

The main asynchronous API is:

```kotlin
val events: Flow<BambuEvent>
```

Applications can collect the flow and react to high-level printer events rather than parsing MQTT packets.

## Event families

### Connection events

```text
PrinterConnected
PrinterDisconnected
```

`PrinterDisconnected` may contain the connection failure cause.

### Snapshot/diagnostic events

```text
PrinterStatusChanged
PrinterDiagnosticsChanged
```

### Lifecycle events

```text
PrinterPreparing
PrinterStarted
PrinterPaused
PrinterResumed
PrinterFinished
PrinterFailed
```

Lifecycle/status events expose the relevant `PrinterSnapshot`.

## Observed state mapping

The H2C tests used during development observed:

```text
Bambu gcode_state   SDK state
-----------------   -----------
PREPARE             PREPARING
RUNNING             PRINTING
PAUSE               PAUSED
FINISH              FINISHED
FAILED              FAILED
```

Unknown values remain `UNKNOWN`.

## Start semantics

A normal sequence can be:

```text
PREPARE
   |
   v
RUNNING
```

which produces:

```text
PrinterPreparing
PrinterStarted
```

However, `PREPARE` is not guaranteed to be observed by the client.

A real H2C test produced a new print that first appeared as `RUNNING`. Therefore the SDK's start semantics
must tolerate entry into `PRINTING` without having previously received `PREPARING`.

For automation, treat `PrinterPreparing` as an early signal and `PrinterStarted` as the stronger print-start
signal.

## Initial synchronization is not a new transition

When a tracker/client first synchronizes, the first status establishes current state.

If the first status says that a printer is already `RUNNING`, that alone does not necessarily mean the print
started at that instant.

This distinction prevents reconnecting software from blindly treating every existing print as a brand-new
start.

Applications that need "recover an already-running print after restart" should implement that policy
explicitly rather than assuming it is identical to a new `PrinterStarted` event.

## Pause and resume

Observed transition:

```text
RUNNING -> PAUSE
```

emits:

```text
PrinterPaused
```

The event may contain:

```kotlin
val issue: PrinterIssue?
```

A manual pause can legitimately have `issue == null`.

Resume:

```text
PAUSE -> RUNNING
```

emits:

```text
PrinterResumed
```

It must **not** be interpreted as a second `PrinterStarted`.

For livestream automation, a pause normally should not stop the stream.

## Finish

A normal terminal transition emits:

```text
PrinterFinished
```

The SDK avoids treating an arbitrary stale `FINISH` packet around a new-job startup as a genuine new
business-level finish.

Do not infer finish solely from:

```text
percent == 100
remainingTime == 0
```

The state/event is the safer signal.

## Failure

A transition into:

```text
FAILED
```

emits:

```text
PrinterFailed
```

`FAILED` does **not** mean "cancelled".

Cancellation can be represented later by a diagnostic code while the normalized state is already `FAILED`.
Keep the lifecycle event and diagnostic interpretation separate.

## `PrinterStatusChanged`

`PrinterStatusChanged` represents a change in user-visible print status information, for example:

```text
state
raw gcode state
job ID
subtask name
progress
layer
total layers
remaining-time value
```

It should not be used as a substitute for every high-level lifecycle event.

MQTT packets are partial, so a snapshot is a merged view of the latest known values.

## `PrinterDiagnosticsChanged`

Diagnostic changes are tracked independently from ordinary print-status changes.

This matters because an error code can arrive after the printer has already entered a terminal state.

A diagnostic-only packet therefore needs to be observable without pretending that print progress/state
changed again.

Consumers should use `PrinterDiagnosticsChanged` when they need to update diagnostics, error displays or
post-failure classification.

## Suggested automation policy

A robust automation can use:

```text
PrinterPreparing
    -> early preparation action, e.g. Wake-on-LAN

PrinterStarted
    -> confirm print, ensure dependent services are ready

PrinterPaused
    -> keep resources/live stream running unless application policy says otherwise

PrinterResumed
    -> resume normal monitoring

PrinterFinished
    -> normal terminal action

PrinterFailed
    -> failure terminal action
```

The exact policy belongs to the consuming application, not to the SDK.
