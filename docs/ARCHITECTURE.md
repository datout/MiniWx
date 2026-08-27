# MiniWx architecture

MiniWx uses a layered design so ordinary enhancements do not depend on a native
or Zygisk stack.

```text
MiniWx app / settings
        |
        +-- LSPosed Java core (default)
        |     +-- HookManager
        |     +-- DexKit resolver
        |     +-- MessageViewApi (shared row bind service)
        |     +-- feature hooks
        |
        +-- NativeBackend facade
              |
              +-- native-core       (future, optional)
              +-- zygisk-loader     (future, optional)
```

## Runtime capabilities

Each `HookItem` declares the capabilities it needs through
`requiredCapabilities()`.

Current capabilities:

- `JAVA_HOOK` - available in 0.7.1;
- `NATIVE_HOOK` - reserved;
- `ZYGISK` - reserved;
- `SUB_PROCESS` - reserved for features that intentionally run outside the main
  WeChat process.

If a feature asks for a capability that is not present, `HookManager` skips it
instead of allowing it to break the rest of MiniWx.

## Process policy

MiniWx 0.7.1 still loads only `com.tencent.mm`'s main process. The decision is now
centralized in `ProcessPolicy`, so a future feature can add a narrowly scoped
subprocess requirement without rewriting the Xposed entry or enabling all
WeChat processes globally.

## Native/Zygisk policy

Native and Zygisk support should remain optional. A normal feature should use
LSPosed/DexKit whenever that is sufficient. Only features with a concrete native
or early-process requirement should depend on the optional backend.
