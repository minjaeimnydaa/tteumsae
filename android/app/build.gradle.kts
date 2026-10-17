import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("org.jetbrains.kotlin.kapt")
}

// Location capability is part of the APK, never an account preference or a remote flag.
// A reviewed automatic-location release can be built explicitly with -PlocationMode=automatic.
val locationMode = providers.gradleProperty("locationMode").orElse("manual").get()
if (locationMode !in setOf("manual", "automatic")) {
    throw GradleException("Unknown locationMode '$locationMode'. Use manual (default) or automatic.")
}
val automaticLocationEnabled = locationMode == "automatic"
val locationSourceSet = if (automaticLocationEnabled) "locationAutomatic" else "locationManual"

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) {
        file.inputStream().use(::load)
    }
}
val kakaoMapNativeAppKey =
    localProperties.getProperty("KAKAO_MAP_NATIVE_APP_KEY", "")
val supabaseUrl =
    localProperties.getProperty("SUPABASE_URL", "").trim()
val supabasePublishableKey =
    localProperties.getProperty("SUPABASE_PUBLISHABLE_KEY", "").trim()
val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) {
        file.inputStream().use(::load)
    }
}

fun releaseSigningValue(propertyName: String, environmentName: String): String =
    System.getenv(environmentName)?.trim()?.takeIf(String::isNotEmpty)
        ?: keystoreProperties.getProperty(propertyName, "").trim()

val releaseStoreFile = releaseSigningValue("storeFile", "TTEUMSAE_RELEASE_STORE_FILE")
val releaseStorePassword = releaseSigningValue("storePassword", "TTEUMSAE_RELEASE_STORE_PASSWORD")
val releaseKeyAlias = releaseSigningValue("keyAlias", "TTEUMSAE_RELEASE_KEY_ALIAS")
val releaseKeyPassword = releaseSigningValue("keyPassword", "TTEUMSAE_RELEASE_KEY_PASSWORD")
val hasReleaseSigningConfig = listOf(
    releaseStoreFile,
    releaseStorePassword,
    releaseKeyAlias,
    releaseKeyPassword,
).all(String::isNotEmpty)
val allowUnsignedRelease = providers.gradleProperty("allowUnsignedRelease").orNull == "true"
val releaseArtifactRequested = gradle.startParameter.taskNames.any { taskName ->
    taskName.substringAfterLast(':').lowercase() in setOf("bundlerelease", "assemblerelease")
}

if (releaseArtifactRequested && !hasReleaseSigningConfig && !allowUnsignedRelease) {
    throw GradleException(
        "Release signing is not configured. Copy keystore.properties.example to " +
            "keystore.properties and fill it, or provide the TTEUMSAE_RELEASE_* environment variables. " +
            "Use -PallowUnsignedRelease=true only for a local compile check.",
    )
}

fun String.asBuildConfigString(): String =
    "\"${replace("\\", "\\\\").replace("\"", "\\\"")}\""

android {
    namespace = "com.tteumsae.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.tteumsae.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 35
        versionName = "0.14.0"
        buildConfigField("boolean", "AUTOMATIC_LOCATION_ENABLED", automaticLocationEnabled.toString())
        buildConfigField(
            "String",
            "API_BASE_URL",
            "\"https://tteumsae-backend-one.vercel.app\"",
        )
        buildConfigField(
            "String",
            "KAKAO_MAP_NATIVE_APP_KEY",
            kakaoMapNativeAppKey.asBuildConfigString(),
        )
        buildConfigField(
            "String",
            "SUPABASE_URL",
            supabaseUrl.asBuildConfigString(),
        )
        buildConfigField(
            "String",
            "SUPABASE_PUBLISHABLE_KEY",
            supabasePublishableKey.asBuildConfigString(),
        )
        buildConfigField(
            "boolean",
            "AUTH_ENABLED",
            (supabaseUrl.isNotBlank() && supabasePublishableKey.isNotBlank()).toString(),
        )

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
    }

    signingConfigs {
        if (hasReleaseSigningConfig) {
            create("release") {
                storeFile = rootProject.file(releaseStoreFile)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release")
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    sourceSets {
        getByName("main") {
            java.srcDir("src/$locationSourceSet/java")
        }
        // Overlay the shared manifest in both variants. The manual overlay also removes
        // any location permission introduced by a library's manifest.
        getByName("debug") {
            manifest.srcFile("src/$locationSourceSet/AndroidManifest.xml")
        }
        getByName("release") {
            manifest.srcFile("src/$locationSourceSet/AndroidManifest.xml")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

kapt {
    arguments {
        arg("room.schemaLocation", "$projectDir/schemas")
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("com.kakao.maps.open:android:2.14.0")
    implementation("androidx.room:room-runtime:2.8.4")
    implementation("androidx.room:room-ktx:2.8.4")
    kapt("androidx.room:room-compiler:2.8.4")
    implementation(platform("io.github.jan-tennert.supabase:bom:3.5.0"))
    implementation("io.github.jan-tennert.supabase:auth-kt")
    implementation("io.github.jan-tennert.supabase:postgrest-kt")
    implementation("io.ktor:ktor-client-android:3.0.3")

    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    testImplementation("org.json:json:20260814")
    androidTestImplementation("androidx.room:room-testing:2.8.4")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
}
