plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.hazbu.xpoint"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.hazbu.xpoint"
        minSdk = 26
        targetSdk = 36
        versionCode = project.findProperty("APP_VERSION_CODE")?.toString()?.toInt() ?: System.getenv("APP_VERSION_CODE")?.toInt() ?: 1
        versionName = project.findProperty("APP_VERSION_NAME")?.toString() ?: System.getenv("APP_VERSION_NAME") ?: "2.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    compileOnly(libs.libxposed.api)
    implementation(libs.libxposed.service)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)
    implementation(libs.osmdroid.android)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}
