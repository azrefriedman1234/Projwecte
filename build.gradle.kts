// Top-level build file
buildscript {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    dependencies {
        classpath("com.android.tools.build:gradle:8.2.0")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:1.9.20")
    }
}

allprojects {
    repositories {
        google()
        mavenCentral()
        // שרת גיבוי למקרה ש-Maven Central קורס או חוסם (פותר שגיאות 403)
        maven { url = uri("https://jcenter.bintray.com/") }
        maven { url = uri("https://jitpack.io") }
    }
}
