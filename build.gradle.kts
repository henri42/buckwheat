buildscript {
    repositories {
        google()
        mavenCentral()

        maven { url=uri("https://androidx.dev/snapshots/builds/-/artifacts/repository") }
    }

    dependencies {
        classpath("com.android.tools.build:gradle:7.4.1")
        classpath("com.google.dagger:hilt-android-gradle-plugin:2.44")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:1.7.10")
        classpath("com.diffplug.spotless:spotless-plugin-gradle:6.11.0")
        classpath("com.google.android.libraries.mapsplatform.secrets-gradle-plugin:secrets-gradle-plugin:2.0.1")
    }
}

subprojects {
    repositories {
        google()
        mavenCentral()
    }

    pluginManager.apply("com.diffplug.spotless")

    configure<com.diffplug.gradle.spotless.SpotlessExtension> {
        kotlin {
            // by default the target is every '.kt' and '.kts` file in the java sourcesets
            ktfmt()    // has its own section below
            ktlint()   // has its own section below
            diktat()   // has its own section below
            prettier() // has its own section below
            licenseHeaderFile(rootProject.file("spotless/copyright.kt")) // or licenseHeaderFile
        }
        kotlinGradle {
            target("*.gradle.kts") // default target for kotlinGradle
            ktlint() // or ktfmt() or prettier()
        }
    }
}

plugins {
    id("com.github.ben-manes.versions") version "0.43.0"
    id("io.sentry.android.gradle") version "3.3.0"

}