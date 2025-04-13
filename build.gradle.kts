import com.lagradost.cloudstream3.gradle.CloudstreamExtension
import com.android.build.gradle.BaseExtension

buildscript {
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }

    dependencies {
        classpath("com.android.tools.build:gradle:7.4.2") // AGP compatible avec compileSdk 33
        classpath("com.github.recloudstream:gradle:master-SNAPSHOT")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.1.0") // Kotlin 2.1.0
    }
}

allprojects {
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }
}

fun Project.cloudstream(configuration: CloudstreamExtension.() -> Unit) =
    extensions.getByName<CloudstreamExtension>("cloudstream").configuration()

fun Project.android(configuration: BaseExtension.() -> Unit) =
    extensions.getByName<BaseExtension>("android").configuration()

subprojects {
    apply(plugin = "com.android.library")
    apply(plugin = "kotlin-android")
    apply(plugin = "com.lagradost.cloudstream3.gradle")

    cloudstream {
        setRepo(System.getenv("GITHUB_REPOSITORY") ?: "user/repo")
    }

    android {
        compileSdkVersion(33)
        namespace = "com.lagradost"

        defaultConfig {
            minSdk = 21
            targetSdk = 33
        }

        compileOptions {
            sourceCompatibility = JavaVersion.VERSION_1_8
            targetCompatibility = JavaVersion.VERSION_1_8
        }

        tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
            kotlinOptions {
                jvmTarget = "1.8"
                freeCompilerArgs = freeCompilerArgs +
                        "-Xno-call-assertions" +
                        "-Xno-param-assertions" +
                        "-Xno-receiver-assertions"
            }
        }
    }

    dependencies {
        val apk by configurations
        val implementation by configurations

        apk("com.lagradost:cloudstream3:pre-release")

        implementation("org.jetbrains.kotlin:kotlin-stdlib:2.1.0")
        implementation("com.github.Blatzar:NiceHttp:0.3.2")
        implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.13.1")
        implementation("org.jsoup:jsoup:1.13.1")
        implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.6.4")
        implementation("org.mozilla:rhino:1.7.14")
        implementation("me.xdrop:fuzzywuzzy:1.4.0")
    }
}

task<Delete>("clean") {
    delete(rootProject.buildDir)
}
