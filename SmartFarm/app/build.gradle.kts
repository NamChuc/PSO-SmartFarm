plugins {
    id("com.android.application")
    id("com.google.gms.google-services")
}

android {
    namespace = "com.example.smartfarm"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.smartfarm"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

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
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}

dependencies {
    implementation(platform("com.google.firebase:firebase-bom:33.10.0"))
    implementation("com.google.firebase:firebase-analytics")
    implementation("com.google.firebase:firebase-database")
    implementation("androidx.appcompat:appcompat:1.6.1") // Sửa lỗi thư viện appcompat
    implementation("com.google.android.material:material:1.9.0") // Sửa lỗi thư viện Material
    implementation("androidx.activity:activity:1.9.0") // Giảm phiên bản để tương thích compileSdk 34
    implementation("androidx.constraintlayout:constraintlayout:2.1.4") // Bổ sung ConstraintLayout
    implementation ("com.github.anastr:speedviewlib:1.6.0")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}