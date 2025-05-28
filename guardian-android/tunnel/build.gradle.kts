// tunnel/build.gradle.kts

import java.util.Properties
import java.io.FileInputStream
import org.gradle.api.Project

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

// --- Go Configuration Helper (Simplified) ---
// Reads local.properties JUST to find the go executable directory if specified
fun getGoEnvArgsForNdkBuild(project: Project): Map<String, String> {
    val args = mutableMapOf<String, String>()
    val localProperties = Properties()
    val localPropertiesFile = project.rootProject.file("local.properties")
    if (localPropertiesFile.isFile) {
        try { FileInputStream(localPropertiesFile).use { localProperties.load(it) } }
        catch (e: Exception) { project.logger.warn("Could not read local.properties: ${e.message}") }
    }

    // Only need the Go executable path to pass to Make
    val goPathProp = localProperties.getProperty("go.path") // Expect directory containing 'go'

    // Determine Go executable path (respecting property > PATH > default 'go')
    val goExeName = "go" + if (System.getProperty("os.name").lowercase().contains("windows")) ".exe" else ""
    val goExePath = if (!goPathProp.isNullOrBlank()) {
        project.file(goPathProp).resolve(goExeName).takeIf { it.canExecute() }?.absolutePath
    } else {
        // Check PATH - Note: This check isn't perfect from Gradle config phase
        // Rely on Make to find 'go' on PATH if property isn't set/valid
        null
    } ?: "go" // Default to 'go' if property invalid or not found on PATH easily

    args["GO_EXE"] = goExePath // Pass the resolved path or just 'go'

    // GOPATH/GOCACHE can be handled within Makefile defaults or relative paths
    project.logger.lifecycle("Passing GO_EXE='${args["GO_EXE"]}' to ndk-build.")
    return args
}


android {
    namespace = "org.mozilla.guardian.tunnel"
    compileSdk = 35

    defaultConfig {
        minSdk = 27


        externalNativeBuild{
            cmake {
                targets.add("go_shared_lib")
            }
        }
    }
    // Configure ndk-build integration
    externalNativeBuild {
        cmake {
            path("src/go/CMakeLists.txt")
        }
    }

    buildFeatures {
        aidl = true
        buildConfig = true
    }

    // AGP typically finds .so files from ndk-build output (libs/<abi>) automatically.
    // Explicitly setting sourceSets.main.jniLibs might interfere or be redundant.
    // Let's rely on the default behavior first.
    // sourceSets.getByName("main").jniLibs.srcDirs("libs") // Default ndk-build output dir

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.security.crypto.ktx)
    implementation(libs.androidx.security.identity.credential)
}


val aidlInputDir = file("src/main/aidl")
val aidlOutputDir = file("$buildDir/outputs/aidl")

fun resolveAidlCppExecutable(): String {
    val sdkDir = System.getenv("ANDROID_SDK_ROOT")
        ?: rootProject.file("local.properties")
            .takeIf { it.exists() }
            ?.inputStream()
            ?.use { propsStream ->
                Properties().apply { load(propsStream) }.getProperty("sdk.dir")
            }
        ?: throw GradleException("ANDROID_SDK_ROOT or sdk.dir in local.properties must be set")

    return File(sdkDir, "build-tools")
        .listFiles()
        ?.maxByOrNull { it.name }  // Latest build-tools version
        ?.resolve("aidl")
        ?.absolutePath
        ?: throw GradleException("Could not find aidl in build-tools.")
}

tasks.register("generateAidlCpp") {
    group = "build"
    description = "Generates C++ headers from AIDL files"

    doLast {
        val aidlExecutable = resolveAidlCppExecutable()
        aidlOutputDir.mkdirs()

        aidlInputDir.walkTopDown()
            .filter { it.extension == "aidl" }
            .forEach { aidlFile ->
                exec {
                    commandLine(
                        aidlExecutable,
                        "--lang=cpp",
                        "-h$aidlOutputDir",
                        "-I$aidlInputDir",
                        "-o$aidlOutputDir",
                        aidlFile.absolutePath
                    )
                }
            }
    }
}
