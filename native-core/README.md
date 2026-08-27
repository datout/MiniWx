# native-core (reserved)

This directory is intentionally **not** included in the Gradle build yet.

Future role:

- common JNI/native APIs shared by advanced features;
- native hook implementation only for features that actually require it;
- no dependency from normal Java/LSPosed features.

The public Java-side facade is currently `dev.miniwx.backend.NativeBackend`.
When native support is added, keep that facade stable and implement the backend
behind it.
