// Top-level build file
buildscript {
    repositories {
        // מירור מהיר של עליבאבא (Aliyun) לגוגל ו-Public
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        maven { url = uri("https://maven.aliyun.com/repository/public") }
        gradlePluginPortal()
    }
    dependencies {
        classpath("com.android.tools.build:gradle:8.2.0")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:1.9.20")
    }
}

allprojects {
    repositories {
        // סדר חשיבות: קודם כל Aliyun (לא נחסם), אחר כך JitPack, ובסוף המקוריים לגיבוי
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        maven { url = uri("https://maven.aliyun.com/repository/public") }
        maven { url = uri("https://jitpack.io") }
        
        // גיבוי למקרה שמשהו חסר במירור
        google()
        mavenCentral()
    }
}
