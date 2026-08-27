# zygisk-loader (reserved)

This directory is intentionally **not** built or loaded in MiniWx 0.3.

Future role:

- optional Zygisk entry/loader;
- load `native-core` only when an advanced feature needs early/native access;
- keep ordinary LSPosed features fully usable without Zygisk.

MiniWx should not inject every WeChat subprocess by default. Process selection
stays centralized in `ProcessPolicy` and should be expanded only for a feature
with a concrete subprocess requirement.
