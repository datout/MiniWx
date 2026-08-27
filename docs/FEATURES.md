# Feature status

## Active in 0.5.0

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
| Group-chat enhancement | Experimental | Incoming group rows append the sender wxid next to the displayed member name. |
| Image enhancement | Experimental | Gallery hook auto-clicks visible “查看原图 / 查看原视频” controls. |

## Next batch

These are visible but disabled until their real WeChat hooks are connected:

- Voice enhancements (auto speech-to-text / playback / save will be split into sub-features)
- Notification enhancements (MessagingStyle / grouping / cleanup)

## Design note

The message-row features share one DexKit-resolved `MessageViewApi`, so adding more row-level enhancements does not require resolving the same WeChat method repeatedly.
