# UEPFlip — AI-Powered Flashcard Study App

<div align="center">

![Android](https://img.shields.io/badge/Android-26%2B-3DDC84?logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-2.3.21-7F52FF?logo=kotlin&logoColor=white)
![Room](https://img.shields.io/badge/Room-2.8.5-4285F4?logo=android&logoColor=white)
![License](https://img.shields.io/badge/License-MIT-blue.svg)

An offline-first Android flashcard application with AI-powered card generation, spaced repetition scheduling, and an intelligent tutor.

[Features](#features) • [Screenshots](#screenshots) • [Architecture](#architecture) • [Setup](#setup) • [Build](#build) • [License](#license)

</div>

---

## Features

### 🎓 Smart Study System
- **Spaced Repetition (SM-2)** — Optimized review scheduling based on your performance
- **Progress Tracking** — Monitor mastery levels, due cards, and study streaks
- **Offline-First** — Full functionality without an internet connection (AI features require connectivity)

### 🤖 AI Integration
- **PDF to Flashcards** — Upload study materials and generate cards automatically (Gemini AI)
- **Topic Expansion** — Type a subject and get comprehensive flashcards
- **AI Tutor** — Chat-based learning assistant with answer evaluation and explanations
- **Smart Hints** — Context-aware nudges that don't reveal answers

### 📊 Organization
- **Deck Management** — Create, rename, delete, and organize flashcard collections
- **Search & Filter** — Find decks by name, status (in-progress/completed)
- **Statistics Dashboard** — Real-time insights into your learning progress

### 🎨 Design
- **Light-Only Theme** — Warm paper palette inspired by physical flashcards
- **Material 3** — Modern Android UI with accessibility-compliant touch targets
- **Smooth Animations** — Card flips, transitions, and interactive elements

---

## Screenshots

> **Note:** Screenshots require an Android device or emulator to capture. The app follows the designer's reference UI (25 layout files; 22 wired, 3 mockups kept as references).

**Key Screens:**
- **Home** — Greeting, overdue reviews, recent decks, and progress card
- **Study** — Tap-to-reveal card interface with difficulty ratings and AI hint
- **Create** — Topic/PDF toggle for AI flashcard generation
- **My Decks** — Searchable deck list with mastery indicators
- **Statistics** — Study progress, review history, and spaced repetition insights
- **AI Tutor** — Chat interface with answer grading and accuracy feedback
- **Onboarding** — 4-page walkthrough plus a 3-step setup flow (features, goal, notifications)
- **My Profile / Settings / Notifications / About** — Account screen and preference-driven setup (profile picture, quiet hours, toggles)

---

## Architecture

### Tech Stack
- **Language:** Kotlin
- **UI:** ViewBinding (Fragment-based navigation)
- **Architecture:** MVVM with Repository pattern
- **Database:** Room (local persistence)
- **Networking:** OkHttp + Moshi (Gemini API)
- **PDF Processing:** Apache PDFBox 2.0.24
- **Remote Config:** Firebase Remote Config (API key management)
- **Coroutines:** Structured concurrency with `viewModelScope`

### Project Structure
```
app/src/main/java/com/example/uepflip_flashcard/
├── data/
│   ├── local/              # Room entities, DAOs, AppDatabase
│   ├── repository/         # FlashcardRepository (single source of truth)
│   ├── service/            # AI generation, PDF extraction
│   └── sample/             # Debug-only sample data seeder
├── domain/
│   ├── scheduler/          # SM-2 spaced repetition engine
│   └── DeckStats.kt        # Pure helpers for mastery/due calculations
└── ui/
    ├── main/               # MainActivity + Fragments (Home, Decks, Stats, Tutor, Create, Onboarding)
    └── study/              # StudyViewModel + StudyFragment
```

### Database Schema
- **DeckEntity** — Deck metadata (name, created/updated timestamps)
- **CardEntity** — Question, answer, SM-2 state (repetitions, interval, easeFactor, nextReviewAt)
- **ReviewEntity** — Review history log (rating, timestamp)
- **DocumentEntity** — PDF metadata (filename, size, extraction status)

Cascading deletes: Deck → Cards → Reviews (Documents are orphaned on deck delete — see issue tracker)

### SM-2 Scheduling
Ratings: `1=Hard`, `2=Medium` (labeled "Normal"), `3=Easy`  
Intervals: 1 day → 6 days → `interval × newEaseFactor`  
Ease factor adjusts per rating (min 1.3, starts at 2.5)

---

## Setup

### Prerequisites
- **Android Studio** Ladybug | 2024.2.1 or later
- **JDK 25** (auto-provisioned by Gradle foojay resolver)
- **Android SDK** with API level 37 (compileSdk/targetSdk)
- **Gemini API Key** (optional — required for AI features)

### Clone the Repository
```bash
git clone https://github.com/ralph12-dev/UEPflip.git
cd UEPflip
```

### Configure Firebase & Gemini API
1. **Firebase Setup**  
   The `app/google-services.json` file is already committed (public project config).
   
2. **Gemini API Key**  
   The API key is read from **Firebase Remote Config** at runtime under the key `gemini_api_key`:
   - Go to the [Firebase Console](https://console.firebase.google.com/)
   - Navigate to your project → Remote Config
   - Add a parameter: `gemini_api_key` with your [Gemini API key](https://makersuite.google.com/app/apikey)
   - Publish changes

   **Without a key:** AI features (Create with AI, Tutor, Hint) will show "AI features are not set up yet."

3. **Gemini model (optional)**  
   The model id defaults to `gemini-flash-latest` (`AiFlashcardGenerator.DEFAULT_MODEL`). To pin a
   different one without shipping a build, add a Remote Config parameter `gemini_model` set to an id
   printed by the `curl` below, and publish. A leading `models/` is stripped, so the id can be pasted
   straight from that response. A new value is picked up the next time an AI screen is opened — not
   mid-request, but possibly mid-session (leave and re-enter Create or Study).

   **`error 404`:** the configured model id is no longer served. Google retires model ids on a
   schedule — `gemini-2.0-flash` was shut down on 2026-06-01 and now returns `404`, which breaks
   every AI feature at once (Create, Tutor, Hint all share `AiGeneratorProvider`). List what the key
   can actually serve, then set `gemini_model` to one of those ids and publish:

   ```bash
   curl "https://generativelanguage.googleapis.com/v1beta/models?key=YOUR_KEY"
   ```

   A blank or malformed `gemini_model` falls back to the default rather than failing, so a bad value
   shows up in Logcat under tag `AiGeneratorProvider` — it does not crash the app.

   Use a **text** `generateContent` model (e.g. `gemini-*-flash`, `gemini-*-flash-lite`). Image, TTS
   and Live variants are rejected because the app sends only text parts and reads a text part back.
   The short calls (Hint, Tutor grading, explanation) request `thinkingLevel: "low"`; that field is
   only sent to `gemini-3*`, `gemini-4*` and `*-latest` ids, because Gemini 2.x rejects it (it uses
   `thinkingBudget` instead).

### Debug Sample Data
Debug builds auto-seed realistic sample data on first launch:
- 4 decks (untouched, partly mature, mostly mature, fully overdue)
- Cards at various SM-2 intervals
- Review history

To reset: Clear app data in **Settings → Apps → UEPFlip → Storage**

---

## Build

### Commands (Windows)
```bash
# Compile check
.\gradlew.bat :app:compileDebugKotlin

# Build debug APK
.\gradlew.bat assembleDebug
# Output: app/build/outputs/apk/debug/app-debug.apk

# Build release APK (unsigned)
.\gradlew.bat assembleRelease

# Run unit tests (57 tests, no emulator needed)
.\gradlew.bat testDebugUnitTest

# Run instrumented tests (requires emulator/device)
.\gradlew.bat connectedDebugAndroidTest

# Lint check
.\gradlew.bat lintDebug
```

### Commands (macOS/Linux)
Replace `.\gradlew.bat` with `./gradlew`

### Gradle Configuration
- **Wrapper:** 9.6.0
- **AGP:** 9.4.1
- **Kotlin:** 2.3.21
- **Config Cache:** Enabled
- All dependency versions: `gradle/libs.versions.toml`

---

## Testing

### Unit Tests (JVM, no device)
- **Sm2SchedulerTest** — SM-2 interval calculations
- **ReviewApplicationTest** — Review persistence logic
- **DeckStatsTest** — Mastery/due/label helpers
- **AiFlashcardGeneratorTest** — Gemini API request/response parsing (stubbed OkHttp)
- **SampleDataSeederTest** — Debug sample data validation
- **DeckNamingTest** — Default deck title derivation

**Current Status:** 57/57 passing ✅

### Instrumented Tests
None configured. The app uses `testOptions.unitTests.isReturnDefaultValues = true` for JVM tests.

---

## Known Issues & Roadmap

### Current Limitations
- **No Manual Card Creation** — Cards are only created via AI import (intentional design choice)
- **Light Theme Only** — No dark mode (warm paper palette is part of the product identity)
- **No Signing Config** — Release builds are unsigned
- **No CI/CD** — Manual testing and build process
- **Orphaned Documents** — Deleting a deck doesn't cascade to `DocumentEntity` (FK missing)

### Future Improvements
- [ ] Rotation-safe deck dialogs (3.5)
- [ ] Deck name validation (length limits, duplicate prevention)
- [ ] DiffUtil for `MsgAdapter` (Tutor chat list)
- [ ] Search IME action (Decks screen)
- [ ] Recent decks ordering by last study time
- [ ] `DocumentEntity` foreign key with cascade delete
- [ ] Signing config for release builds
- [ ] ProGuard/R8 minification (currently disabled)

---

## Contributing

Contributions are welcome! Please:
1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Follow the existing code style (see `AGENTS.md` for architecture patterns)
4. Ensure all 57 unit tests pass (`.\gradlew.bat testDebugUnitTest`)
5. Commit with clear messages (`git commit -m 'feat: add amazing feature'`)
6. Push to your branch (`git push origin feature/amazing-feature`)
7. Open a Pull Request

### Code Quality Checklist
- [ ] ViewModels use `StateFlow` for UI state
- [ ] Repository methods wrapped in `withTransaction` for multi-table writes
- [ ] Strings extracted to `res/values/strings.xml`
- [ ] Content descriptions on interactive elements, `@null` on decorative
- [ ] Touch targets ≥48dp
- [ ] No hardcoded dates/times (use System.currentTimeMillis())
- [ ] CancellationException rethrown in coroutines

---

## License

MIT License

Copyright (c) 2026 UEPFlip Contributors

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.

---

## Acknowledgments

- **Spaced Repetition Algorithm:** [SM-2 by SuperMemo](https://www.supermemo.com/en/archives1990-2015/english/ol/sm2)
- **AI Integration:** [Google Gemini API](https://ai.google.dev/)
- **PDF Processing:** [Apache PDFBox](https://pdfbox.apache.org/)
- **Design Reference:** Partner repository `appdevvvvv` (13 canonical layouts)

---

## Support

For questions, bug reports, or feature requests:
- **Issues:** [GitHub Issues](https://github.com/ralph12-dev/UEPflip/issues)
- **Developer Notes:** See `AGENTS.md` for architecture details and agent instructions

**Last Updated:** October 15, 2026
