import com.android.build.gradle.internal.tasks.factory.dependsOn
import java.io.FileNotFoundException
import java.util.Base64

plugins {
    id("com.android.application")
    id("kotlin-android")
    id("com.jakewharton.butterknife")
    id("kotlin-kapt")//Deprecated!!
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(versions.javaVersionInt))
    }
}

// 把 Axgent 运行时脚本（Axgent/src）打进 APK：assets/agent-src/**。
// 排除 config/local.js，避免把本机 API Key 打进安装包；模型配置走 App 的 ui-config.json。
// rootProject.projectDir 是 Axgent/android，上一层才是 Axgent；多退一级会落到 workspace 的旧副本。
val axgentRoot = rootProject.projectDir.parentFile
val agentSrcAssetsDir = layout.buildDirectory.dir("generated/agentSrcAssets").get().asFile
val copyAgentSrcAssets by tasks.registering(Copy::class) {
    from(axgentRoot.resolve("src")) {
        exclude("config/local.js")
    }
    into(agentSrcAssetsDir.resolve("agent-src"))
}
tasks.matching { it.name.startsWith("merge") && it.name.endsWith("Assets") }.configureEach {
    dependsOn(copyAgentSrcAssets)
}

android {
    namespace = "org.autojs.autoxjs"
    compileSdk = versions.compile
    defaultConfig {
        applicationId = "org.autojs.autoxjs"
        minSdk = versions.mini
        targetSdk = versions.target
        versionCode = versions.appVersionCode
        versionName = versions.appVersionName
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
//        multiDexEnabled = true
        buildConfigField("boolean", "isMarket", "false")

        resourceConfigurations.addAll(
            listOf("zh-rCN", "en", "es", "ar", "ja", "zh_TW", "fr", "de", "it", "ko", "ru", "tr", "lt")
        )
    }
    buildFeatures {
        compose = true
        viewBinding = true
        buildConfig = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = compose_version
    }
    lint {
        abortOnError = false
        disable.addAll(listOf("MissingTranslation", "ExtraTranslation"))
    }

    splits {
        // Configures multiple APKs based on ABI.
        abi {
            // Enables building multiple APKs per ABI.
            isEnable = true
            // By default all ABIs are included, so use reset() and include to specify that we only
            // want APKs for x86 and x86_64.
            // Resets the list of ABIs that Gradle should create APKs for to none.
            reset()
            // Specifies a list of ABIs that Gradle should create APKs for.
            include("arm64-v8a")
            // Specifies that we do not want to also generate a universal APK that includes all ABIs.
            isUniversalApk = false
        }
    }
    val signing =
        if (System.getenv("CI") == "true" && !System.getenv("KEYSTORE_BASE64").isNullOrEmpty()) {
            val file = File.createTempFile("key", "jks")
            val bytes = Base64.getDecoder().decode(System.getenv("KEYSTORE_BASE64"))
            file.writeBytes(bytes)
            signingConfigs.create("release") {
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
                storeFile = file
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        } else null

    buildTypes {
        named("debug") {
            // 与已安装的 AutoX 并排安装，避免覆盖/签名冲突
            applicationIdSuffix = ".agentdebug"
            isShrinkResources = false
            isMinifyEnabled = false
            setProguardFiles(
                listOf(
                    getDefaultProguardFile("proguard-android.txt"), "proguard-rules.pro"
                )
            )
        }
        named("release") {
            if (signing != null) {
                signingConfig = signing
            }
            isShrinkResources = false
            isMinifyEnabled = false
            setProguardFiles(
                listOf(
                    getDefaultProguardFile("proguard-android.txt"), "proguard-rules.pro"
                )
            )
        }
        create("prod") {
            initWith(getByName("debug"))
            // 正式产品变体不追加 .agentdebug；仅使用本地 debug 签名（用户要求暂不做正式签名）。
            applicationIdSuffix = ""
            // 暂不做正式发布签名；保持 debuggable，避免 AutoX 运行时在非 debuggable 模式下异常退出。
            isDebuggable = true
            signingConfig = signingConfigs.getByName("debug")
            isShrinkResources = false
            isMinifyEnabled = false
            matchingFallbacks.addAll(listOf("debug", "release"))
        }
    }

    flavorDimensions.add("channel")
    productFlavors {
        create("common") {
            applicationIdSuffix = ".common"
            versionCode = versions.appVersionCode
            versionName = versions.appVersionName
            buildConfigField("String", "CHANNEL", "\"common\"")
            manifestPlaceholders.putAll(mapOf("appName" to "@string/app_name"))
        }
        create("v7") {
            applicationIdSuffix = ".v7"
            versionCode = versions.devVersionCode
            versionName = versions.devVersionName
            buildConfigField("String", "CHANNEL", "\"v7\"")
            manifestPlaceholders.putAll(mapOf("appName" to "Autox.js v7"))
        }
        create("v7_mini") {
            applicationIdSuffix = ".v7"
            buildConfigField("String", "CHANNEL", "\"v7\"")
            manifestPlaceholders.putAll(mapOf("appName" to "Autox.js v7"))
        }
        create("agent") {
            // Axgent 正式包名：反向域名 com.axgent.agent，prod buildType 不追加后缀。
            applicationId = "com.axgent.agent"
            versionCode = versions.devVersionCode
            versionName = versions.devVersionName
            buildConfigField("String", "CHANNEL", "\"agent\"")
            manifestPlaceholders.putAll(mapOf("appName" to "Axgent"))
            matchingFallbacks.addAll(listOf("v7", "common"))
        }
    }
    sourceSets {
        getByName("main") {
            res.srcDirs("src/main/res", "src/main/res-i18n")
            assets.srcDir(agentSrcAssetsDir)
            aidl {
                srcDirs("src/main/aidl", "src/main/java")
            }
        }
    }
    configurations.all {
        resolutionStrategy.force("com.google.code.findbugs:jsr305:3.0.1")
        exclude(group = "org.jetbrains", module = "annotations-java5")
    }

    packaging {
        //ktor cio implementation("io.ktor:ktor-server-cio:2.0.3")
        resources.pickFirsts.add("META-INF/INDEX.LIST")
        jniLibs {
            // Axgent 只运行 .js（Rhino），不提供 Node 引擎（.mjs）；排除约 86MB 的 Javet Node 原生库。
            excludes += "**/libjavet-node-android*.so"
        }
    }

}

dependencies {

    implementation(platform(libs.compose.bom))
    // Deprecated!!
    implementation("androidx.localbroadcastmanager:localbroadcastmanager:1.1.0")
    implementation(libs.androidx.swiperefreshlayout)
    implementation(libs.androidx.webkit)

    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    androidTestImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.tooling)
    implementation(libs.activity.compose)

    androidTestImplementation(libs.espresso.core)
    testImplementation(libs.junit)
    // Kotlin携程
    implementation(libs.kotlinx.coroutines.android)
    // ButterKnife Deprecated!!
    implementation("com.jakewharton:butterknife:10.2.1")
    kapt("com.jakewharton:butterknife-compiler:10.2.3")
    // Android supports
    implementation(libs.preference.ktx)
    implementation(libs.appcompat) //

    implementation(libs.compose.material3)
    implementation(libs.compose.material3.window.size)
    implementation(libs.compose.material3.adaptive.navigation.suite)
    // Personal libraries  Deprecated!!
    implementation("com.github.hyb1996:MutableTheme:1.0.0")
    implementation(libs.material)
    // Material Dialogs  Deprecated!!
    implementation("com.afollestad.material-dialogs:core:0.9.2.3")
    // Common Markdown
    implementation(libs.commonmark)
    implementation(libs.commonmark.ext.heading.anchor)
    //MultiLevelListView
    implementation("com.github.hyb1996:android-multi-level-listview:1.1")
    //Licenses Dialog  Deprecated!!
    implementation("de.psdev.licensesdialog:licensesdialog:2.2.0")
    //Expandable RecyclerView
    implementation("com.bignerdranch.android:expandablerecyclerview:3.0.0-RC1")
    //FlexibleDivider
    implementation("com.yqritc:recyclerview-flexibledivider:1.4.0")

    implementation(libs.rxjava3.rxandroid)
    // Retrofit
    implementation(libs.retrofit2.retrofit)
    implementation(libs.retrofit2.converter.gson)
    debugImplementation(libs.leakcanary.android)
    // Tasker Plugin
    implementation("com.twofortyfouram:android-plugin-client-sdk-for-locale:4.0.3")
    // MaterialDialogCommon
    implementation("com.afollestad.material-dialogs:commons:0.9.2.3")
    // WorkManager
    implementation(libs.androidx.work)
    // Optional, if you use support library fragments:
    implementation(project(":autojs"))
    implementation(project(":codeeditor"))
    // FileObservable 等工具类直接使用 commons-io；原先由 :apkbuilder 传递引入
    implementation(libs.commons.io)

    // ViewModel
    implementation(libs.lifecycle.viewmodel.ktx)
    // ViewModel utilities for Compose
    implementation(libs.lifecycle.viewmodel.compose)
    // Lifecycles only (without ViewModel or LiveData)
    implementation(libs.lifecycle.runtime.ktx)
    // Saved state module for ViewModel
    implementation(libs.lifecycle.viewmodel.savedstate)
    implementation(libs.lifecycle.service)

    implementation(libs.androidx.savedstate.ktx)
    implementation(libs.androidx.savedstate)

    implementation(libs.bundles.ktor)
    //TextView markdown
    implementation(libs.markwon)
    implementation(libs.androidx.viewpager2)
    implementation(libs.coil)
    implementation(libs.coil.compose)
    implementation(libs.coil.gif)
}

