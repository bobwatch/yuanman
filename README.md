# Yuanman

<p align="center"><img src="docs/logo.png" width="120" alt="Yuanman logo"></p>

> Yuanman — Money, Habits & Mood. Three everyday things, one calm app.
> 中文简介：沅满 —— 记账 · 打卡 · 心情，一个 App 管好三件事。

**[中文文档](README.zh-CN.md)** | Free and open-source. No server, no account — your data never leaves your LAN.

[![CI](https://github.com/bobwatch/yuanman/actions/workflows/ci.yml/badge.svg)](https://github.com/bobwatch/yuanman/actions/workflows/ci.yml)
[![Release](https://github.com/bobwatch/yuanman/actions/workflows/release.yml/badge.svg)](https://github.com/bobwatch/yuanman/actions/workflows/release.yml)
[![GitHub release](https://img.shields.io/github/v/release/bobwatch/yuanman)](https://github.com/bobwatch/yuanman/releases)
[![License: MIT](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)
[![minSdk 24](https://img.shields.io/badge/Android-7.0%2B-blue)](https://github.com/bobwatch/yuanman)

## Features

**Money**

- Two-step recording: type the amount on the built-in keypad, tap a category — saved
- Keypad supports chained addition; no system keyboard needed
- Expense/income, emoji categories, notes, backdating via Material 3 DatePicker
- Tap to edit, swipe to delete with undo, long-press to duplicate
- Recurring bills (weekly/monthly/yearly) recorded automatically on due date
- Monthly budget with progress bar, today's spending, recording streak,
  last-month summary card, search, custom categories, recent-categories-first
- Savings goals with animated progress rings, ETA prediction, milestone
  celebrations and a confetti finale
- Home-screen widget: today / this month at a glance, "+" jumps to the keypad

**Habits**

- Build habits: one-tap daily check-in with streak counting, undo supported
- Quit habits: days-clean counter accumulates automatically, relapse resets with confirmation
- 8 one-tap presets (gym, reading, early rise, quit soda/alcohol/smoking/late nights…)

**Mood**

- One tap per day: 😄 🙂 😐 😔 😠, plus an optional one-line note
- Monthly donut breakdown, angry-days counter, days-without-anger streak
- Dynamic encouragement messages, month grid of colored mood dots
- Mood × spending insight: average daily spend on angry days vs. calm days

**Badges**

- 12 badges across money / habits / mood, confetti on unlock, badge wall with earned dates

**Charts** — category donut, 6-month trend, daily curve; all hand-drawn on Compose Canvas, zero chart libraries.

**Sync & privacy**

- Family sync over LAN: NSD discovery + TCP merge, 6-digit pairing code (only its SHA-256 prefix is exchanged)
- No server, no sign-up, no tracking; JSON files in app-private storage with atomic writes and automatic backups
- Export JSON + CSV; import with merge or overwrite mode
- Chinese / English UI follows the system language
- v0.0.2 polish pass: form state survives process death (rememberSaveable),
  unsaved-input discard confirmation, tappable overview card, back-closes-search,
  snackbars no longer cover the FAB, withdrawal overdraft guard, density-aware
  chart labels, refined contrast for light/dark themes

## Screenshots

> Placeholders — contributions welcome.

| Money | Habits | Mood | Me |
| --- | --- | --- | --- |
| ![Money](docs/screenshots/home.png) | ![Habits](docs/screenshots/habits.png) | ![Mood](docs/screenshots/mood.png) | ![Me](docs/screenshots/mine.png) |

## Download

- Get the latest APK from [Releases](https://github.com/bobwatch/yuanman/releases)
- Requires Android 7.0 (API 24) or above
- Long-press the launcher icon for an "Add transaction" shortcut

## Tech

- Kotlin + Jetpack Compose (Material 3, BOM 2024.06.00) + Navigation Compose
- Single activity, four bottom tabs (`home / habits / mood / mine`) plus detail routes
- Zero third-party dependencies: `org.json` for serialization, Canvas for charts,
  RemoteViews for the widget, NsdManager + ServerSocket for LAN sync
- AGP 8.5.2 / Kotlin 1.9.24 / Gradle 8.7, minSdk 24 / targetSdk 34, Java 17
- i18n via resource qualifiers: `values` (English, default) + `values-zh-rCN`

**Project layout**

```
app/src/main/java/com/moneyhistory/app/
├── MainActivity.kt            # Single activity, bottom tabs, lifecycle
├── MainViewModel.kt           # All screen state & operations (badge engine driver)
├── Transaction.kt(Store)      # Ledger model & JSON store (v2 tombstones/merge/atomic writes)
├── RecurringStore.kt          # Recurring bills & due settlement
├── SavingsStore.kt            # Savings goals (milestones / ETA)
├── HabitsStore.kt             # Habits (streaks / days-clean)
├── MoodStore.kt               # Mood entries (per calendar day)
├── Badges.kt                  # Badge catalog & evaluation engine
├── Categories.kt(Store)       # Preset + custom categories
├── SettingsStore.kt           # Theme / budget / badges (SharedPreferences)
├── DateUtils.kt               # Calendar/SimpleDateFormat helpers (no java.time)
├── MoneyUtils.kt              # Cents ↔ yuan formatting & parsing
├── sync/FamilySyncManager.kt  # NSD discovery + TCP merge sync
├── widget/                    # Home-screen widget (RemoteViews)
└── ui/                        # Compose screens & components (Charts.kt hand-drawn)
```

**Storage** — `filesDir/transactions.json` (v2; `amount` in cents, `deleted` tombstones
for sync); `recurring.json` / `goals.json` / `habits.json` / `mood.json` / `categories.json`
follow the same atomic-write pattern.

## Privacy

The app talks to no server and collects nothing. Permissions:

| Permission | Purpose |
| --- | --- |
| `INTERNET` | LAN TCP for family sync |
| `ACCESS_NETWORK_STATE` / `ACCESS_WIFI_STATE` | Detect LAN environment |
| `CHANGE_WIFI_MULTICAST_STATE` | Receive NSD multicast packets |
| `NEARBY_WIFI_DEVICES` (neverForLocation) | Nearby Wi-Fi discovery on Android 13+ |
| `VIBRATE` | Haptic tick on successful save |

## Build

Android Studio Hedgehog (2023.1.1) or newer — open the repo root and run.

CLI (JDK 17 + Android SDK required):

```bash
./gradlew assembleDebug      # debug build
./gradlew assembleRelease    # release build (signing via env vars, see CI)
```

## Roadmap

- [ ] Per-category budgets and overspending alerts
- [ ] Yearly statistics and more chart dimensions
- [ ] Habit reminders (notifications)
- [ ] Year-in-pixels mood calendar
- [ ] Incremental sync protocol with conflict hints

## Contributing

Issues and PRs are welcome — please read [CONTRIBUTING.md](CONTRIBUTING.md) first.
Commit messages in English; all UI copy lives in string resources
(`values` = English default, `values-zh-rCN` = Chinese).

- [Report a bug](https://github.com/bobwatch/yuanman/issues/new?template=bug_report.md)
- [Request a feature](https://github.com/bobwatch/yuanman/issues/new?template=feature_request.md)

## License

[MIT](LICENSE) © 2026 bobwatch
