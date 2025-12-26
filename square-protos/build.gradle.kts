plugins {
    kotlin("jvm") version "2.2.20"
    id("com.squareup.wire") version "5.3.5"
}

repositories {
    mavenCentral()
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

wire {
    kotlin {
        // Generate Kotlin code for all protos
    }
}
