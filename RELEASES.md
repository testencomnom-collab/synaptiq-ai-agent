# Release Notes

## v18.0.0 — Autonomous "Think-Act-Observe" Update

### What's new
- **ContinuousAgentService hinzugefügt**: Der Agent läuft nun im Hintergrund via Foreground Service.
- **Think-Act-Observe Loop implementiert**: Der Agent kann selbstständig nächste Schritte planen und ausführen.
- **Kill-Switch (Stop-Button)**: In der UI hinzugefügt, um den Loop jederzeit sicher abzubrechen.
- **Echtes Screen-Reading (OBSERVE)**: Der Agent kann über den AgentAccessibilityService den aktuellen Bildschirminhalt auslesen und in seine Entscheidungsfindung einbeziehen.

### Download
The APK is available in the [`releases/`](releases/) folder: [synaptiq-ai-agent-V18-Autonomous.apk](releases/synaptiq-ai-agent-V18-Autonomous.apk)


## v1.0.0 — On-Device Agent

### What's new
- **Runtime permissions system** — The agent proactively requests Contacts, Calendar, Location access
- **Encrypted API key storage** — Keys stored with `EncryptedSharedPreferences` (Android Keystore)
- **R8/ProGuard minification** — Smaller, obfuscated APK for better security
- **HTTP logging disabled in production** — No sensitive data leaked in release builds

### Download
The APK is available in the [`releases/`](releases/) folder: [on-device-agent-v1.0.0.apk](releases/on-device-agent-v1.0.0.apk)

### Permissions requested at runtime
| Permission | Purpose |
|---|---|
| READ_CALENDAR / WRITE_CALENDAR | Schedule & read events |
| READ_CONTACTS | Address emails, identify people |
| ACCESS_FINE_LOCATION | Location-based suggestions |
| GET_ACCOUNTS | Email account integration |
| SEND_SMS | Optional messaging extensions |
