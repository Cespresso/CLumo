plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
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
    compileSdk = 34

    defaultConfig {
        applicationId = "io.github.cespresso.clumo"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
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
        getByName("release") {
            signingConfig = signingConfigs.findByName("release")
        }
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.0")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    testImplementation("junit:junit:4.13.2")
    // Android's org.json classes are method stubs in local JVM tests.
    testImplementation("org.json:json:20180813")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
