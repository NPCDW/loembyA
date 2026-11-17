import com.android.build.api.variant.FilterConfiguration
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.io.File
import java.util.Properties

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose.compiler)
  alias(libs.plugins.kotlinx.serialization)
  alias(libs.plugins.ksp)
  alias(libs.plugins.room)
  alias(libs.plugins.aboutlibraries)
}

// ── release lint 开关（清单 1.2）──
// 定义在 android {} 之前，好让 lint { checkReleaseBuilds } 和下面的
// tasks.matching { lintVital } 共用同一个值，避免两处各读一遍属性。
// 取普通 Boolean 而非 Provider：checkReleaseBuilds 的签名不接受 Provider。
// 这里用 orNull 而不是 .get()，是为了不强制求值、不打断配置缓存。
val lintVitalOn: Boolean = providers.gradleProperty("lintVital").orNull?.toBoolean() ?: false

// CI 外部签名必须显式启用；缺省或 false 保留本机 release 签名行为。
val externalReleaseSigning: Boolean = providers.gradleProperty("externalReleaseSigning").orNull?.let { value ->
  value.toBooleanStrictOrNull() ?: throw GradleException("externalReleaseSigning must be true or false.")
} ?: false

android {
  namespace = "app.marlboroadvance.mpvex"
  compileSdk = 37

  // 开发期快速出包开关（只打单个 ABI 包，避免 4×151MB 原生库合并把构建拖超时）：
  //   -Pabi=x86_64      只出 x86_64 单包（模拟器用这个）
  //   -Pabi=arm64-v8a   只出 arm64 单包（真机用这个）
  //   -Parm64Only       等价于 -Pabi=arm64-v8a（保留旧用法，向后兼容）
  // 不带任何参数时，构建行为与原来完全一致（4 个 ABI 拆包 + universal）。
  val singleAbi: String? =
    (project.findProperty("abi") as String?)?.takeIf { it.isNotBlank() }
      ?: if (project.hasProperty("arm64Only")) "arm64-v8a" else null

  defaultConfig {
    // 应用标识（安装包名）：使用 loembyA 自身的 com.github.npcdw.loembya。
    // namespace 保持不动，它决定源码组件与 R / BuildConfig 的包名。
    applicationId = "com.github.npcdw.loembya"
    minSdk = 26
    targetSdk = 36
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    // versionCode 决定能否覆盖安装：每发一版都要涨（APK 里的最终值 = versionCode * 10 + ABI 码）
    versionCode = 241
    versionName = "0.4.0"

    vectorDrawables {
      useSupportLibrary = true
    }

    buildConfigField("String", "GIT_SHA", "\"${getCommitSha()}\"")

    // ── dandanplay 开放平台凭证（可选，**刻意不硬编码**）──
    // 为什么走 local.properties 而不是写死：
    //   官方文档明确不建议在客户端硬编码 AppSecret（源码一开源就等于公开）。
    //   所以这里是「构建期从本机文件读 → 注入 BuildConfig → 运行期由用户可覆盖」。
    //   local.properties 已被 .gitignore 忽略，仓库里永远不会出现密钥。
    // 没配 local.properties 也能编：两个字段为空串 ⇒ 运行时不下发签名头，
    // 弹幕只对「自建服务端 / 未开认证的兼容服务端」可用（这本身就是一条有效路径）。
    buildConfigField("String", "DANDAN_APP_ID", "\"${localDanmakuAppId}\"")
    buildConfigField("String", "DANDAN_APP_SECRET", "\"${localDanmakuAppSecret}\"")
  }

  flavorDimensions += "distribution"

  productFlavors {
    create("standard") {
      dimension = "distribution"
      isDefault = true
      buildConfigField("boolean", "ENABLE_UPDATE_FEATURE", "true")
    }

    create("playstore") {
      dimension = "distribution"
      versionNameSuffix = "-playstore"
      buildConfigField("boolean", "ENABLE_UPDATE_FEATURE", "false")
    }

    create("fdroid") {
      dimension = "distribution"
      versionNameSuffix = "-fdroid"
      buildConfigField("boolean", "ENABLE_UPDATE_FEATURE", "false")

      ndk {
        // 与 splits 的 ABI 过滤互斥：单包模式（-Pabi / -Parm64Only）下必须留空，否则 AGP 直接报配置冲突
        if (singleAbi == null) abiFilters += "arm64-v8a"
      }
    }
  }

  dependenciesInfo {
    includeInApk = false
    includeInBundle = false
  }

  splits {
    abi {
      isEnable = true
      reset()
      if (singleAbi != null) {
        include(singleAbi)
        isUniversalApk = false
      } else {
        include("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
        isUniversalApk = true
      }
    }
  }

  // ── Release 签名 ──
  // 私钥路径与口令统一放在仓库根的 keystore.properties（.gitignore 已屏蔽），
  // 避免把口令硬编码进构建脚本。外部签名模式忽略本机配置，交给 CI 对中间 APK 签名。
  // 默认模式缺少完整配置时不抛异常；若构建 release，其 APK 将未签名，debug 签名不受影响。
  val keystorePropsFile = rootProject.file("keystore.properties")
  val keystoreProps = Properties()
  if (!externalReleaseSigning && keystorePropsFile.exists()) {
    keystorePropsFile.inputStream().use { keystoreProps.load(it) }
  }
  val releaseKeystoreFile: File? =
    keystoreProps.getProperty("storeFile")?.trim()?.takeIf { it.isNotEmpty() }?.let { rootProject.file(it) }
  val releaseStorePassword = keystoreProps.getProperty("storePassword")?.takeIf { it.isNotBlank() }
  val releaseKeyAlias = keystoreProps.getProperty("keyAlias")?.takeIf { it.isNotBlank() }
  val releaseKeyPassword = keystoreProps.getProperty("keyPassword")?.takeIf { it.isNotBlank() }
  val hasReleaseKeystore = releaseKeystoreFile?.exists() == true &&
    releaseStorePassword != null && releaseKeyAlias != null && releaseKeyPassword != null

  if (externalReleaseSigning) {
    logger.lifecycle(
      "[loembyA] externalReleaseSigning=true：release APK 仅为未签名中间产物；CI 将在构建后签名并验证最终 APK。",
    )
  } else if (!hasReleaseKeystore) {
    logger.lifecycle(
      "[loembyA] 未找到完整 release 签名配置（storeFile/storePassword/keyAlias/keyPassword）；" +
        "若构建 release，将产出未签名 APK；debug 签名不受影响。",
    )
  }

  signingConfigs {
    if (!externalReleaseSigning && hasReleaseKeystore) {
      create("release") {
        storeFile = releaseKeystoreFile
        storePassword = releaseStorePassword
        keyAlias = releaseKeyAlias
        keyPassword = releaseKeyPassword
        // minSdk 26 → V2 已全线支持，无需 V1（省掉 META-INF 里的冗余签名文件）。
        enableV1Signing = false
        enableV2Signing = true
        enableV3Signing = true
      }
    }
  }

  buildTypes {
    named("release") {
      signingConfig = if (externalReleaseSigning) null else signingConfigs.findByName("release")
      isMinifyEnabled = true
      isShrinkResources = true
      proguardFiles(
        getDefaultProguardFile("proguard-android-optimize.txt"),
        "proguard-rules.pro"
      )
      ndk {
        debugSymbolLevel = "none"
      }
    }

    create("preview") {
      initWith(getByName("release"))
      signingConfig = null
      applicationIdSuffix = ".preview"
      versionNameSuffix = "-${getCommitCount()}"
    }

    named("debug") {
      applicationIdSuffix = ".debug"
      versionNameSuffix = "-${getCommitCount()}"
    }
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }

  buildFeatures {
    compose = true
    viewBinding = true
    buildConfig = true
  }

  // ── 测试（清单 1.2）──
  sourceSets {
    getByName("androidTest") {
      // Room 2.8 的 MigrationTestHelper 按 databaseClass.canonicalName 原样查找：
      // app.marlboroadvance.mpvex.database.MpvExDatabase/<version>.json。
      // 同时保留历史小写目录，供旧测试资产/旧版本 schema 使用。
      assets.directories.add("$projectDir/schemas")
    }
  }

  testOptions {
    unitTests {
      isReturnDefaultValues = true
    }
  }

  // ── Lint（清单 1.2）──
  // 规则集本身在 app/lint.xml 里（致命项 error、存量噪音降级 warning、误报 ignore）。
  lint {
    // 静态检查出错就中断构建 —— 前提是 lint.xml 已把存量噪音降级，
    // 否则第一次跑就会被几百条历史问题卡住，配置也就白配了。
    abortOnError = true
    // 警告不阻断出包，但会写进报告供体检用。
    warningsAsErrors = false
    checkDependencies = false
    // release 变体的 lint 检查很慢（实测 lintVitalAnalyze 要 7 分钟），
    // 日常出包关掉；上线前体检加 -PlintVital=true 跑一次。
    // 与下面那段 tasks.matching { name.startsWith("lintVital") } 开关是同一件事，
    // 这里从 lint 配置侧再关一次，保证不带参数时任何路径都不会误触发。
    checkReleaseBuilds = lintVitalOn
  }

  packaging {
    resources {
      excludes += "/META-INF/{AL2.0,LGPL2.1}"
      excludes += "META-INF/DEPENDENCIES"
      excludes += "META-INF/LICENSE*"
      excludes += "META-INF/NOTICE*"
      excludes += "META-INF/*.kotlin_module"
      excludes += "META-INF/versions/9/OSGI-INF/MANIFEST.MF"
    }
    jniLibs {
      useLegacyPackaging = true
    }
  }

  @Suppress("UnstableApiUsage")
  androidResources {
    generateLocaleConfig = true
  }
}

// ── 构建提速：默认关掉 release 的 lintVital ──
//
// 实测（本机 6 核 i7-8750H）assembleStandardRelease 任务耗时分布：
//   lintVitalAnalyzeStandardRelease   7m02s  ← 比 R8 还慢
//   minifyStandardReleaseWithR8       6m31s
//   compileJavaWithJavac                54s
//   其余全部                          ~3m
// lintVital 是 AGP 给 release 变体加的「致命问题」静态检查，跑在 R8 之后、
// 且几乎吃不掉并行度（它和 R8 抢 CPU），是本机构建最大的一块纯开销。
// 日常出包关掉它；要上线前体检时加 -PlintVital=true 跑一次即可。
// 值复用 android {} 之前声明的 lintVitalOn（那里 lint { checkReleaseBuilds } 也要用），
// 避免同一个开关在两处各解析一遍。
tasks.matching { it.name.startsWith("lintVital") }.configureEach {
  enabled = lintVitalOn
}

androidComponents {
  val abiCodes = mapOf(
    "armeabi-v7a" to 1,
    "arm64-v8a" to 2,
    "x86" to 3,
    "x86_64" to 4
  )

  onVariants { variant ->
    variant.outputs.forEach { output ->
      val abi = output.filters
        .find { it.filterType == FilterConfiguration.FilterType.ABI }
        ?.identifier

      output.versionCode.set(
        (output.versionCode.orNull ?: 0) * 10 + (abiCodes[abi] ?: 0)
      )
    }
  }
}

kotlin {
  compilerOptions {
    freeCompilerArgs.addAll(
      "-Xcontext-parameters",
      "-Xannotation-default-target=param-property",
      "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
      // 视界流翻页要用 VerticalPager（来自 androidx.compose.foundation）
      "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi"
    )
    jvmTarget.set(JvmTarget.JVM_17)
  }
}

composeCompiler {
  // 源信息（把重组对应回源码行号）只有 debug 的 Compose 工具链用得上。
  // release 打开会给每个 composable 塞额外字符串元数据：Compose 编译器更慢、
  // R8 要处理的代码量更大（R8 占 release 构建 6m31s），产物也更大。
  includeSourceInformation = false
  // Compose mapping only feeds tooling/obfuscation diagnostics and pulls an extra artifact.
  // Disable it so offline release builds remain reproducible with the checked dependency cache.
  includeComposeMappingFile = false
}

room {
  schemaDirectory("$projectDir/schemas")
}

dependencies {
  implementation(libs.androidx.activity.compose)
  // PostgreSQL 验证通过后用 ProcessLifecycleOwner 管理共享数据的前台刷新。
  // 进程生命周期不是 activity-compose 的传递依赖，版本与 lifecycle-runtime 对齐。
  implementation("androidx.lifecycle:lifecycle-process:2.10.0")
  implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.ui)
  implementation(libs.androidx.ui.graphics)
  implementation(libs.androidx.material3.android)
  implementation("com.google.android.material:material:1.13.0")
  implementation(libs.androidx.compose.material)
  implementation(libs.androidx.ui.tooling.preview)
  debugImplementation(libs.androidx.ui.tooling)
  implementation(libs.bundles.compose.navigation3)
  implementation(libs.androidx.appcompat)
  implementation(libs.androidx.compose.constraintlayout)
  implementation("androidx.preference:preference-ktx:1.2.1")
  implementation("androidx.constraintlayout:constraintlayout:2.2.0")
  implementation(libs.androidx.material3.icons.extended)
  // foundation 显式声明是为了 VerticalPager —— 原来它是靠 ui 传递进来的，
  // 现在翻页是视界流的核心路径，不该依赖传递关系。
  implementation(libs.androidx.compose.foundation)
  // 背景模糊（Cine 玻璃层）。Compose 官方只有 `Modifier.blur()`（模糊自己），
  // 没有 CSS `backdrop-filter` 的等价物 —— 设计稿里 Dock / 顶栏 / 长按菜单那种
  // 「透出下面内容并磨砂」的效果只能靠它。
  // ⚠️ 只在 ui/cine/** 里用；Classic 界面零引用，所以对老 UI 的实际影响为 0。
  implementation(libs.haze)
  implementation(libs.mediasession)
  implementation(libs.androidx.documentfile)
  implementation(libs.saveable)

  implementation(platform(libs.koin.bom))
  implementation(libs.bundles.koin)

  implementation(libs.compose.prefs)
  implementation(libs.aboutlibraries.compose.m3)


  implementation(libs.room.runtime)
  ksp(libs.room.compiler)

  implementation(libs.kotlinx.immutable.collections)
  implementation(libs.kotlinx.serialization.json)
  implementation(libs.postgresql)
  implementation(libs.okhttp)
  implementation(libs.ktor.server.cio)
  implementation(libs.brotli.dec)
  // Android AAR supplies ABI-specific zstd JNI; the desktop JAR cannot load on Android.
  implementation("com.github.luben:zstd-jni:${libs.versions.zstd.get()}@aar")

  // DanmakuFlameMaster is vendored under master/flame/danmaku/ and tv/cjump/jni/.
  // The local parser and lifetime/timer fixes require source integration and the existing keep rules.

  // Lottie：加载动画可换（外观 → 加载动画）。搬的是 Yamby 内置的那 12 个 json（res/raw/yamby_*.json）
  // + 自研的两套，用 lottie-compose 在 Compose 里渲染。
  implementation(libs.lottie.compose)
  // Telephoto：照片浏览器（图集）的捏合缩放 / 拖动平移 / 双击放大手势。
  // 只依赖核心 zoomable 模块 —— 手势与「图片怎么加载」是两件事，图片交给下面的 Coil。
  implementation(libs.telephoto.zoomable)
  // Coil 3：照片链路（浏览器 + 照片网格卡）的图片渲染层。
  // 字节仍然由项目自己的 EmbyImageLoader 取（节流 + 双鉴权 + 缩放失败退回原图），
  // 交给 Coil 的是「解码 / 采样 / 缓存 / 取消」，
  // 见 ui/browser/emby/photo/EmbyPhotoImageLoader.kt。
  // ⚠️ 刻意**没有**引官方的 coil-network-okhttp：那套网络实现不知道 Emby 的鉴权，
  //    引了还要跟我们自己的 Fetcher 抢 String 类型的请求。
  implementation(libs.coil.compose)

    implementation(libs.truetype.parser)
    implementation(libs.fsaf)
    implementation(libs.mediainfo.lib)
    implementation(libs.mpv.android)

    // SideScrollbar owns scrolling and drag throttling without a second library controller.

    // ── Emby Java SDK (OpenAPI 生成) ──
    // 源码 `app/src/main/java/embyclient/` 已从 sourceSet 排除（见上方 android{} 里的说明），
    // 它原本带来的 OkHttp 2 + Gson + gson-fire + swagger-annotations 依赖**全部移除**：
    //   - OkHttp 2 (`com.squareup.okhttp`) 早已 EOL，且与主用的 OkHttp 5 并存
    //     （OkHttp 5 的 Maven 坐标仍是 `com.squareup.okhttp3`，包名也仍是 `okhttp3.*`）；
    //   - Gson 同理，主项目统一用 kotlinx.serialization；
    //   - 主项目源码里 grep 不到 gson / swagger / javax.annotation 的任何引用。
    // 端点路径与参数仍可查 embyclient/ 源码（不参与编译，只作参考）。
    // javax.annotation.* 在 Android 上缺失，若将来真要启用那份 SDK 再补 jsr250-api。

    // ── 测试依赖（清单 1.2）──
    // 之前项目**一个测试都没有**（app/src/test 与 androidTest 目录都不存在），
    // 11 段手写 Room 迁移零覆盖 —— 这是全项目最高的风险点。
    //
    // 分工：
    //   testImplementation  → JVM 单测（分页状态机、错误分类、正则筛选、排序…），秒级
    //   androidTestImplementation → Room 迁移测试（MigrationTestHelper 需要真 SQLite）
    testImplementation(libs.junit)
    // Execute the app-owned stats Lua with a fake mpv API; never packaged in the APK.
    testImplementation("org.luaj:luaj-jse:3.0.1")
    // JVM tests use desktop JNI, while production retains the Android-only AAR above.
    testRuntimeOnly("com.github.luben:zstd-jni:${libs.versions.zstd.get()}")
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.truth)
    testImplementation("org.xerial:sqlite-jdbc:3.41.2.2")

    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.room.testing)
}

