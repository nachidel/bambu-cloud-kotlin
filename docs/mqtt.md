## Print state transitions observed on H2C

The following transitions have been observed directly on a Bambu Lab H2C:

| Raw `gcode_state` | SDK state | Meaning |
|---|---|---|
| `PREPARE` | `PREPARING` | Print preparation |
| `RUNNING` | `PRINTING` | Printing |
| `PAUSE` | `PAUSED` | Print paused |
| `FINISH` | `FINISHED` | Print finished |

Observed transitions:

PREPARE -> RUNNING
PrinterStarted

RUNNING -> PAUSE
PrinterPaused

PAUSE -> RUNNING
PrinterResumed

RUNNING -> FINISH
PrinterFinished