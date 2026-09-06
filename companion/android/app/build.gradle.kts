plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.ktlint)
}

// A release task without its signing material fails here rather than falling
// back to the debug key.
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

    lint {
        warningsAsErrors = true
        // Fires on the targetSdk above, which is held back on purpose.
        disable += "OldTargetApi"
        // Reaches for the network on every run.
        disable += "NewerVersionAvailable"
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

ktlint {
    version.set(libs.versions.ktlintEngine.get())
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.material3)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.glance.appwidget)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.json)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)
    ktlintRuleset(libs.ktlint.compose.rules)
}
