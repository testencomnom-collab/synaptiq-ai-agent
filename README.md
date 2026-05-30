<div align="center">

# 🤖 Android Mobile Automation Agent

**An AI-powered Android automation assistant designed specifically for mobile devices. It manages your calendar, drafts and sends emails, and automates daily tasks—running natively on Android to coordinate actions through device-level integrations.**

[![Android](https://img.shields.io/badge/Platform-Android-3DDC84?logo=android&logoColor=white)](https://developer.android.com/)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org/)
[![Jetpack Compose](https://img.shields.io/badge/Jetpack_Compose-Material3-4285F4?logo=jetpack-compose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
[![Download APK](https://img.shields.io/badge/Download-Latest_APK-FF4500?style=for-the-badge&logo=android)](https://github.com/testencomnom-collab/on-device-agent/raw/main/releases/on-device-agent-V2.apk)

</div>

---

## ✨ Features

- 📱 **Mobile Automation Engine** — Automatically reads, parses, and executes complex system actions on Android devices based on conversational queries.
- 🔌 **Fully Offline Simulated Agents** — No API key? No problem. The app falls back to a powerful offline local simulator where you can interact with mock versions of OpenHands, Goose, BrowserUse, and more.
- 🔑 **Dynamic Runtime Permissions** — Natively requests and handles standard Android runtime permissions (Calendar, Contacts, Location, SMS, Accounts) to coordinate automated task routing.
- 📅 **Natively Scheduled Workflows** — Automatically checks conflicts on your local device calendar and inserts/updates appointments without manual scheduling.
- 📧 **Simulated & Real Email Automation** — Drafts context-aware email replies from mock inbox streams and invokes system intents to compose drafts in external mail apps.
- 🧠 **Multi-LLM Integration** — Choose OpenAI (GPT-4o), Anthropic (Claude 3.5), or Google Gemini backends as the orchestration engine.
- 🗄️ **Local Security & Cache** — Securely preserves conversation flows, inbox tables, and sensitive API keys locally utilizing SQLite (Room) and encrypted preferences.

---

## 🏗️ Architecture

```
com.example/
├── data/
│   ├── api/              # Retrofit API clients for OpenAI, Anthropic, Gemini
│   ├── database/         # Room database, DAOs for chat & email persistence
│   ├── model/            # Data classes (ChatMessage, EmailItem)
│   └── repository/       # Repository layer for data access
├── services/
│   ├── CalendarManager   # System calendar read/write operations
│   └── LLMAgentService   # Core agent logic, tool calling, prompt orchestration
└── ui/
    ├── AgentViewModel    # Main ViewModel managing app state
    ├── screens/          # Compose UI screens (chat, inbox, calendar, settings)
    └── theme/            # Material 3 theming (colors, typography)
```

---

## 🛠️ Tech Stack

| Category | Technology |
|----------|-----------|
| **Language** | Kotlin 2.0 |
| **UI Framework** | Jetpack Compose + Material Design 3 |
| **Networking** | Retrofit + OkHttp + Moshi |
| **Database** | Room (SQLite) |
| **Architecture** | MVVM with Repository Pattern |
| **Build System** | Gradle (Kotlin DSL) with Version Catalog |
| **Min SDK** | Android 7.0 (API 24) |
| **Target SDK** | Android 16 (API 36) |

---

## 🚀 Getting Started

### Prerequisites

- [Android Studio](https://developer.android.com/studio) (latest stable)
- Android SDK 36
- A physical device or emulator running Android 7.0+

### Setup

1. **Clone the repository**
   ```bash
   git clone https://github.com/testencomnom-collab/on-device-agent.git
   cd on-device-agent
   ```

2. **Open in Android Studio**
   Select **File → Open** and choose the project directory.

3. **Configure API Keys**
   Create a `.env` file in the project root (see [`.env.example`](.env.example)):
   ```env
   GEMINI_API_KEY=your_gemini_api_key_here
   ```

4. **Run the app**
   Build and run on an emulator or physical device via Android Studio.

### Supported LLM Providers

You can configure your preferred AI provider in the app's settings screen:

| Provider | Model | Configuration |
|----------|-------|---------------|
| **Google Gemini** | Gemini 2.5 Flash | API key via `.env` file |
| **OpenAI** | GPT-4o | API key via in-app settings |
| **Anthropic** | Claude 3.5 Sonnet | API key via in-app settings |

---

## 🔒 Security

- API keys are stored locally on-device and **never** transmitted to third parties
- The `.env` file containing your Gemini API key is excluded from version control via `.gitignore`
- Release signing keystores are **not** included in the repository

---

## 📄 License

This project is licensed under the MIT License — see the [LICENSE](LICENSE) file for details.

---

<div align="center">

**Built with ❤️ using Kotlin & Jetpack Compose**

</div>
