import java.io.File
import java.util.Base64

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
        versionCode = 5
        versionName = "v1.5.0"

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

    // ─── 固定签名 ──────────────────────────────────────────────────────
    // WxScript 用一个长期不变的密钥签名（生成于 2026-11-02，有效期 100 年），
    // 保证后续所有版本都能覆盖安装、不会因为签名变化要求卸载重装。
    // 解析顺序：KEYSTORE_BASE64（CI secrets）→ WXSCRIPT_KEYSTORE_FILE → 仓库根 keystore/
    val releaseStoreFile: File? = run {
        val local = rootProject.file("keystore/wxscript-release.jks")
        val fromEnvPath = System.getenv("WXSCRIPT_KEYSTORE_FILE")?.takeIf { it.isNotBlank() }?.let(::File)
        val b64 = System.getenv("KEYSTORE_BASE64")
        when {
            fromEnvPath != null && fromEnvPath.exists() -> fromEnvPath
            !b64.isNullOrBlank() -> {
                val target = layout.buildDirectory.file("keystore/wxscript-release.jks").get().asFile
                target.parentFile?.mkdirs()
                target.writeBytes(Base64.getDecoder().decode(b64))
                target
            }
            local.exists() -> local
            else -> null
        }
    }

    signingConfigs {
        if (releaseStoreFile != null) {
            create("release") {
                storeFile = releaseStoreFile
                storePassword = System.getenv("STORE_PASSWORD") ?: "WxScript-2026-Sign"
                keyAlias = System.getenv("KEY_ALIAS") ?: "wxscript"
                keyPassword = System.getenv("KEY_PASSWORD") ?: "WxScript-2026-Sign"
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (releaseStoreFile != null) signingConfig = signingConfigs.getByName("release")
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
    // 模块自身进程绑定 LSPosed 服务（显示框架版本/作用域），与使用 legacy API 不冲突
    implementation("io.github.libxposed:service:102.0.0")
    // 代理微信设置页的抽象设置项（java.lang.reflect.Proxy 只能代理接口）
    implementation("com.linkedin.dexmaker:dexmaker:2.28.5")
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
