import com.android.build.api.instrumentation.FramesComputationMode
import com.android.build.api.instrumentation.InstrumentationScope
import my.fuelledger.build.OfflineMlKitTelemetry

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
}

val ledgerAppName = "Fuel & Expense Tracker"
val ledgerVersionName = "1.3.0"

android {
    namespace = "my.fuelledger"
    compileSdk = 37
    defaultConfig {
        applicationId = "my.fuelledger"
        minSdk = 26
        targetSdk = 37
        versionCode = 5
        versionName = ledgerVersionName
        resValue("string", "app_name", ledgerAppName)
        resValue("string", "app_version", "v$ledgerVersionName")
        resValue("string", "app_display_name", "$ledgerAppName v$ledgerVersionName")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildFeatures {
        compose = true
        resValues = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.all { test ->
            test.systemProperty("robolectric.dependency.repo.url", "https://repo.maven.apache.org/maven2")
            // Robolectric needs a writable cache/lock directory in sandboxed cloud machines.
            val testHome = layout.buildDirectory.dir("test-home").get().asFile
            test.systemProperty("user.home", testHome.absolutePath)
            test.doFirst { testHome.mkdirs() }
            listOf("https.proxyHost", "https.proxyPort", "http.proxyHost", "http.proxyPort", "http.nonProxyHosts", "https.nonProxyHosts").forEach { key ->
                System.getProperty(key)?.let { value -> test.systemProperty(key, value) }
            }
        }
    }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    sourceSets.getByName("androidTest").assets.srcDir("schemas")
}
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
ksp { arg("room.schemaLocation", "$projectDir/schemas") }
// Bundled OCR includes Firelog transports by default. The reviewed sinks are compiled
// to no-ops, and the network/scheduling implementations are excluded from every APK.
configurations.configureEach {
    exclude(group = "com.google.android.datatransport", module = "transport-runtime")
    exclude(group = "com.google.android.datatransport", module = "transport-backend-cct")
}
androidComponents {
    onVariants { variant ->
        variant.instrumentation.transformClassesWith(OfflineMlKitTelemetry::class.java, InstrumentationScope.ALL) {}
        variant.instrumentation.setAsmFramesComputationMode(FramesComputationMode.COMPUTE_FRAMES_FOR_INSTRUMENTED_METHODS)
    }
}
val verifyOfflineOcrPolicy by tasks.registering {
    doLast {
        OfflineMlKitTelemetry.verifyAars(configurations.getByName("debugRuntimeClasspath").files)
    }
}
tasks.named("preBuild") { dependsOn(verifyOfflineOcrPolicy) }
dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.09.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended:1.7.8")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
    implementation("androidx.room:room-runtime:2.8.5")
    implementation("androidx.room:room-ktx:2.8.5")
    ksp("androidx.room:room-compiler:2.8.5")
    implementation("androidx.datastore:datastore-preferences:1.2.1")
    implementation("androidx.exifinterface:exifinterface:1.4.2")
    implementation("net.zetetic:sqlcipher-android:4.19.1")
    implementation("androidx.sqlite:sqlite:2.6.2")
    implementation("com.google.crypto.tink:tink-android:1.23.0")
    implementation("androidx.biometric:biometric:1.1.0")
    implementation("androidx.fragment:fragment-ktx:1.9.1")
    implementation("androidx.work:work-runtime-ktx:2.12.0")
    // Bundles the Latin recognition model for offline receipt scanning.
    implementation("com.google.mlkit:text-recognition:16.0.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    testImplementation(composeBom)
    testImplementation("androidx.compose.ui:ui-test-junit4")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.17")
    testImplementation("androidx.test:core:1.7.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test:core:1.7.0")
}
