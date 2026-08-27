# Feature status

## Active in 0.6.0

| Feature | Status | Notes |
| --- | --- | --- |
| Module settings screen | Active | Plain Android UI; no extra UI framework dependency. |
| Runtime injection status | Active | WeChat reports a heartbeat after `Application.attach`. |
| Detected WeChat version | Active | Reported from the host package. |
| Anti-recall | Experimental | DexKit resolves the XML parser and suppresses local `revokemsg` handling. |
| Recall notice | Experimental | Shows the original recall replacement text as a local Toast after blocking. |
| Own recall normal | Experimental | Conservative text matching; DB-backed sender detection is still planned. |
| Detailed message time | Experimental | Uses the Mvvm chat row bind hook and `timeTV` to show `yyyy/MM/dd HH:mm:ss`. |
| Copy wxid | Experimental | Incoming sender wxid is shown on the time line; long-press it to copy. |
| Group sender ID | Experimental | Incoming group rows append the sender wxid beside the displayed member name. |
| Group role badges | Experimental | Uses chatroom data to distinguish owner/admin/member; regular-member badge is optional. |
| Image enhancement | Experimental | Gallery hook auto-clicks visible “查看原图 / 查看原视频” controls. |
| Auto voice-to-text | Experimental | Calls WeChat's own TransformComponent for incoming type-34 voice messages and clears the local unplayed marker through VoiceLogic. |
| MessagingStyle notifications | Experimental | Rebuilds eligible WeChat message notifications with Android MessagingStyle. |
| Notification merge | Experimental | Uses a stable ID per conversation and translates WeChat's original cancel ID. |
| GitHub Release CI | Active | A successful push to `main` creates/updates `v<version>` and publishes the APK + SHA-256 under Releases. |

## Planned

- Voice save/export (requires a stable current-WeChat media path/storage resolver)
- Notification sender avatars
- DB-backed own-recall detection
- More group-chat tools and message actions
- Optional native / Zygisk-only features when a feature genuinely needs them

## Design note

The message-row features share one DexKit-resolved `MessageViewApi`, so row-level enhancements do not resolve the same WeChat method repeatedly. New feature hooks stay isolated: one failed resolver should not stop the rest of MiniWx from loading.
