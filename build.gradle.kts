plugins {
    // Version 9.4.1 is pinned in buildSrc for the offline ML Kit instrumentation.
    id("com.android.application") apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.20" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.3.20" apply false
    id("com.google.devtools.ksp") version "2.3.12" apply false
}
