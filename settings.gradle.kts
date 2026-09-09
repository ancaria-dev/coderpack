// Three published artifacts and nothing else. The agent is JavaScript and the
// tools are Python, so neither belongs to this build; they are copied into the
// payload by whoever assembles it.
dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

rootProject.name = "coderpack"

// api-kotlin is the same API said in Kotlin and is published beside the other
// two. Unlike them the loader does not hand it to a mod: it is inline
// extensions over api, and a mod that wants it packs it.
include("api", "zygote", "api-kotlin")
