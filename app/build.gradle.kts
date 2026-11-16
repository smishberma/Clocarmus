import com.android.build.api.artifact.SingleArtifact
import javax.xml.parsers.DocumentBuilderFactory

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Use the resolved graph and merged manifest, so transitive AAR requirements/permissions
// are checked too. Runtime codec, SAF and foreground-service tests remain separate gates.
androidComponents.onVariants(androidComponents.selector().withBuildType("debug")) { variant ->
    val mergedManifest = variant.artifacts.get(SingleArtifact.MERGED_MANIFEST)
    val compatibilityCheck = tasks.register("verifyDebugCompatibility") {
        group = "verification"
        description = "Checks API 23/SDK 35 compatibility and the offline merged manifest."
        dependsOn("checkDebugAarMetadata")
        inputs.file(mergedManifest)
        doLast {
            val factory = DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = true
                setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            }
            val manifest = factory.newDocumentBuilder().parse(mergedManifest.get().asFile)
            val namespace = "http://schemas.android.com/apk/res/android"
            val sdk = manifest.getElementsByTagName("uses-sdk").item(0) as org.w3c.dom.Element
            check(sdk.getAttributeNS(namespace, "minSdkVersion") == "23")
            check(sdk.getAttributeNS(namespace, "targetSdkVersion") == "35")
            check(manifest.documentElement.getAttribute("package") == "com.smishberma.clocarmus")
            val permissions = manifest.getElementsByTagName("uses-permission")
            for (index in 0 until permissions.length) {
                val name = (permissions.item(index) as org.w3c.dom.Element).getAttributeNS(namespace, "name")
                check(name != "android.permission.INTERNET" && name != "android.permission.ACCESS_NETWORK_STATE") {
                    "Offline app unexpectedly declares $name"
                }
            }
            check(android.compileSdk == 35)
            check(android.compileOptions.sourceCompatibility == JavaVersion.VERSION_17)
            check(android.compileOptions.targetCompatibility == JavaVersion.VERSION_17)
            logger.lifecycle("Compatibility passed: API 23 minimum, SDK 35, Java 17, com.smishberma.clocarmus app ID, no network permissions.")
        }
    }
    tasks.named("check").configure { dependsOn(compatibilityCheck) }
}

android {
    useLibrary("android.test.runner", false)
    useLibrary("android.test.base", false)
    namespace = "com.example.clocarmus"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.smishberma.clocarmus"
        minSdk = 23
        targetSdk = 35
        versionCode = 2
        versionName = "1.0.1"
        testInstrumentationRunner = "android.test.InstrumentationTestRunner"
        // Compose renders vectors directly on API 23, including gradients.
        vectorDrawables.useSupportLibrary = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.session)
    implementation(libs.androidx.room.runtime)
    implementation(libs.kotlinx.coroutines.android)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)

    implementation(libs.lottie.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
