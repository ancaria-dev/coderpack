// What a mod compiles against. No dependencies on purpose: a mod jar carries
// everything it needs, and every library in here would be one more thing to
// carry.
description = "The events and handles a Sacred Gold mod is written against"

dependencies {
    // compileOnly, so nullability is documented in the bytecode without the
    // annotation jar following the API into anybody's mod jar. javac keeps
    // class-file annotations it cannot resolve, so an IDE still reads them.
    compileOnly("com.google.code.findbugs:jsr305:3.0.2")
}

publishing.publications.withType<MavenPublication>().configureEach {
    pom.description = project.description
}
