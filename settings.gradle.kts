pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
    versionCatalogs {
        create("libs") {
            version("agp", "8.13.1")
            version("kotlin", "2.0.21")
            version("compileSdk", "35")
            version("minSdk", "24")
            version("targetSdk", "35")
            version("coreKtx", "1.15.0")
            version("appcompat", "1.7.0")
            version("material", "1.12.0")
            version("constraintlayout", "2.1.4")
            version("junit4", "4.13.2")
            version("androidxJunit", "1.2.1")
            version("espresso", "3.6.1")

            library("androidx-core-ktx", "androidx.core", "core-ktx").versionRef("coreKtx")
            library("androidx-appcompat", "androidx.appcompat", "appcompat").versionRef("appcompat")
            library("androidx-material", "com.google.android.material", "material").versionRef("material")
            library("androidx-constraintlayout", "androidx.constraintlayout", "constraintlayout").versionRef("constraintlayout")

            library("junit4", "junit", "junit").versionRef("junit4")
            library("androidx-test-ext-junit", "androidx.test.ext", "junit").versionRef("androidxJunit")
            library("androidx-test-espresso-core", "androidx.test.espresso", "espresso-core").versionRef("espresso")

            plugin("android-application", "com.android.application").versionRef("agp")
            plugin("kotlin-android", "org.jetbrains.kotlin.android").versionRef("kotlin")
        }
    }
}

rootProject.name = "echoboard"
include(":app")