/*
 * ── 关于 Emby Java SDK（OpenAPI 生成）目录 `app/src/main/java/embyclient/` ──
 *
 * 它**已从源码目录移出**到 `app/embyclient-reference/`，故不再参与编译，
 * 上面那批 OkHttp 2 + Gson + gson-fire + swagger-annotations 依赖才能全部移除。
 *
 * 移出理由（全部已核实，不是猜测）：
 *   - 全项目零引用：`Grep "^import embyclient"` 在 mpvex 下无命中。实际请求全部走
 *     `domain/emby/EmbyClient.kt` 手写的 OkHttp 5 + kotlinx.serialization。
 *   - OkHttp 2 (`com.squareup.okhttp`) 早已 EOL，却和主用的 OkHttp 5 并存 ——
 *     后者坐标仍是 `com.squareup.okhttp3`，包体、混淆、依赖冲突三重风险。
 *   - Gson 与主项目统一的 kotlinx.serialization 重复；主项目源码里 grep 不到
 *     gson / swagger / javax.annotation 的任何引用。
 *
 * ⚠️ 源码没删，只是挪了位置 —— 查某个 Emby 端点的确切路径 / 参数名时，
 * 去 `app/embyclient-reference/` 照抄到 EmbyClient 手写即可，这本来就是生成它的用途。
 * 新加的功能（播放列表 / 字幕下载 / 相似推荐 / 虚拟库等）都按这个流程做。
 */


