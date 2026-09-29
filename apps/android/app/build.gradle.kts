plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "uk.lunarlab.health2609"
    compileSdk = 37

    defaultConfig {
        applicationId = "uk.lunarlab.health2609"
        minSdk = 26
        targetSdk = 36
        versionCode = 4
        versionName = "0.2.2"

        val apiBaseUrl = providers.gradleProperty("HEALTH2609_API_BASE_URL")
            .orNull
            ?: "https://h2609.lunarlab.uk/"
        buildConfigField("String", "API_BASE_URL", "\"$apiBaseUrl\"")
    }

    signingConfigs {
        val keystorePath = providers.gradleProperty("HEALTH2609_KEYSTORE_PATH").orNull
            ?: System.getenv("HEALTH2609_KEYSTORE_PATH")
        val keystorePassword = providers.gradleProperty("HEALTH2609_KEYSTORE_PASSWORD").orNull
            ?: System.getenv("HEALTH2609_KEYSTORE_PASSWORD")
        val keyAliasValue = providers.gradleProperty("HEALTH2609_KEY_ALIAS").orNull
            ?: System.getenv("HEALTH2609_KEY_ALIAS")
        val keyPasswordValue = providers.gradleProperty("HEALTH2609_KEY_PASSWORD").orNull
            ?: System.getenv("HEALTH2609_KEY_PASSWORD")

        if (
            keystorePath != null &&
            keystorePassword != null &&
            keyAliasValue != null &&
            keyPasswordValue != null
        ) {
            create("release") {
                storeFile = file(keystorePath)
                storePassword = keystorePassword
                keyAlias = keyAliasValue
                keyPassword = keyPasswordValue
            }
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}


dependencies {
    val composeBom = platform("androidx.compose:compose-bom-alpha:2026.09.00")
    implementation(composeBom)

    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.4")
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.datastore:datastore-preferences:1.2.1")
    implementation("androidx.health.connect:connect-client:1.1.0")

    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
