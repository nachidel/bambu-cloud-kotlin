# bambu-cloud-kotlin

Modern Kotlin/JVM SDK for interacting with Bambu Lab Cloud services and printer status over cloud MQTT.

> **Important**
>
> This project is unofficial and is not affiliated with, endorsed by, or supported by Bambu Lab.
> The cloud HTTP and MQTT behaviour used by this library has been discovered through implementation work,
> community resources, and empirical testing. It may change without notice.

## Project status

The library is usable for cloud authentication, printer discovery, cloud MQTT status tracking,
high-level print events, and diagnostic/HMS resolution.

The behaviour has been tested most extensively with a **Bambu Lab H2C**. Model names represented by
`PrinterType` should not be interpreted as a guarantee that every feature has been validated on every model.

## Features

- Bambu Cloud authentication
- Email verification-code authentication flow
- Existing access-token authentication
- Discovery of printers linked to the Bambu account
- Cloud MQTT connection
- Incremental printer status tracking
- Print progress, current layer, total layers, remaining-time value and job metadata
- High-level print lifecycle events
- Pause/resume handling
- Raw printer diagnostics
- Normalized diagnostic codes
- Curated printer issues for automation
- Bundled French HMS diagnostic catalogs
- Diagnostic catalog lookup while preserving ambiguous or unknown codes
- Periodic full-status reconciliation in addition to spontaneous MQTT updates

## Requirements

- JDK 21
- A Kotlin/JVM project
- A Bambu Cloud account
- Network access to the Bambu Cloud services

## Build from source

```bash
git clone https://github.com/nachidel/bambu-cloud-kotlin.git
cd bambu-cloud-kotlin
./gradlew clean test
```

On Windows PowerShell:

```powershell
git clone https://github.com/nachidel/bambu-cloud-kotlin.git
cd bambu-cloud-kotlin
.\gradlew clean test
```

The repository currently documents source usage. Do not invent or rely on a Maven coordinate unless one
is explicitly published by the project.

## Quick start

### 1. Create the client

If you already have an access token:

```kotlin
import com.nachidel.bambu.api.BambuCloudClient
import com.nachidel.bambu.value.AccessToken

val bambu =
    BambuCloudClient {
        accessToken =
            AccessToken(
                System.getenv("BAMBU_TOKEN")
            )
    }
```

Do not hard-code credentials or tokens in source control.

### 2. Authenticate with email and password

The password login may either authenticate immediately or require a verification code sent by email.

```kotlin
import com.nachidel.bambu.auth.AuthenticationResult

when (
    val result =
        bambu.login(
            email = email,
            password = password
        )
) {
    is AuthenticationResult.Authenticated -> {
        // Authenticated.
    }

    AuthenticationResult.VerificationCodeRequired -> {
        val verification =
            bambu.verifyCode(
                email = email,
                code = codeFromEmail
            )

        when (verification) {
            is AuthenticationResult.Authenticated -> {
                // Authenticated.
            }

            AuthenticationResult.VerificationCodeRequired -> {
                error("A new verification code is required.")
            }

            is AuthenticationResult.Rejected -> {
                error(
                    "Verification rejected: " +
                        "${verification.code} - ${verification.message}"
                )
            }
        }
    }

    is AuthenticationResult.Rejected -> {
        error(
            "Authentication rejected: " +
                "${result.code} - ${result.message}"
        )
    }
}
```

See [Authentication](docs/authentication.md).

### 3. Discover printers

```kotlin
val printers =
    bambu.printers()

printers.forEach { printer ->
    println(
        "${printer.name} | " +
            "${printer.type} | " +
            "${printer.serial} | " +
            printer.state
    )
}
```

### 4. Listen for events

`events` is a Kotlin `Flow<BambuEvent>`.

