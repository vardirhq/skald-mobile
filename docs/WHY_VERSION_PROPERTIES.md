# Why version.properties?

Android needs both a human version (`versionName`) and an integer upgrade ordering (`versionCode`). Keeping those in a tiny dedicated properties file gives release automation one canonical place to update while letting Gradle consume the values directly. It avoids parsing or rewriting Kotlin build scripts merely to cut a release.
