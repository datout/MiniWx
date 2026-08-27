# Feature status

## Active in 0.7.1

| Feature | Status | Notes |
| --- | --- | --- |
| Module settings screen | Active | Plain Android UI; no extra UI framework dependency. |
| Runtime injection status | Active | WeChat reports a heartbeat after `Application.attach`. |
| Hook compatibility card | Active | DexKit resolver success/failure is persisted and shown in MiniWx. |
| Detected WeChat version | Active | Reported from the host package. |
| Anti-recall | Experimental | DexKit resolves the XML parser and suppresses local `revokemsg` handling. |
| Recall notice | Experimental | Shows the original recall replacement text as a local Toast after blocking. |
| Persistent message DB lookup | Experimental | Resolves WeChat CoreStorage/WCDB and queries the persisted `message` row by `msgSvrId`. |
| Own recall normal | Experimental | Uses persisted database `isSend` first, then the in-process snapshot cache, then replacement text. |
| Detailed message time | Experimental | Uses the Mvvm chat row bind hook and `timeTV`. |
| Copy wxid | Experimental | Incoming sender wxid is shown on the time line; long-press it to copy. |
| Group sender ID | Experimental | Incoming group rows append the sender wxid beside the displayed member name. |
| Group role badges | Experimental | Uses chatroom data to distinguish owner/admin/member. |
| Image enhancement | Experimental | Gallery hook auto-clicks visible “查看原图 / 查看原视频” controls. |
| Auto voice-to-text | Experimental | Calls WeChat's own TransformComponent for incoming type-34 voice messages. |
| Favorite voice forwarding | Experimental | Supports the chat favorite picker and `我 -> 收藏 -> 转发` single-voice flow. |
| Original voice export | Experimental | Tap `保存语音` on a voice row; Android 10+ writes the original encoded file to `Download/MiniWx`. |
| MessagingStyle notifications | Experimental | Rebuilds eligible WeChat message notifications with Android MessagingStyle. |
| Notification merge | Experimental | Uses a stable ID per conversation and translates WeChat's original cancel ID. |
| Notification avatar reuse | Experimental | Reuses WeChat's original notification large icon in MessagingStyle sender entries. |
| >100 message selection | Experimental / version-dependent | Targets the classic `ChattingDataAdapterV3` implementation; newer WeChat builds may replace it, which will be shown as a failed compatibility hook rather than crashing. |
| GitHub Release CI | Active | Successful `main` builds create/update `v<version>` and publish APK + SHA-256. |

## Planned after 0.7 testing

- MP3 conversion for saved voice (requires bringing in a tested SILK/PCM/MP3 native pipeline)
- True per-sender notification avatars for group notifications
- Chat toolbar
- Swipe-to-quote / optional repeat/edit actions
- Optional native / Zygisk-only features only when a feature genuinely needs them

## Design note

The message-row features share one DexKit-resolved `MessageViewApi`. Expensive resolvers run on one MiniWx background queue, and each feature remains isolated so an obsolete WeChat hook does not stop unrelated enhancements.
