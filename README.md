<p align="center"><img src="header.png" width="200" alt="Wais Logo"></p>

<div style="align:center">

### Wais - Realtime Fact Checker & Analyzer

</div>

The first **real-time** AI fact-checker for social media. Point the floating button at any post or share a link — get instant verdicts backed by sourced evidence.

---

> **Source snapshot for static analysis.** This repository contains the app source only. Build files, Gradle configuration and API key setup have been removed on purpose, so it is not buildable and is not updated with ongoing development.

## How the app is used

1. **Permissions** — Grant overlay permission and enable the accessibility service when prompted.
2. **Scan** — Browse any social app and tap the floating button, or share a link directly to Wais.

## Core features

- **Overlay capture** — Floating button reads any screen content via Android's AccessibilityService and sends it to the AI for claim extraction and sourcing.
- **Share intent** — Receive shared links from any app, fetch page content with Jina.ai, extract the main claim, and verify with sourced evidence.
- **Real-time loading states** — Live progress updates as content is fetched, claims extracted, and sources searched.
- **Verdicts & legitimacy score** — AI-powered verdicts (TRUE/FALSE/MISLEADING) with a legitimacy percentage.
- **Sourced evidence** — Claims are cross-referenced with credible sources via the Linkup API.
- **Archive** — All fact checks are saved locally with full details, verdict, and sources. Tap any saved check to review or share it.
- **Share** — Share any fact check result as clean structured text to any app.
- **Dark/light mode** — Toggle between themes from settings. Dark mode is the default.
- **No account required** — Everything is stored locally on device.

## Tech stack

- **Language**: Kotlin
- **UI**: Jetpack Compose + XML overlays
- **AI**: OpenRouter (LLM for claim extraction)
- **Search**: Linkup (source retrieval), Jina.ai (page fetching)
- **Storage**: Room (SQLite) for local archive
- **Platform**: Android (AccessibilityService, OverlayService)

## License

MIT License
