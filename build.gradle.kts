plugins {
    application
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

// Keep the original layout and IntelliJ IDEA module working without moving sources.
sourceSets {
    main {
        java.setSrcDirs(listOf("src"))
    }
    test {
        java.setSrcDirs(listOf("test"))
    }
}

dependencies {
    // JAMA is already included as Java sources in src/Jama.
    testImplementation(files("lib/junit-4.13.1.jar", "lib/hamcrest-core-1.3.jar"))
}

application {
    mainClass = "MCD.Main"
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release = 17
}

tasks.test {
    useJUnit()
    // The original tests use Java assert statements rather than JUnit assertions.
    enableAssertions = true
    systemProperty("file.encoding", "UTF-8")
}

tasks.named<JavaExec>("run") {
    // Gradle otherwise supplies an empty input stream to the console application.
    standardInput = System.`in`
    systemProperty("file.encoding", "UTF-8")
}
