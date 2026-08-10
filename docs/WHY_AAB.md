# Why publish both APK and AAB?

The APK is the useful artifact for direct GitHub installs and testing real release builds. The AAB is the store-oriented artifact expected by Google Play and similar distribution pipelines. Building both in the same release job proves they come from the same source version and signing configuration, without forcing a future store release to invent a second packaging path.
