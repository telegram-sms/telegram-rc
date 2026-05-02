# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

Android app (Kotlin) that exposes the device to a Telegram bot for remote control: SMS relay, USSD, mobile data / Wi-Fi / tethering toggles, dual-SIM switching, beacon presence detection, call log, etc. Most privileged operations go through Shizuku rather than root. Min SDK 29, target/compile SDK 36.

## Build commands

The Gradle Kotlin toolchain is JVM 21 (`jvmToolchain(21)` in [app/build.gradle](app/build.gradle)) — BUILD.md still says JDK 17, but JDK 21 is what the build expects.

```bash
./gradlew assembleDebug              # debug APK -> app/build/outputs/apk/debug/
./gradlew assembleRelease            # release APK (requires signing env vars)
./gradlew clean
./gradlew :app:lint                  # Android lint
git submodule update --init --recursive   # required after fresh clone
```

There is no test source set (`app/src/` only has `main`); do not invent test commands.

Release signing reads from env vars or `-P` properties: `KEYSTORE_PASS`, `ALIAS_NAME`, `ALIAS_PASS`, with keystore expected at `app/keys.jks`. Version is also env-driven: `VERSION_CODE`, `VERSION_NAME`. Release builds are restricted to `arm64-v8a` via `abiFilters`.

The `luch` (beacon) dependency is fetched from a private GitHub Packages repo (see [build.gradle](build.gradle)) — needs `USERNAME` / `GITHUB_ACCESS_KEY` env vars (or `gpr.user` / `gpr.key` properties) for fresh dependency resolution. There are also two git submodules under `app/src/main/java/com/github/sumimakito/` (AwesomeQrRenderer, CodeauxLibPortable) — code expects them present.

## Architecture

Everything lives in one module under `com.qwe7002.telegram_rc`. There is no MVVM / DI framework — it is service-driven imperative Kotlin with global state in MMKV.

### Runtime entry points (services and receivers)

- **[ChatService](app/src/main/java/com/qwe7002/telegram_rc/ChatService.kt)** — the heart of the app. Long-poll loop against Telegram `getUpdates`, dispatches commands in a giant `when (command)` switch, and is also invoked by other components to push outbound messages. New bot commands are added here.
- **BeaconReceiverService** — foreground service (`location` type) that scans BLE beacons and reports presence/absence. Beacon definitions live under [beacon/](app/src/main/java/com/qwe7002/telegram_rc/beacon/) and persisted models in [data_structure/BeaconModel.kt](app/src/main/java/com/qwe7002/telegram_rc/data_structure/BeaconModel.kt).
- **BatteryService** + **BatteryNetworkJob** — periodic battery / network state push.
- **NotifyListenerService** — `NotificationListenerService` for forwarding selected app notifications.
- **SMSReceiver / CallReceiver / BootReceiver / SMSSendResultReceiver** — broadcast receivers that funnel events into ChatService / outbound Telegram requests.
- **KeepAliveJob / ReSendJob / CcSendJob** — `JobService` schedulers for keep-alive, retry queue, and CC-SMS forwarding.

Activities (MainActivity, BeaconActivity, CcActivity, SpamActivity, ExtraSwitchActivity, NotifyActivity, QRCodeActivity, ScannerActivity, LogcatActivity, YellowPageSyncActivity) are mostly settings UIs that read/write MMKV; they do not own runtime behavior.

### Cross-cutting layers

- **`static_class/`** — stateless `object` utilities grouped by domain (Network, Phone, SMS, USSD, Battery, Hotspot, Notify, Other, ServiceManage, DataUsage, ArfcnConverter, MdnsResponder, BeaconDataRepository). Treat these as the canonical helpers; service code calls into them rather than reimplementing logic.
- **`shizuku_kit/`** — wrappers around system services accessed via Shizuku (`Telephony`, `ISub`, `IPhoneSubInfo`, `SVC` for connectivity toggles, `TetheringManagerShizuku`, `VPNHotspot`, `ShizukuKit`). All privileged telephony / connectivity calls go through here. Always gate with `Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PERMISSION_GRANTED`.
- **`data_structure/telegram/`** — `RequestMessage`, `PollingJson` Gson payloads. Other data classes (SMSRequestInfo, BeaconModel, GitHubRelease, ScannerJson, OutputMetadata, LogAdapter, CcSendService) sit directly in `data_structure/`.
- **`MMKV/`** — `MMKVKey.kt` defines the named MMKV instance IDs (chat_info, beacon, status, proxy, resend, upgrade, IMSI, data_plan, update). Use these IDs verbatim — they are the persistent contract between services and activities. `Const.SETTINGS_MMKV_ID` is the default unnamed instance.
- **`database/yellowpage/`** — Room database (`com.qwe7002.telegram_rc.Room.YellowPage`, version 1) with `Organization` + `PhoneNumber` entities for the contact/yellow-page feature; accessed via `AppDatabase.getDatabase(context)`.
- **`value/`** — `Const.kt` (`JSON_TYPE`, `SYSTEM_CONFIG_VERSION`, `RESULT_CONFIG_JSON`) and `LogTags.kt` (`TAG = "Telegram-RC"`).

### Networking

OkHttp is used everywhere. `Network.getOkhttpObj` builds the client (with optional DNS-over-HTTPS via `cloudflare-dns.com` and proxy/auth from the `proxy` MMKV instance). `Network.getUrl(token, method)` constructs Telegram Bot API URLs. Outbound messages are `Gson().toJson(RequestMessage).toRequestBody(JSON_TYPE)`.

### Conventions enforced by [.github/copilot-instructions.md](.github/copilot-instructions.md)

That file is the source of truth for code patterns; key items worth honoring even when not explicitly mentioned in a request:

- System-originated Telegram messages are prefixed with `getString(R.string.system_message_head)\n…` and use HTML parse mode.
- Log with `Log.{d,e,i,w}(TAG, …)` using `value.TAG`.
- Always validate inbound chat ID / message_thread_id against the configured values before acting.
- Foreground services must call `startForeground` with the correct `FOREGROUND_SERVICE_TYPE_*` for Android 14+ (manifest declares `location` and `specialUse` types).
- Release WakeLock / WifiLock in `onDestroy()`; existing services already follow this.

## After code changes

Per the user's global instruction: run a Gemini review on the diff before declaring code work complete:

```bash
git diff | gemini -p "Review for bugs, race conditions, and deadlocks"
```

Surface the findings, fix real issues, and explain false positives. Skip only for docs / comment / config-value-only edits. If `gemini` is not available, say so rather than silently skipping.
