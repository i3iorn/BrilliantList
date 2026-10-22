plugins {
    id("org.jetbrains.kotlin.jvm")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    // Pure Kotlin module: the JVM unit tests here run without a device and are
    // the only real tests executed by CI (see .github/workflows/ci.yml).
    testImplementation(libs.junit)
}
