plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.johnny.wxscript"
    compileSdk {
        version = release(libs.versions.compileSdk.get().toInt()) {
            minorApiLevel = 0
        }
    }

    defaultConfig {
        applicationId = "com.johnny.wxscript"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 2
        versionName = "v1.1.0"

        buildConfigField("String", "TAG", "\"WxScript\"")
        buildConfigField("String", "HOST_PACKAGE", "\"com.tencent.mm\"")
        buildConfigField("String", "FLAVOR_SLUG", "\"standard\"")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.toVersion(libs.versions.jdk.get().toInt())
        targetCompatibility = JavaVersion.toVersion(libs.versions.jdk.get().toInt())
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    packaging {
        resources.excludes += listOf(
            "kotlin/**",
            "**.bin",
            "kotlin-tooling-metadata.json",
            "META-INF/INDEX.LIST",
            "META-INF/*.kotlin_module"
        )
        resources.merges += listOf("META-INF/xposed/*")
    }

    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.fromTarget(libs.versions.jdk.get()))
        freeCompilerArgs.add("-opt-in=androidx.compose.material3.ExperimentalMaterial3Api")
        freeCompilerArgs.add("-opt-in=androidx.compose.material3.ExperimentalMaterial3ExpressiveApi")
        freeCompilerArgs.add("-Xmax-errors=200")
    }
}

dependencies {
    compileOnly(files("libs/xposed-api-82.jar"))
    compileOnly(project(":libs:common:stubs"))

    implementation(project(":libs:common:bsh"))
    implementation(project(":libs:common:reflekt"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.annotation)
    implementation(libs.androidx.navigationevent)
    implementation(libs.androidx.navigationevent.compose)
    implementation(libs.androidx.fragment)
    implementation(libs.androidx.browser)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)

    implementation(libs.dexkit)
    // 只用到 miuix 的 theme / basic / window（VoiceForwardMiuixDialog 的 WindowDialog）。
    // miuix-blur 的 minSdk 是 33，引进来会拖高本项目 minSdk，因此不引。
    implementation(libs.miuix.ui)

    implementation(libs.materialkolor)
    implementation(libs.kavaref.core)
    implementation(libs.fastjson2)
    implementation(libs.protobuf.javalite)
    implementation(libs.flatbuffers)
    implementation(libs.fastkv)
    implementation(libs.mmkv)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.serialization.protobuf)
    implementation(libs.hiddenapibypass)
    implementation(libs.symbols.filled)
    implementation(libs.symbols.outlined)

    // 内置 Rhino（WCX fork 45f29f6）：JitPack 单点源不可靠，内置保证构建可复现
    implementation(files("libs/rhino-45f29f6.jar"))
}
