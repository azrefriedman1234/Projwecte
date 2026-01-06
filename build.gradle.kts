buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        // שינמוך קל לגרסה סופר-יציבה של הפלאגין
        classpath("com.android.tools.build:gradle:8.1.1")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:1.9.0")
    }
}

allprojects {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}