/* ---------------- Git helpers ---------------- */

/**
 * 本机可用的 git 可执行文件（找不到就是 null）。
 *
 * 优先使用 GIT_EXE；否则检查 PATH 与 GitHub Desktop 的内置 Git。
 * 直接 exec "git" 必然起不来；而配置期起外部进程失败会让 Gradle 9 判定配置缓存无效
 * （实测报 "failed to compute value with custom source ... starting process 'command 'git''"，
 * 缓存每次被丢弃）。所以只在**确认文件存在**时才真的调用。
 */
fun findGitExecutable(): String? {
  val candidates = mutableListOf<String>()
  System.getenv("GIT_EXE")?.let(candidates::add)
  System.getenv("PATH")?.split(File.pathSeparator)?.forEach { directory ->
    candidates += File(directory, if (System.getProperty("os.name").startsWith("Windows", true)) "git.exe" else "git").absolutePath
  }
  val ghRoot = System.getenv("LOCALAPPDATA")?.let { File(it, "GitHubDesktop") }
  ghRoot
    ?.listFiles()
    ?.filter { it.isDirectory && it.name.startsWith("app-") }
    ?.forEach { appDir ->
      candidates += File(appDir, "resources/app/git/cmd/git.exe").absolutePath
    }
  return candidates.firstOrNull { File(it).exists() }
}

