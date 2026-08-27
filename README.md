# MiniWx

A small, self-use WeChat enhancement module built around LSPosed/Xposed. Native/Zygisk is reserved as an optional extension backend for future features rather than a hard dependency.

## 0.5.0

Implemented and connected to WeChat hooks:

- LSPosed/Xposed entry for `com.tencent.mm`
- Main-process-only loading policy
- Runtime heartbeat / detected WeChat version
- Anti-recall + recall notice + conservative own-recall allowance
- Shared DexKit-resolved message-row binding service
- Precise message time (`yyyy/MM/dd HH:mm:ss`)
- Incoming sender wxid display + long-press copy
- Group-chat sender wxid beside the member name
- Auto “查看原图 / 查看原视频” in gallery UI
- Per-hook and per-listener exception isolation

Still intentionally disabled until a real implementation is connected:

- Voice enhancements
- Notification enhancements

The current release does not modify WeChat network traffic and does not contain Xposed/Root concealment or risk-control bypass logic.

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

GitHub Actions uploads it as `MiniWx-0.5.0-debug-apk`.

## Scope

MiniWx currently loads only the WeChat main process. `native-core` and `zygisk-loader` remain optional architecture placeholders.

## License

GPL-3.0. See `LICENSE` and `NOTICE`.
