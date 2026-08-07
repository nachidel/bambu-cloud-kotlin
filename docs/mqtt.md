## MQTT payloads

Bambu Lab MQTT messages may contain either a complete printer state
or only a partial update.

The SDK therefore maintains a local printer snapshot and applies
incoming MQTT messages as patches.

Examples:

- `samples/mqtt-full-status.json`
- `samples/mqtt-partial-status.json`