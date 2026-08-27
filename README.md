# MiniWx

A small, self-use WeChat enhancement module built around LSPosed/Xposed. Native/Zygisk stays an optional future backend rather than a hard dependency.

## 0.6.0

Implemented and connected to WeChat hooks:

- LSPosed/Xposed entry for `com.tencent.mm`
- Main-process-only loading policy
- Runtime heartbeat / detected WeChat version
- Anti-recall + recall notice + conservative own-recall allowance
- Shared DexKit-resolved message-row binding service
- Precise message time (`yyyy/MM/dd HH:mm:ss`)
- Incoming sender wxid display + long-press copy
- Group-chat sender wxid beside the member name
- Group owner / administrator badges (optional member badge)
- Auto “查看原图 / 查看原视频” in gallery UI
- Auto voice-to-text using WeChat's own TransformComponent; clears the unplayed voice marker through WeChat's local VoiceLogic
- Notification MessagingStyle conversion
- Same-conversation notification merging + cancel-ID translation
- Per-hook and per-listener exception isolation
- Background serialized DexKit resolver queue to reduce WeChat cold-start blocking
- Automatic GitHub Release publishing after successful `main` builds

The project does not modify WeChat network traffic and does not contain Xposed/Root concealment or risk-control bypass logic.

## Build

Requirements:

- JDK 21
- Android SDK 36

Git Bash / Linux:

```bash
./bootstrap-gradle.sh :app:assembleDebug
```

APK:

```text
app/build/outputs/apk/debug/app-debug.apk
```

GitHub Actions also uploads `MiniWx-<version>-apk` under **Artifacts**.

## GitHub Release

A normal push to `main` now publishes the current version automatically after a successful build. For `0.6.0`, the workflow creates or updates tag/release `v0.6.0` and attaches:

```text
MiniWx-0.6.0.apk
MiniWx-0.6.0.apk.sha256
```

So the normal release flow is simply:

```bash
git add .
git commit -m "MiniWx 0.6.0"
git push
```

No separate tag command is required. Bump `versionName`/`versionCode` before the next release.

## Scope

MiniWx currently loads only the WeChat main process. `native-core` and `zygisk-loader` remain optional architecture placeholders.

## License

GPL-3.0. See `LICENSE` and `NOTICE`.
