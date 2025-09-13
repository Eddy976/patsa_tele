import com.android.build.gradle.BaseExtension
import com.lagradost.cloudstream3.gradle.CloudstreamExtension
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

buildscript {
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")  // Pour CloudStream et extensions
    }

    extra["kotlin_version"] = "1.9.22"  // Correction : extra au lieu de ext

    dependencies {
        classpath("com.android.tools.build:gradle:8.7.3")  // AGP récente pour SDK 33+
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:${extra["kotlin_version"]}")
        // Plugin CloudStream pour build extensions
        classpath("com.github.recloudstream:cloudstream-gradle-plugin:0.2.5")  // Version récente
    }
}

plugins {
    id("com.android.library")  // Pour provider (library, pas app)
    id("org.jetbrains.kotlin.android")
    id("com.lagradost.cloudstream3.gradle")  // Correction : nom complet du plugin CloudStream
}

android {
    namespace = "com.lagradost"  // Correction : = au lieu de ' '
    compileSdk = 34

    defaultConfig {
        minSdk = 21
        targetSdk = 34
        versionCode = 10
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = JvmTarget.JVM_1_8  // Correction : JvmTarget enum
    }
}

tasks.withType<KotlinJvmCompile> {
    kotlinOptions {
        jvmTarget = JvmTarget.JVM_1_8
    }
}

dependencies {
    // CloudStream3 API (essentielle pour MainAPI, TvType, new* méthodes)
    implementation("com.github.recloudstream:cloudstream-api:0.2.5")

    // Kotlin core + coroutines (pour suspend, runBlocking)
    implementation("org.jetbrains.kotlin:kotlin-stdlib:${extra["kotlin_version"]}")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // JSON (pour @JsonProperty, parseJson/toJson)
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.16.1")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.16.1")

    // FuzzyWuzzy (utilisé dans le code)
    implementation("me.xdrop:fuzzywuzzy:1.4.0")

    // AndroidX bases (mises à jour)
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.legacy:legacy-support-v4:1.0.0")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.lifecycle:lifecycle-extensions:2.2.0")
    implementation("androidx.tvprovider:tvprovider:1.1.0")  // Pour TvType/Live support

    // Tests (optionnel)
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}

// Extension CloudStream (corrigée pour Kotlin DSL)
cloudstream {
    description = "Add your IPTV account or use the default account to watch Live TV, Movies,TvSeries ..."
    authors = listOf("Eddy")

    /**
     * Status int as the following:
     * 0: Down
     * 1: Ok
     * 2: Slow
     * 3: Beta only
     * */
    status = 0 // will be 3 if unspecified
    tvTypes = setOf(TvType.Live, TvType.TvSeries, TvType.Movie, TvType.Anime)  // Correction : setOf<TvType>

    iconUrl = "https://www.google.com/s2/favicons?domain=franceiptv.fr/&sz=%size%"
    requiresResources = true
}
