# MiniWx

MiniWx is a small self-use WeChat enhancement module built around LSPosed/Xposed. Native/Zygisk remains an optional future backend rather than a hard dependency.

## 0.7.1

Implemented and connected to WeChat hooks:

- LSPosed/Xposed entry for `com.tencent.mm` and main-process-only loading
- Runtime heartbeat, detected WeChat version, and per-hook compatibility/status card
- Anti-recall + optional recall notice
- Own-recall allowance now **first queries WeChat's persisted local `message` table by `msgSvrId` and reads `isSend`**; process snapshots and recall text are only fallbacks
- Precise message time (`yyyy/MM/dd HH:mm:ss`)
- Incoming sender wxid display + long-press copy
- Group-chat sender wxid + owner/administrator badges
- Auto “查看原图 / 查看原视频”
- Auto voice-to-text through WeChat's own TransformComponent
- **Favorite voice forwarding**
  - chat favorite-picker path (when a current conversation is known)
  - `我 -> 收藏 -> 转发` path, including multiple recipients
- **Original voice export**
  - enable “保存原始语音”
  - tap the `保存语音` action shown on a voice message's time row
  - Android 10+ writes to `Download/MiniWx`
  - this exports WeChat's original encoded voice file; it is not converted to MP3 yet
- Notification MessagingStyle conversion + same-conversation merging/cancel-ID translation
- MessagingStyle sender entries reuse WeChat's original notification large icon when available
- Experimental removal of the classic 100-message multi-select limit on WeChat versions that still use `ChattingDataAdapterV3`
- Serialized background DexKit resolution so expensive lookups do not block `Application.attach`
- Automatic GitHub Release publishing after successful `main` builds

Features are isolated: a failed/obsolete hook is logged and shown in the MiniWx status page instead of stopping the rest of the module.

## Not in 0.7 yet

The chat toolbar and swipe-to-quote features are not represented by fake switches. Their UI/API chain is more version-sensitive and will be added only after the underlying WeChat hooks are connected and testable.

MiniWx does not modify WeChat network traffic and does not contain Xposed/Root concealment or risk-control bypass logic.

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

A successful normal push to `main` creates or updates the release matching `versionName`. For `0.7.1`, the workflow creates/updates `v0.7.1` and attaches:

```text
MiniWx-0.7.1.apk
MiniWx-0.7.1.apk.sha256
```

Normal release flow:

```bash
git add .
git commit -m "MiniWx 0.7.1"
git push
```

No separate tag command is required.

## Test checklist for 0.7.1

1. Enable MiniWx in LSPosed for WeChat, force-stop WeChat, then reopen it.
2. Open MiniWx and verify the runtime heartbeat plus Hook compatibility card.
3. Test persistent anti-recall: send/receive messages, force-stop and reopen WeChat, then recall those pre-restart messages from the other device/account. Verify incoming recall is blocked while your own recall remains normal.
4. Enable `语音相关增强` + `收藏语音转发`, then test both a favorite picker from chat and `我 -> 收藏 -> 转发`.
5. Enable `保存原始语音`, open a chat containing a voice message, and tap `保存语音` on its time row.
6. Enable notification enhancement and verify merged MessagingStyle notifications are cleared after reading.
7. If your WeChat still has `ChattingDataAdapterV3`, test selecting more than 100 messages. If not, the Hook status page should show that this compatibility path failed instead of crashing WeChat.

## Scope

MiniWx currently loads only the WeChat main process. `native-core` and `zygisk-loader` remain optional architecture placeholders.

## License

GPL-3.0. See `LICENSE` and `NOTICE`.
