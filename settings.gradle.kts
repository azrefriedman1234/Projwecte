pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    // שיניתי מ-FAIL_ON_PROJECT_REPOS ל-PREFER_SETTINGS
    // זה מאפשר לתיקיית app להגדיר את התיקייה libs המקומית
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}
rootProject.name = "PasiflonetMobile"
include(":app")
