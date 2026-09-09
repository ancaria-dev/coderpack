import org.gradle.plugins.signing.SigningExtension

plugins {
    `java-library`
    `maven-publish`
    // Uploads what `maven-publish` already produced to the Central Portal, as
    // one signed bundle. It creates no publications of its own, which is why
    // the blocks below are unchanged from when this published nowhere.
    id("com.gradleup.nmcp.aggregation") version "1.6.2"
    // Applied by :api-kotlin, not here: the root and the other two modules are
    // Java. Declared at this level because a version is only allowed to be
    // named once in a build, and the number is the one the scaffolder writes
    // into a generated Kotlin mod.
    kotlin("jvm") version "2.4.10" apply false
}

// All three modules are published and describe themselves the same way. Keeping
// that here rather than duplicating it three times means the coordinates and the
// licence cannot drift apart between them.
// The signing key, read once here rather than in each subproject. Absent on a
// developer's machine, which is the point of asking a provider rather than
// requiring it: `publishToMavenLocal` has to keep working with no key at all.
val signingKey = providers.environmentVariable("SIGNING_KEY")
val signingPassword = providers.environmentVariable("SIGNING_PASSWORD")

subprojects {
    apply(plugin = "java-library")
    apply(plugin = "maven-publish")
    apply(plugin = "signing")
    // Offers this project's publications to the aggregation at the root.
    apply(plugin = "com.gradleup.nmcp")

    group = rootProject.group
    version = rootProject.version

    // --release rather than a toolchain: a toolchain that is not installed
    // sends Gradle off to download a JDK, and the class files have to run on
    // whatever JVM the player already has anyway.
    tasks.withType<JavaCompile>().configureEach {
        options.release = 21
        options.encoding = "UTF-8"
    }

    // The javadoc jar exists so an IDE can show the doc comments, not so a
    // doclint run can list every getter that has none. For :api-kotlin it comes
    // out empty, because javadoc reads Java; the KDoc travels in the sources jar
    // beside it, which is what an IDE opens anyway. Central refuses a
    // publication without the file, not without content in it.
    tasks.withType<Javadoc>().configureEach {
        (options as StandardJavadocDocletOptions).addStringOption("Xdoclint:none", "-quiet")
    }

    extensions.configure<JavaPluginExtension> {
        withSourcesJar()
        withJavadocJar()
    }

    extensions.configure<PublishingExtension> {
        publications {
            create<MavenPublication>("maven") {
                from(components["java"])
                pom {
                    name = project.name
                    url = "https://github.com/ancaria-dev/coderpack"
                    licenses {
                        license {
                            name = "MIT"
                            url = "https://opensource.org/licenses/MIT"
                        }
                    }
                    developers {
                        developer {
                            id = "mairwunnx"
                            name = "MairwunNx (Pavel Erokhin)"
                            url = "https://ancaria.dev"
                        }
                    }
                    scm {
                        url = "https://github.com/ancaria-dev/coderpack"
                        connection = "scm:git:https://github.com/ancaria-dev/coderpack.git"
                        developerConnection =
                            "scm:git:ssh://git@github.com/ancaria-dev/coderpack.git"
                    }
                }
            }
        }
        // No repository block. Maven Central is not a repository a publish task
        // writes to. The Portal takes one signed bundle over its own API, and
        // that is what the aggregation at the root does. `publishToMavenLocal`
        // still works and is what a mod on this machine resolves.
    }

    // Central refuses an unsigned artifact. Without a key the sign tasks are
    // skipped rather than failing, so a checkout with no secrets still builds,
    // tests and publishes to the local repository.
    extensions.configure<SigningExtension> {
        isRequired = signingKey.isPresent
        if (signingKey.isPresent) {
            useInMemoryPgpKeys(signingKey.get(), signingPassword.get())
        }
        sign(extensions.getByType<PublishingExtension>().publications)
    }
}

// --- what goes to Maven Central ------------------------------------------

dependencies {
    nmcpAggregation(project(":api"))
    nmcpAggregation(project(":zygote"))
    nmcpAggregation(project(":api-kotlin"))
}

nmcpAggregation {
    centralPortal {
        username = providers.environmentVariable("CENTRAL_USERNAME")
        password = providers.environmentVariable("CENTRAL_PASSWORD")

        // The upload is automatic; the release is one click in the portal.
        // Central artifacts cannot be deleted, ever, so the first few releases
        // are worth looking at before they are permanent. Change this to
        // "AUTOMATIC" once the shape of a deployment is known to be right, and
        // raising a version number is again the only thing that ships.
        publishingType = "USER_MANAGED"
        publicationName = "${project.group}:${project.version}"
    }
}