```kotlin
import com.nachidel.bambu.event.BambuEvent
import kotlinx.coroutines.launch

val eventJob =
    launch {
        bambu.events.collect { event ->

            when (event) {

                BambuEvent.PrinterConnected -> {
                    println("Connected")
                }

                is BambuEvent.PrinterPreparing -> {
                    println(
                        "Preparing: ${event.snapshot.subtaskName}"
                    )
                }

                is BambuEvent.PrinterStarted -> {
                    println(
                        "Started: ${event.snapshot.subtaskName}"
                    )
                }

                is BambuEvent.PrinterPaused -> {
                    println(
                        "Paused: ${event.snapshot.subtaskName}"
                    )
                }

                is BambuEvent.PrinterResumed -> {
                    println(
                        "Resumed: ${event.snapshot.subtaskName}"
                    )
                }

                is BambuEvent.PrinterFinished -> {
                    println(
                        "Finished: ${event.snapshot.subtaskName}"
                    )
                }

                is BambuEvent.PrinterFailed -> {
                    println(
                        "Failed: ${event.snapshot.subtaskName}"
                    )
                }

                else -> Unit
            }
        }
    }
```

Then connect:

```kotlin
bambu.connect()
```

Always close the client when it is no longer needed:

```kotlin
bambu.close()
```

## Printer snapshot

Status events expose a `PrinterSnapshot` containing the normalized state plus the latest values known from
incremental MQTT updates.

Important fields include:

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

MQTT status packets are frequently partial. A field being absent from one packet does not necessarily mean
that the printer cleared the value.

See [Events and lifecycle](docs/events.md) and [MQTT observations](docs/mqtt.md).

## Diagnostics

The SDK separates raw diagnostics from interpretation.

```text
PrinterDiagnostics
        |
        v
PrinterDiagnosticCodeResolver
        |
        v
BambuErrorCode
        |
        +----------------------+
        |                      |
        v                      v
PrinterIssueDetector   PrinterDiagnosticCatalogResolver
        |                      |
        v                      v
automation meaning      HMS catalog messages
```

This is intentional:

- `PrinterIssue` is a curated semantic layer for useful automation decisions.
- `PrinterDiagnosticCatalogResolver` is a data/explanation layer.
- Unknown diagnostic codes are preserved.
- A catalog lookup may return multiple messages when the printer/catalog family is ambiguous.

See [Diagnostics and HMS](docs/diagnostics.md).

## Lifecycle events

The public event model includes:

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

A pause is **not** a terminal state. `PAUSED -> PRINTING` is represented as `PrinterResumed`, not as a new
print start.

`FAILED` must also not be assumed to mean "cancelled": cancellation is a diagnostic refinement and its
diagnostic code may arrive after the state has already changed to `FAILED`.

See [Events and lifecycle](docs/events.md).

## Known cloud/MQTT caveats

Empirical testing has shown that:

- `PREPARE` may be observed, but it can also be absent from the received stream.
- A new print may therefore first become visible as `RUNNING`.
- The first status received by a newly connected tracker is synchronization and does not necessarily imply
  that a new lifecycle transition just happened.
- MQTT updates are partial.
- Values from a previous job can briefly appear around a new-job transition.
- Progress must not be assumed to be strictly monotonic.
- A remaining-time value of `0` does not prove that the print has finished.
- Diagnostic data can arrive after the state transition that triggered it.
- `fail_reason` can behave like historical/sticky data.
- HMS entries can persist across multiple packets.
- Periodic full-status requests are used as reconciliation.

Do not build automation solely from `percent == 100` or `remainingTime == 0`.
Prefer high-level lifecycle events and normalized printer state.

## Documentation

- [Public API overview](docs/api.md)
- [Authentication](docs/authentication.md)
- [Events and lifecycle](docs/events.md)
- [Diagnostics and HMS](docs/diagnostics.md)
- [Cloud MQTT observations](docs/mqtt.md)

The `sample` module is also intended as executable documentation.

## Security

Never commit:

- Bambu account passwords
- access tokens
- verification codes
- device access codes
- live-view credentials
- signed URLs
- raw logs containing private identifiers

Prefer environment variables or another external secret-management mechanism.

## License

This project is licensed under the **GNU Affero General Public License v3.0 (AGPL-3.0)**.
See `LICENSE.txt`.

Third-party resources, where present, remain subject to their applicable copyright and licensing notices.

## Disclaimer

Bambu Lab does not publish this project as an official SDK.

Cloud endpoints, MQTT fields, diagnostic codes and device behaviour described here should be considered
reverse-engineered or empirically observed unless explicitly documented otherwise by their original owner.
