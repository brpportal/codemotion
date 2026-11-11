plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.brpportal.codemotion"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.brpportal.codemotion"
        minSdk = 26
        targetSdk = 34
        versionCode = 3
        versionName = "2.00"
    }

    buildTypes {
        release {
            // Chhota size: minify + resource shrink (WhatsApp delivery ke liye <5MB target)
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Test build hai — debug key se sign karo taake seedha install ho jaye
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
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.09.00"))
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.compose.material3:material3")
}
