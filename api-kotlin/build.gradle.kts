import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// The same API, said in Kotlin. Nothing here reaches the game: every
// declaration below forwards to a method on `api`, and almost all of them are
// inline, so what a mod compiles down to is the call it would have written by
// hand.
description = "Kotlin extensions for the Sacred Gold mod API"

plugins {
    kotlin("jvm")
}

dependencies {
    // compileOnlyApi rather than api, and the difference is the whole reason a
    // Kotlin mod still passes the linter. The loader hands every mod the API,
    // so a second copy inside a mod jar is a different class with the same name
    // and `Contents` refuses the jar for it. This scope puts `api` on a
    // consumer's compile classpath and keeps it off the runtime one, which is
    // the classpath Shadow packs.
    //
    // Gradle module metadata says exactly that. The POM beside it cannot: it
    // writes this as `compile`, because Maven has no scope that means the same
    // thing. Harmless while every mod is built by Gradle, which reads the
    // metadata and not the POM. A Maven build would have to declare `api` as
    // `provided` itself, which is what maven/README.md in the build repository
    // already says a Maven implementation must do.
    compileOnlyApi(project(":api"))

    // Read for its nullability, not packed: with these on the compile classpath
    // Kotlin sees the API's @Nonnull and @Nullable instead of platform types,
    // so `Game.player` below is declared `Player?` because the Java says so
    // rather than because somebody remembered.
    compileOnly("com.google.code.findbugs:jsr305:3.0.2")
}

kotlin {
    // A published API, so nothing gets a visibility or a return type by
    // accident. Every declaration here is somebody else's compile error later.
    explicitApi()

    compilerOptions {
        // The loader targets Java 21 and starts on whatever JVM the player has.
        // Left alone the Kotlin compiler emits for the JDK running Gradle.
        jvmTarget = JvmTarget.JVM_21
    }
}

publishing.publications.withType<MavenPublication>().configureEach {
    pom.description = project.description
}
