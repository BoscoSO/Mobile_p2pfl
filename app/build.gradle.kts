plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.kotlin.android)


}

android {
    namespace = "com.example.mobile_p2pfl"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.mobile_p2pfl"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        /*
        ndk {
            abiFilters += listOf("armeabi-v7a", "arm64-v8a")
        }*/
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
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures {
        viewBinding = true

    }

    androidResources {
        noCompress += listOf("tflite", "lite")
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)
    implementation(libs.androidx.lifecycle.livedata.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.camera.view)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    //tflite
    implementation(libs.tensorflow.lite)

    implementation(libs.tensorflow.lite.gpu)
    implementation(libs.tensorflow.lite.support)

    //paintdrawing
    implementation(libs.finger.paint.view)

    //GRPC
    implementation(libs.grpc.stub)
    implementation(libs.grpc.grpc.protobuf)
    implementation(libs.grpc.okhttp)
    implementation(libs.protoc.gen.grpc.kotlin)
    implementation(libs.grpc.kotlin.stub)
    implementation(libs.protobuf.kotlin)

}