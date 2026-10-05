plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Cada build do GitHub ganha um número de versão maior, para o Android aceitar a atualização.
val buildNumber: Int = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1

android {
    namespace = "com.taleco.radarcorridas"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.taleco.radarcorridas"
        minSdk = 26
        targetSdk = 34
        versionCode = buildNumber
        versionName = "0.1.$buildNumber"

        // Só a arquitetura dos celulares atuais: deixa o app bem menor.
        ndk {
            abiFilters += listOf("arm64-v8a")
        }
    }

    // Chave fixa: permite instalar versões novas por cima sem perder as configurações.
    signingConfigs {
        create("radar") {
            storeFile = file("radar.keystore")
            storePassword = "radarcorridas"
            keyAlias = "radar"
            keyPassword = "radarcorridas"
        }
    }

    buildTypes {
        getByName("debug") {
            signingConfig = signingConfigs.getByName("radar")
        }
        getByName("release") {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("radar")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    // Leitura de texto em imagem, feita no próprio celular (sem internet)
    implementation("com.google.mlkit:text-recognition:16.0.1")
}
