# MiniWx

A small, self-use WeChat enhancement module built around LSPosed/Xposed, with an optional native/Zygisk extension architecture reserved for future features.

## 0.4.0

Implemented and connected to WeChat hooks:

- LSPosed/Xposed entry for `com.tencent.mm`
- Main-process-only loading policy
- DexKit dynamic XML parser resolution
- Anti-recall switch
- Recall notice switch (local Toast notice)
- Conservative "allow own recall" option
- Runtime heartbeat / detected WeChat version
- Per-hook exception isolation

Settings entries already included, but **not yet connected to version-specific WeChat hooks**:

- Copy wxid
- Detailed message time
- Voice enhancements
- Image enhancements
- Notification enhancements
- Group-chat enhancements

These entries are intentionally disabled until their real hooks are ported. MiniWx does not present placeholder switches as working features.

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

GitHub Actions also uploads it as the artifact `MiniWx-0.4.0-debug-apk`.

## Scope

MiniWx currently loads only the WeChat main process. The native/Zygisk folders remain architectural placeholders and are not required by the current release.

## License

GPL-3.0. See `LICENSE` and `NOTICE`.
