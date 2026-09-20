import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jetbrains.kotlin.konan.properties.Properties
import java.io.FileInputStream

plugins {
    alias(libs.plugins.android)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.kotlinParcelize)
    alias(libs.plugins.ksp)
    alias(libs.plugins.detekt)
}

val keystorePropertiesFile: File = rootProject.file("keystore.properties")
val keystoreProperties = Properties()
if (keystorePropertiesFile.exists()) {
    keystoreProperties.load(FileInputStream(keystorePropertiesFile))
}

fun hasSigningVars(): Boolean {
    return providers.environmentVariable("SIGNING_KEY_ALIAS").orNull != null
            && providers.environmentVariable("SIGNING_KEY_PASSWORD").orNull != null
            && providers.environmentVariable("SIGNING_STORE_FILE").orNull != null
            && providers.environmentVariable("SIGNING_STORE_PASSWORD").orNull != null
}

// This fork is versioned on its own terms; see gradle.properties. VERSION_NAME and VERSION_CODE
// stay exactly as upstream ships them, so syncing never conflicts on them, and they record which
// release this is built on.
val leviniumVersionName = project.property("LEVINIUM_VERSION_NAME").toString()
val leviniumVersionCode = project.property("LEVINIUM_VERSION_CODE").toString().toInt()

base {
    archivesName = "levinium-messages-$leviniumVersionCode"
}

android {
    compileSdk = project.libs.versions.app.build.compileSDKVersion.get().toInt()

    defaultConfig {
        applicationId = project.property("APPLICATION_ID").toString()

        // Commons toggles the launcher icon aliases using component names it builds as
        // "${appId.removeSuffix(".debug")}.activities.SplashActivity<Color>". Manifest names have to
        // match that exactly, so this placeholder carries the applicationId without the debug
        // suffix -- do not use ${applicationId}, which includes it on debug builds.
        manifestPlaceholders["appId"] = project.property("APPLICATION_ID").toString()

        minSdk = project.libs.versions.app.build.minimumSDK.get().toInt()
        targetSdk = project.libs.versions.app.build.targetSDK.get().toInt()
        versionName = leviniumVersionName
        versionCode = leviniumVersionCode
        ksp {
            arg("room.schemaLocation", "$projectDir/schemas")
        }
    }

    signingConfigs {
        if (keystorePropertiesFile.exists()) {
            register("release") {
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
            }
        } else if (hasSigningVars()) {
            register("release") {
                keyAlias = providers.environmentVariable("SIGNING_KEY_ALIAS").get()
                keyPassword = providers.environmentVariable("SIGNING_KEY_PASSWORD").get()
                storeFile = file(providers.environmentVariable("SIGNING_STORE_FILE").get())
                storePassword = providers.environmentVariable("SIGNING_STORE_PASSWORD").get()
            }
        } else {
            logger.warn("Warning: No signing config found. Build will be unsigned.")
        }
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (keystorePropertiesFile.exists() || hasSigningVars()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    flavorDimensions.add("variants")
    productFlavors {
        register("core")
        register("foss")
        register("gplay")
    }

    sourceSets {
        getByName("main").java.directories.add("src/main/kotlin")
        getByName("test").java.directories.add("src/test/kotlin")
    }

    compileOptions {
        val currentJavaVersionFromLibs = JavaVersion.valueOf(libs.versions.app.build.javaVersion.get())
        sourceCompatibility = currentJavaVersionFromLibs
        targetCompatibility = currentJavaVersionFromLibs
    }

    dependenciesInfo {
        includeInApk = false
    }

    androidResources {
        @Suppress("UnstableApiUsage")
        generateLocaleConfig = true
    }

    tasks.withType<KotlinCompile> {
        compilerOptions.jvmTarget.set(
            JvmTarget.fromTarget(project.libs.versions.app.build.kotlinJVMTarget.get())
        )
    }

    namespace = project.property("APP_ID").toString()

    lint {
        checkReleaseBuilds = false
        abortOnError = true
        warningsAsErrors = false
        baseline = file("lint-baseline.xml")
        lintConfig = rootProject.file("lint.xml")
    }

    bundle {
        language {
            enableSplit = false
        }
    }
}

detekt {
    baseline = file("detekt-baseline.xml")
    config.setFrom("$rootDir/detekt.yml")
    buildUponDefaultConfig = true
    allRules = false
}

dependencies {
    implementation(libs.fossify.commons)
    implementation(libs.eventbus)
    implementation(libs.indicator.fast.scroll)
    implementation(libs.mmslib)
    implementation(libs.androidx.swiperefreshlayout)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.documentfile)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.ez.vcard)
    implementation(libs.photoview)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.ui)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.bundles.room)
    ksp(libs.androidx.room.compiler)
    detektPlugins(libs.compose.detekt)
    testImplementation(libs.junit)
}
