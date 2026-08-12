// Two published artifacts and nothing else. The agent is JavaScript and the
// tools are Python, so neither belongs to this build; they are copied into the
// payload by whoever assembles it.
dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

rootProject.name = "coderpack"

include("api", "zygote")
