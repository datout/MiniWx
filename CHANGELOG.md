# Changelog

## 0.7.0

- Add favorite voice forwarding for the chat favorite picker and `我 -> 收藏 -> 转发` path.
- Add a minimal protobuf reader for WeChat favorite voice metadata and reuse WeChat's own voice send/storage flow.
- Add original voice export to `Download/MiniWx` on Android 10+; no MP3 conversion yet.
- Improve own-recall detection using captured original-message `isSend` metadata before falling back to replacement text.
- Capture message metadata from both message-row binding and MsgInfoStorage insert paths.
- Add a Hook compatibility/status card to the MiniWx settings app.
- Reuse WeChat's original notification large icon in MessagingStyle sender entries when available.
- Add an experimental compatibility hook for the classic 100-message multi-select limit.
- Keep chat toolbar and swipe-to-quote out of the UI until their version-sensitive hook chain is actually connected.

## 0.6.0

- Publish/update the matching GitHub Release automatically after successful `main` builds.
- Add automatic voice-to-text using WeChat's own TransformComponent and clear the local unplayed voice marker.
- Add Android MessagingStyle notification conversion.
- Add same-conversation notification merging and cancel-ID translation.
- Add group owner / administrator badges, with optional ordinary-member badge.
- Long-press a group sender name to copy its wxid when wxid copy is enabled.
- Read the displayed MiniWx version from the installed package instead of hardcoding it.
- Move expensive DexKit resolution off `Application.attach` onto a serialized MiniWx background resolver queue.
- Restrict the exported settings bridge to MiniWx itself and the WeChat UID.
- Refresh runtime injection status with a lightweight four-minute heartbeat.

## 0.5.0

- Precise message timestamps.
- Incoming sender wxid display and copy.
- Group sender wxid display.
- Auto original image / video.
- Shared message-row binding API.
