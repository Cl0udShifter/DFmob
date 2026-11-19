import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val localProperties = Properties().apply {
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        localPropertiesFile.inputStream().use { load(it) }
    }
}
val cartoApiKey: String = localProperties.getProperty("CARTO_API_KEY")
    ?: System.getenv("CARTO_API_KEY")
    ?: ""
val stadiaApiKey: String = localProperties.getProperty("STADIA_API_KEY")
    ?: System.getenv("STADIA_API_KEY")
    ?: "d09566e4-2f4d-4a5c-8b32-adb43a4f44ad"
val moovitApiKey: String = localProperties.getProperty("MOOVIT_API_KEY")
    ?: System.getenv("MOOVIT_API_KEY")
    ?: ""
val moovitUserKey: String = localProperties.getProperty("MOOVIT_USER_KEY")
    ?: System.getenv("MOOVIT_USER_KEY")
    ?: ""

android {
    namespace = "com.dfmob"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.dfmob"
        minSdk = 26
        targetSdk = 34
        versionCode = 42
        versionName = "1.4.12"

        buildConfigField("String", "CARTO_API_KEY", "\"$cartoApiKey\"")
        buildConfigField("String", "STADIA_API_KEY", "\"$stadiaApiKey\"")
        buildConfigField("String", "MOOVIT_API_KEY", "\"$moovitApiKey\"")
        buildConfigField("String", "MOOVIT_USER_KEY", "\"$moovitUserKey\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        getByName("debug") {
            // Chave debug padrao para instalacao direta de builds
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            applicationIdSuffix = ""
            signingConfig = signingConfigs.getByName("debug")
        }
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

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.11"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.02.02")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.2")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.0")
    implementation("org.osmdroid:osmdroid-android:6.1.18")
    implementation("androidx.work:work-runtime-ktx:2.9.0")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