tasks.register("buildDocs") {
    group = "build"
    doLast {
        val v2DocDir = File(rootProject.projectDir, "docs/v2")
        val jsApiDir = File(rootProject.projectDir, "autojs/src/main/js/v7-api")
        if (!v2DocDir.isDirectory) {
            logger.error("run command: `git submodule update --init --recursive` install docs/v2")
            throw FileNotFoundException("${v2DocDir.path} not found")
        }
        val buildFile = File.createTempFile("buildJs", ".mjs")
        exec {
            workingDir(jsApiDir)
            buildFile.writeText(
                """
                import { execSync } from 'child_process'
                execSync('npm install', { stdio: 'inherit' })
                execSync('npm run docs', { stdio: 'inherit' })
            """.trimIndent()
            )
            execCommand("node " + buildFile.path)
        }
        copy {
            from(File(jsApiDir, "docs"))
            delete(File(v2DocDir, "docs/nodejs/modules"))
            into(File(v2DocDir, "docs/nodejs/modules"))
        }
        exec {
            workingDir(v2DocDir)
            buildFile.writeText(
                """
                import { execSync } from 'child_process'
                execSync('npm install', { stdio: 'inherit' })
                execSync('npm run build', { stdio: 'inherit' })
            """.trimIndent()
            )
            execCommand("node " + buildFile.path)
        }
        copy {
            from(File(v2DocDir, "build"))
            into(File(projectDir, "src/main/assets/docs/v2"))
        }
        buildFile.delete()
    }
}