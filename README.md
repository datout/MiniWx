# MiniWx

A small, self-use WeChat enhancement module built around LSPosed/Xposed and
DexKit. The project intentionally keeps the default injection surface small and
adds capabilities only when a feature actually needs them.

## Current version: 0.3.0

Implemented:

- WeChat main-process LSPosed/Xposed entry;
- per-feature failure isolation;
- DexKit-based dynamic method resolution;
- experimental anti-recall;
- runtime capability declarations for future advanced features;
- centralized process policy;
- optional native/Zygisk architecture placeholders (not built or loaded yet).

MiniWx 0.3 has **no INTERNET permission** and currently loads only the WeChat
main process (`com.tencent.mm`).

## Architecture

```text
app
  -> LSPosed Java core          [enabled]
       -> HostLifecycle
       -> AntiRecall
       -> DexKit
  -> NativeBackend facade       [reserved]
       -> native-core           [future / optional]
       -> zygisk-loader         [future / optional]
```

See [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) for the extension model.

## Anti-recall

The current implementation dynamically locates WeChat's XML parser with DexKit.
For a locally parsed `sysmsg/revokemsg`, it prevents the normal local recall
handler from recognizing the event.

Current limitation: 0.3 does not yet distinguish a recall initiated by the local
user from somebody else's recall, and it does not insert a separate recall tip.

## Build

Requirements:

- JDK 21
- Android SDK 37

Windows:

```bat
bootstrap-gradle.bat :app:assembleDebug
```

Git Bash:

```bash
./bootstrap-gradle.sh :app:assembleDebug
```

APK output:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Install

1. Install the APK.
2. Enable MiniWx in LSPosed.
3. Select only WeChat as the module scope.
4. Force-stop and restart WeChat.
5. Search LSPosed logs for `[MiniWx]`.

Expected bootstrap logs include:

```text
[MiniWx] entry loaded: package=com.tencent.mm, process=com.tencent.mm
[MiniWx] runtime capabilities=[JAVA_HOOK]
[MiniWx] installed: HostLifecycle
```

## License

GPL-3.0. See `LICENSE` and `NOTICE`.
