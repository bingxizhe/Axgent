plugins {
    id("com.android.library")
    id("kotlin-android")
    id("kotlin-kapt")
}
java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(versions.javaVersionInt))
    }
}
android {
    namespace = "com.aiselp.autojs.codeeditor"
    compileSdk = versions.compile

    defaultConfig {
        minSdk = versions.mini

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }
    buildFeatures {
        compose = true
        viewBinding = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = compose_version
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
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.androidx.constraintlayout)
    implementation(libs.kotlinx.coroutines.android)
    api(libs.androidx.webkit)
    implementation(libs.google.gson)
    implementation(libs.core.ktx)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.appcompat)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(project(":autojs"))

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.espresso.core)
}

// Axgent 不提供脚本编辑器入口（agent 变体已从清单移除 EditActivity），
// 仅保留模块供 AutoX 主界面代码编译，不再下载/打包 vscode-mobile 的 dist.zip（约 20.8MB）。