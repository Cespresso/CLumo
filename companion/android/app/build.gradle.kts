plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

// release ビルドは必ず専用鍵で署名する。署名材料が欠けたまま
// release タスクを要求されたら debug 署名に落とさず失敗させる。
val releaseSigningNames = listOf(
    "CLUMO_KEYSTORE_PATH",
    "CLUMO_KEYSTORE_PASSWORD",
    "CLUMO_KEY_ALIAS",
    "CLUMO_KEY_PASSWORD",
)
val releaseSigning = releaseSigningNames.associateWith(System::getenv)
val releaseRequested = gradle.startParameter.taskNames.any {
    it.contains("release", ignoreCase = true)
}
if (releaseRequested) {
    val missing = releaseSigning.filterValues { it.isNullOrBlank() }.keys
    require(missing.isEmpty()) {
        "Missing release signing environment variables: ${missing.joinToString()}"
    }
}

android {
    namespace = "io.github.cespresso.clumo"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.cespresso.clumo"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    signingConfigs {
        if (releaseSigning.values.all { !it.isNullOrBlank() }) {
            create("release") {
                storeFile = file(requireNotNull(releaseSigning["CLUMO_KEYSTORE_PATH"]))
                storePassword = releaseSigning["CLUMO_KEYSTORE_PASSWORD"]
                keyAlias = releaseSigning["CLUMO_KEY_ALIAS"]
                keyPassword = releaseSigning["CLUMO_KEY_PASSWORD"]
            }
        }
    }

    buildTypes {
        getByName("debug") {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        getByName("release") {
            signingConfig = signingConfigs.findByName("release")
        }
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.08.00"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.core:core-ktx:1.19.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
    implementation("androidx.datastore:datastore-preferences:1.2.1")
    implementation("androidx.glance:glance-appwidget:1.2.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
    // Android's org.json classes are method stubs in local JVM tests.
    testImplementation("org.json:json:20180813")
    androidTestImplementation(platform("androidx.compose:compose-bom:2026.08.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
