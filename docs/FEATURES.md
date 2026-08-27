# Feature status

## Active in 0.4.0

| Feature | Status | Notes |
| --- | --- | --- |
| Module settings screen | Active | Plain Android UI; no extra UI framework dependency. |
| Runtime injection status | Active | WeChat reports a heartbeat after `Application.attach`. |
| Detected WeChat version | Active | Reported from the host package. |
| Anti-recall | Experimental | DexKit resolves the XML parser and suppresses local `revokemsg` handling. |
| Recall notice | Experimental | Shows the original recall replacement text as a local Toast after blocking. |
| Own recall normal | Experimental | Uses conservative replacement-text matching in 0.4; database-backed detection is planned. |

## Registered, not yet hooked

The following settings are intentionally visible but disabled until their real, version-specific hooks are ported:

- Copy wxid
- Detailed message time
- Voice enhancements
- Image enhancements
- Notification enhancements
- Group-chat enhancements

This avoids presenting a switch as working when it has no effect.
