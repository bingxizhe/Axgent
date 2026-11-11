# Axgent

[中文](README.md) | [English](README.en.md)

A general-purpose GUI agent for Android: describe a task in natural language, and Axgent reads the screen, taps, swipes, fills forms and edits files on its own.

- Engine: bundled AutoX.js v7 (`android/`, v7.2.4), scripts run on **Rhino (ES5)**
- UI: native Compose (mist-glass theme), Chinese / English switchable
- Package / entry: `com.axgent.agent` / `org.autojs.autojs.agent.AgentChatActivity`
- Download: [GitHub Releases](https://github.com/bingxizhe/Axgent/releases)

## Features

- **Natural-language tasks**: describe the goal; Axgent plans, observes, acts and reports back
- **Screen understanding**: UI node tree + screenshots + OCR, with frame-stability detection; falls back to Shizuku when accessibility is unavailable
- **GUI actions**: tap, long-press, swipe, type, back, home, launch apps
- **Three permission tiers**: accessibility → Shizuku → root, with global and per-Agent switches and hard enforcement in the tool layer
- **Background virtual display**: run other apps on a separate Display without touching the physical screen; parallel sub-agents
- **Skills**: progressive SKILL.md loading, stored in app-private storage, ZIP import/export
- **Model management**: multiple providers, separate chat and vision models, switch anytime
- **Safe by default**: Axgent's own UI is off-limits, file sandbox, trash, approvals, root audit log
- **Bilingual UI**: follows the system language on first launch, switchable in Settings

## Documentation

| Doc | Content |
| --- | --- |
| [docs/BLUEPRINT.md](docs/BLUEPRINT.md) | Design baseline (history and boundaries) |
| [docs/TOOLS.md](docs/TOOLS.md) | Tool catalog, parameters, risk and hard limits |
| [docs/UI.md](docs/UI.md) | Screens, back stack, settings, performance |
| [docs/VD.md](docs/VD.md) | Virtual display, parallel sub-agents, node-tree backends |
| [docs/VERIFY.md](docs/VERIFY.md) | On-device verification log |
| [docs/CACHE.md](docs/CACHE.md) | Context and KV-cache optimization |
| [docs/WEB.md](docs/WEB.md) | Web search / fetch provider chain |
| [docs/PERFORMANCE.md](docs/PERFORMANCE.md) | Frame-rate and latency measurement |
| [docs/HANDOFF-PROMPT.md](docs/HANDOFF-PROMPT.md) | Handoff prompt template |

## Build and install

Requirements: JDK 17 and Android SDK 34. A Gradle wrapper is included, so no separate Gradle install is needed.

```powershell
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"

# Build the agent variant (drop --offline on first build or dependency changes)
Push-Location .\android
.\gradlew :app:assembleAgentProd --offline
Pop-Location

# Output: android\app\build\outputs\apk\agent\prod\app-agent-arm64-v8a-prod.apk
& $adb install -r .\android\app\build\outputs\apk\agent\prod\app-agent-arm64-v8a-prod.apk
& $adb shell am start -n com.axgent.agent/org.autojs.autojs.agent.AgentChatActivity
```

> The first build needs network access for dependencies; later builds can use `--offline`. If it fails, check the SDK path in `android/local.properties`.

## Usage

The Agent scripts are bundled in `assets/agent-src` and deployed to `/sdcard/phone-agent/src` on first install, app upgrade or when missing. A device-side `config/local.js` is never overwritten.

1. Complete onboarding: accessibility, all files access, battery allowlist, notifications (overlay optional)
2. Send a task in the input box; steps appear live. In background virtual-display mode the physical screen stays free
3. Stop with the Stop button, the notification action, or double-press volume-down in foreground mode (long-press = pause/resume)
4. Logs: `/sdcard/Download/phone-agent/runs/<timestamp>/` (`main.log` / `steps.jsonl` / `final.md`)

## Layout

```
src/                   Agent core (Rhino ES5)
  main.js              Entry: config validation, virtual-display setup, task loop
  core/                Agent loop, LLM, context, memory, parallel, guards
  tools/               Tool registry and implementations (file/perception/web/browser/VD…)
  perception/          Node-tree serialization, screenshots/OCR, stability detection
  skills/              SKILL.md library and auto-capture
  config/              default.js; local.js for local secrets (gitignored)
  ui/                  JS-side panel/preview helpers
android/               Android project (AutoX v7 engine + Axgent Compose UI)
  app/                 App module; product UI lives in app/src/main/java/org/autojs/autojs/agent
runtime/               Native sources for the virtual display / tool side (built into android/app/src/main/assets/vd)
  vd-server-go/        Go control service (:3070)
  vd-tool-java/        DaemonMain / ToolMain
  agent-hook-apk/      Optional LSPosed hook
scripts/               PC-side helper scripts (push, log export, etc.)
docs/                  Design and verification docs
```

Runtime data on device:

```
/sdcard/phone-agent/src/                 deployed scripts
/sdcard/Download/phone-agent/runs/       per-run logs and tool results
/sdcard/Download/phone-agent/memory/     FACT.md + journal.jsonl
/sdcard/Download/phone-agent/ui-config.json   config written by the UI (models, capabilities, appearance, permissions)
/data/data/com.axgent.agent/files/agent/skills/  private SKILL.md library (each skill has work/)
/sdcard/Download/Axgent/skills/          skill ZIP exports
```

## Configuration

- Defaults: [src/config/default.js](src/config/default.js); the full schema is committed
- Local development: put only overrides in `src/config/local.js` (gitignored)

```js
module.exports = {
  llm: { baseUrl: 'https://api.deepseek.com', apiKey: 'sk-...', model: 'deepseek-flash' },
  // Global permission tiers: root defaults to off; the Agent also needs capabilities.root
  privilege: { accessibility: true, shizuku: true, root: false }
};
```

- Models, capability switches, global permission tiers, popup handling, sandbox roots, appearance and language set in the app are written to `ui-config.json` and override script defaults
- API keys stay in a local file; they are never uploaded, logged or echoed in the UI

## Capabilities and tools

- 58 candidate tools, trimmed dynamically by mode and capability switches: 52 in foreground mode; **42** with the background virtual display enabled — `gui_*`, `shot`, `look`, `ocr`, `wait_text`, `app_list` and `notify_read` are replaced by the unified `mobile` tool, `root_shell` only appears when Agent Root is on, and `a11y_ensure` appears when Shizuku or root is available
- Capability switches (Settings → Capabilities) are enforced both when generating the tool schema and in `execute`, independent of prompts
- Background virtual display: slot 7 for the parent task, 1..N for sub-agents; `spawn_task` runs in parallel with a limit of `min(6, maxParallelDisplays)`; sub-agents cannot spawn further agents
- Node-tree backend: direct accessibility service (concurrent, lock-free) first; falls back to serial ToolMain when accessibility is unavailable
- Permission tiers: foreground GUI actions degrade accessibility → Shizuku → root; privileged settings (enabling accessibility, ColorOS allowlist) use Shizuku → root. Settings → Runtime & security shows the three states and lets you add the app to the allowlist
- Global switches: accessibility / Shizuku / root are independent. When off, neither the app nor the Agent may use that tier; the Agent capability panel mirrors them and greys out when globally off; enabling global accessibility/Shizuku auto-enables the Agent counterparts, while Root stays off by default
- Own-UI guard: while `com.axgent.agent` is in the foreground the Agent may only go back, go home, launch other apps and read/write files; observation, taps, typing, swipes and key events are blocked in code
- Popup handling: a global switch under Settings → Runtime & security that only handles allowlisted ColorOS accessibility prompts, ordinary runtime permissions and ANR/update dialogs; it never auto-grants Shizuku/root/accessibility
- Accessibility auto-recovery: if accessibility drops mid-task, Axgent re-enables the service through Shizuku (works without root) and waits for reconnection; when Agent Root is off it will never fall back to `su` via global Root. The model can also call `a11y_ensure` explicitly

## Security defaults

- Global `privilege.root` and Agent `capabilities.root` both default to **false**: `root_shell` is not exposed, and execution checks both switches and writes `root-audit.jsonl`
- The background virtual display and `root_shell` remain root-only; Shizuku covers GUI actions, `pm list packages`, notifications and secure settings, but never downgrades `root_shell` to shell
- Root hard denylist (refused even when enabled): enabling accessibility, `pm grant`, `app_process`, writing `ui-config.json`, writing `/data/local/tmp/`, launching Axgent's own Activity
- File tools cannot access app-private storage, the AutoX script directory, `/data/local/tmp` or the export directory, preventing the Agent from creating scripts to bypass permissions
- The skill library lives in app-private storage, so external edits have no effect; Settings → Skills supports ZIP export/import per skill (path traversal and oversized files are rejected)
- Settings → Runtime & security → Permission tiers shows accessibility / Shizuku / root state and can add the app to the ColorOS `accessibility_turn_off_skip_package` allowlist (written via Shizuku → root). The allowlist covers periodic reminders; the **first prompt after enabling or reinstalling still appears** — tap "Keep on"
- File tools default to `/sdcard/Download/phone-agent` and `/sdcard/Documents`
- Deletions go to the trash (`runs/.trash/`) instead of being unlinked
- System paths such as `runs/`, `conversations/`, `src/`, `task.txt`, `control.txt` and `ui-config.json` cannot be written or deleted by file tools; approval channels like `approval.txt` / `answer.txt` are protected too, so the Agent cannot bypass approvals
- `web_fetch` only allows http/https and blocks private-network and credentialed URLs by default
- Defaults: 25 steps / 10 minutes per task; the context window comes from the provider and is compressed automatically at 80%

## Tested environment

| Item | Value |
| --- | --- |
| Device | OnePlus PLC110 (Android 16 / API 36, 1272×2800 @560dpi) |
| min / target SDK | 27 / 34 |
| AutoX | bundled v7.2.4 (Rhino engine, scripts written in ES5) |
| Root | KernelSU (optional; required for the virtual display and `root_shell`) |
| Default models | `deepseek-flash` (chat) + `glm-4.6v-flash` (vision) |

## Credits

Axgent borrows from the following projects. What was borrowed is listed explicitly; everything else is original work.

### AutoX.js

The execution engine is based on [AutoX.js](https://github.com/kkevsekk1/AutoX) v7. The `android/` tree is a customized fork; changes are concentrated in `app/src/main/java/org/autojs/autojs/agent/`: the Agent chat UI, permission tiers, tool bridge and virtual-display orchestration.

### Cherry Studio

[CherryHQ/cherry-studio](https://github.com/CherryHQ/cherry-studio) is a cross-platform AI workstation. Axgent borrowed its information architecture and several interaction patterns:

- **Provider / model separation**: a provider stores connection info (base URL, API key, enabled state), while chat and vision models reference it; one connection serves both model types, and each model can be enabled or disabled independently
- **Model management UI**: provider list, model library, creating a connection from a preset, model search and multi-select
- **Progressive SKILL.md loading**: the skill catalog exposes only the name and description of enabled skills; the body is read when the model calls `skill_load`, so context is not flooded up front
- **Context usage indicator and auto-compaction**: the input area shows context usage and compacts history at 80% of the provider window
- **Conversation management**: recent chats, all chats, search and batch deletion

The implementation is independent; no Cherry Studio source code was copied.

### agent-mobile-use

[AcidGr/agent-mobile-use](https://github.com/AcidGr/agent-mobile-use) provides the Android virtual-display and background-control foundation. Axgent's `runtime/` and `android/app/src/main/assets/vd` come from it:

- **Virtual display pipeline**: the `vd-server-go` control gateway (:3070), `DaemonMain` / `ToolMain` in `vd-tool-java`, and Display creation, mirroring and input injection
- **LSPosed hook**: `agent-hook-apk` for cross-display scheduling, IME isolation and popup-free text injection (optional)
- **KSU module and live-capsule notifications**: root authorization, boot startup and ColorOS live activities
- **Parallel displays**: multiple Displays at once, one slot per sub-agent; Axgent adds the `mobile` tool, `spawn_task` and a unified node-tree/screenshot backend on top

### axgent-promo-video

`axgent-promo-video` is Axgent's Remotion promo-video project. Axgent reused its brand visuals:

- **Logo and palette**: the Axgent logo, the mist-glass palette and its light/dark derivation
- **Motion pacing**: timing curves for cross-fades, selected states and background breathing
- The project is used only for promotional material and is not part of the APK build

## License scope (important)

- **Axgent original code** (`src/`, `docs/`, Kotlin/Compose code under `agent/`, etc.): [MIT License](./LICENSE).
- **`android/` engine**: contains a customized fork of [AutoX.js](https://github.com/kkevsekk1/AutoX). Upstream `android/LICENSE.md` is **MPL-2.0 plus a non-commercial restriction**, with `android/LICENSE-GPL-V2.md` alongside; therefore `android/` and its derivatives are **not MIT-licensed** and may not be used commercially or for profit.
- **`runtime/` and `android/app/src/main/assets/vd`**: from [agent-mobile-use](https://github.com/AcidGr/agent-mobile-use); follow that project's license.
- When distributing the repository or APK you must also comply with the upstream licenses above. For commercial use, obtain the appropriate permission or replace/remove the restricted components.