/** 提交数（用作 versionCode / debug 版本号后缀）；拿不到就退回 0 */
fun getCommitCount(): String = runGit("rev-list", "--count", "HEAD") ?: "0"

/** 短提交号（写进 BuildConfig.GIT_SHA）；拿不到就退回 unknown */
fun getCommitSha(): String = runGit("rev-parse", "--short", "HEAD") ?: "unknown"

/**
 * 从本机 `local.properties` 读一个键；读不到返回空串。
 *
 * ⚠️ 只在**值看起来合法**时才返回 —— 它要进 `buildConfigField` 的字符串字面量，
 * 一个没转义的 `"` 或 `\` 会让生成的 BuildConfig.java 语法错误，
 * 而报错信息指向 BuildConfig、完全不提local.properties，极难定位。
 */
fun localSecret(key: String): String {
  val file = rootProject.file("local.properties")
  if (!file.exists()) return ""
  return try {
    val line = file.readLines()
      .map { it.trim() }
      .firstOrNull { it.startsWith("$key=") }
      ?.substringAfter('=')
      ?.trim()
      .orEmpty()
    // 只放行字母数字与下划线：AppId/AppSecret 本来就只由这些字符组成。
    if (line.isNotEmpty() && line.all { it.isLetterOrDigit() || it == '_' }) line else ""
  } catch (e: Exception) {
    ""
  }
}

val localDanmakuAppId: String = localSecret("dandan.appId")
val localDanmakuAppSecret: String = localSecret("dandan.appSecret")

/**
 * 用 providers.exec 而不是 ProcessBuilder 起 git。
 *
 * providers.exec 会被 Gradle 当构建输入跟踪，配置缓存能正常命中；
 * 而 ProcessBuilder 属于「配置期外部进程」，Gradle 9 直接判缓存无效。
 */
fun runGit(vararg args: String): String? {
  val exe = findGitExecutable() ?: return null
  return try {
    providers.exec {
      commandLine(listOf(exe) + args)
      // 不在 git 仓库时不要让构建失败
      isIgnoreExitValue = true
    }.standardOutput.asText.get().trim().ifEmpty { null }
  } catch (e: Exception) {
    null
  }
}
