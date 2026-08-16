// The JVM side: reads frames from the host, loads the mods, answers back.
description = "Loads Sacred Gold mods and connects them to the running game"

dependencies {
    api(project(":api"))

    testImplementation(platform("org.junit:junit-bom:5.10.1"))
    testImplementation("org.junit.jupiter:junit-jupiter-api")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}

publishing.publications.withType<MavenPublication>().configureEach {
    pom.description = project.description
}
