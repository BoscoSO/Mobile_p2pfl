import com.google.protobuf.gradle.id

plugins {
    id("com.google.protobuf") version "0.9.4"
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.kotlin.android)

}
//Grpc plugin
protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:4.26.0"
    }
    plugins {
        id("grpc") {
            artifact = "io.grpc:protoc-gen-grpc-java:1.43.0"
        }
    }
    generateProtoTasks {
        all().forEach { task ->
            task.builtins {
                create("java") {
                    option("lite")
                }
            }
            task.plugins {
                create("grpc") {
                    option("lite")
                }
            }
//
//            task.generateDescriptorSet = true
//            task.descriptorSetOptions.includeImports = true
//            task.descriptorSetOptions.path =
//                "$rootDir/app/src/main/proto/app.desc"
        }
    }


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
        // filtro de arquitectura
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
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        viewBinding = true
      //  mlModelBinding = true

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
    implementation(libs.androidx.espresso.remote)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    //tflite
    implementation(libs.tensorflow.lite)

    implementation(libs.tensorflow.lite.gpu)
    implementation(libs.tensorflow.lite.support)

    //implementation(libs.tensorflow.lite.metadata)
    //nueva
    implementation(libs.tensorflow.lite.select.tf.ops)// 2.9.0 version
    //implementation("org.tensorflow:tensorflow-lite-select-tf-ops:2.16.1")

    //implementation(files("libs/tensorflow-lite-select-tf-ops-2.16.1.aar"))

    //paintdrawing
    implementation(libs.finger.paint.view)


    //GRPC
    implementation(libs.grpc.okhttp)
    implementation(libs.grpc.protobuf.lite)
    implementation(libs.grpc.stub)
    implementation(libs.annotation.javax.annotation.api)
    implementation(libs.protobuf.javalite)
    //implementation("com.google.protobuf:protobuf-kotlin:4.27.4")

   // implementation(libs.protobuf.java)
    implementation(libs.grpc.android)
    implementation(libs.protobuf.gradle.plugin)


    //test




}

tasks.withType<com.google.protobuf.gradle.GenerateProtoTask> {

}