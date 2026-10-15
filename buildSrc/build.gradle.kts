plugins { java }
repositories { google(); mavenCentral() }
dependencies {
    // Supplies the pinned Android plugin and its instrumentation API in one classloader.
    implementation("com.android.tools.build:gradle:9.4.1")
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:2.3.20")
    implementation("org.ow2.asm:asm:9.9.1")
    testImplementation("junit:junit:4.13.2")
}
java { toolchain.languageVersion.set(JavaLanguageVersion.of(21)) }
