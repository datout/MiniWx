# Changelog

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
