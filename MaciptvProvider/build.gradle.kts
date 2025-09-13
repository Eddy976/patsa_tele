// Top-level build file where you can add configuration options common to all sub-projects/modules.
buildscript {
    ext.kotlin_version = '1.9.22'  // Version récente pour compatibilité CloudStream3
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlin_version")
        // Ajout pour CloudStream plugin (si pas dans root)
        classpath("com.github.recloudstream:cloudstream-gradle-plugin:0.2.5")  // Ajustez version
    }
}

plugins {
    id 'com.android.application'
    id 'org.jetbrains.kotlin.android'
    id 'cloudstream'  // Plugin pour injection CloudStream3 API
}

android {
    namespace 'com.lagradost'  // Ajout pour supprimer warning namespace (supprimez package de AndroidManifest.xml)
    compileSdk 34  // Mise à jour pour Android 14+

    defaultConfig {
        applicationId "com.lagradost.maciptvprovider"
        minSdk 21
        targetSdk 34
        versionCode 10  // Déjà présent
        versionName "1.0"
        testInstrumentationRunner "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            minifyEnabled false
            proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'), 'proguard-rules.pro'
        }
    }

    compileOptions {
        sourceCompatibility JavaVersion.VERSION_1_8
        targetCompatibility JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = '1.8'
    }
}

dependencies {
    // Dépendances CloudStream3 (injectées par plugin, mais explicites pour compilation)
    implementation 'com.github.recloudstream:cloudstream-api:0.2.5'  // Ajustez à votre version ; inclut MainAPI, TvType, etc.
    
    // Kotlin core
    implementation "org.jetbrains.kotlin:kotlin-stdlib:$kotlin_version"
    implementation 'org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3'  // Pour suspend fun et runBlocking
    
    // JSON parsing (pour parseJson/toJson et @JsonProperty)
    implementation 'com.fasterxml.jackson.module:jackson-module-kotlin:2.16.1'
    implementation 'com.fasterxml.jackson.core:jackson-databind:2.16.1'
    
    // FuzzySearch (déjà utilisé dans le code)
    implementation 'me.xdrop:fuzzywuzzy:1.4.0'
    
    // AndroidX (mises à jour)
    implementation 'androidx.core:core-ktx:1.12.0'
    implementation 'androidx.appcompat:appcompat:1.6.1'
    implementation 'androidx.legacy:legacy-support-v4:1.0.0'
    implementation 'com.google.android.material:material:1.11.0'  // Mise à jour
    implementation 'androidx.lifecycle:lifecycle-extensions:2.2.0'
    implementation 'androidx.tvprovider:tvprovider:1.1.0'  // Pour TvType et IPTV support (optionnel mais recommandé)
    
    // Tests (optionnel)
    testImplementation 'junit:junit:4.13.2'
    androidTestImplementation 'androidx.test.ext:junit:1.1.5'
    androidTestImplementation 'androidx.test.espresso:espresso-core:3.5.1'
}

// Configuration CloudStream (déjà bonne, mais tvTypes corrigé en Set<TvType>)
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
    tvTypes = setOf(TvType.Live, TvType.TvSeries, TvType.Movie, TvType.Anime)  // Corrigé : Set<TvType> au lieu de List<String>

    iconUrl = "https://www.google.com/s2/favicons?domain=franceiptv.fr/&sz=%size%"
    requiresResources = true
}
