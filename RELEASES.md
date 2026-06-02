# Release Notes

## v18.4.1 — Snapchat Typing & Click Automation Fix

### What's new
- **Direct Snapchat Search Typing**: Fixed race conditions during contact searches in Snapchat by injecting text directly into the search bar.
- **Recursive Click Traversal**: Added a recursive parent-clicking helper (`performClickRecursively`) to reliably click target buttons or contacts up to 6 parent layers deep.
- **Improved Automation Reliability**: Solved cases where automated messages required manual clicks before sending.

### Download
The APK is available in the [`releases/`](releases/) folder: [synaptiq-ai-agent-V18.4.1.apk](releases/synaptiq-ai-agent-V18.4.1.apk) (and also updated as [synaptiq-ai-agent-latest.apk](releases/synaptiq-ai-agent-latest.apk))

## v18.4.0 — Messaging Automation & Lifecycle Update

### What's new
- **Hands-Free Messaging Automation**: Enhanced the accessibility service for 100% hands-free WhatsApp and Snapchat message dispatching.
- **Search & Type Fallbacks**: Implemented fallback search and typing flows in Snapchat and WhatsApp to look up contacts when they are not in the recent list.
- **Clean State Lifecycle**: Added proper state initialization and cleanup (`start()` and `stop()`) to prevent stale flags from freezing subsequent automation runs.
- **Improved Traversal**: Utilized deep parent-node click searches and localized content-description matches.
- **String Interpolation Fixes**: Resolved string interpolation escapes that caused raw template strings to show in UI toasts/alerts.

### Download
The APK is available in the [`releases/`](releases/) folder: [synaptiq-ai-agent-V18.4.apk](releases/synaptiq-ai-agent-V18.4.apk) (and also updated as [synaptiq-ai-agent-latest.apk](releases/synaptiq-ai-agent-latest.apk))

## v18.1.0 — Crash Fixes & Auto-Scroll Update

### What's new
- **Auto-Scroll Navigation**: The agent can now automatically scroll through apps and menus using `SCROLL_DOWN` and `SCROLL_UP`.
- **Memory Leak Fixed**: The AgentAccessibilityService correctly releases accessibility nodes (`recycle()`) after reading them, preventing crashes caused by OutOfMemoryError.
- **15-Second Timeout Fix**: The UI action loop now has a timeout. If the screen reader gets stuck, the agent will no longer freeze.
- **Improved Network Stability**: Connection drops (e.g., TimeoutException from the LLM) no longer hard-crash the task. The agent pauses for 5 seconds and then retries.
- **Code & UI Cleanup**: Obsolete UI elements and dead code from previous architecture versions have been cleaned up.

### Download
The APK is available in the [`releases/`](releases/) folder: [synaptiq-ai-agent-V18-crash-fixes.apk](releases/synaptiq-ai-agent-V18-crash-fixes.apk)

## v18.0.0 — Autonomous "Think-Act-Observe" Update

### What's new
- **Added ContinuousAgentService**: The agent now runs in the background via a Foreground Service.
- **Implemented Think-Act-Observe Loop**: The agent can autonomously plan and execute its next steps.
- **Kill-Switch (Stop Button)**: Added to the UI to safely abort the loop at any time.
- **True Screen-Reading (OBSERVE)**: The agent can read the current screen content via the AgentAccessibilityService and incorporate it into its decision-making.
- **WakeLock for Standby**: The agent uses a Partial WakeLock to keep the CPU active, allowing it to seamlessly continue background tasks even when the screen is turned off.
- **Optimized Short-Term Memory**: The agent tracks the last 5 actions as history context, saving token limits and preventing infinite loops.

### Download
The APK is available in the [`releases/`](releases/) folder: [synaptiq-ai-agent-V18-Bug-Fixes.apk](releases/synaptiq-ai-agent-V18-Bug-Fixes.apk)


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
