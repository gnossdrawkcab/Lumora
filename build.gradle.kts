plugins {
    // AGP 8.12 is the supported stable line for compileSdk 36 (8.2 only supported 34).
    id("com.android.application") version "8.12.2" apply false
    id("org.jetbrains.kotlin.android") version "1.9.22" apply false
    id("com.google.devtools.ksp") version "1.9.22-1.0.17" apply false
}
