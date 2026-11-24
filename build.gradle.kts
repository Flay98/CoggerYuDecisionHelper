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
    implementation(files("lib/gson-2.13.2.jar"))
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

tasks.register<JavaExec>("runConsistency") {
    group = "application"
    description = "Analyze and correct a pairwise comparison matrix from CSV or JSON"
    dependsOn(tasks.classes)
    classpath = sourceSets["main"].runtimeClasspath
    mainClass = "MCD.consistency.cli.ConsistencyCli"
    javaLauncher = javaToolchains.launcherFor {
        languageVersion = JavaLanguageVersion.of(17)
    }
    systemProperty("file.encoding", "UTF-8")
}

tasks.register<JavaExec>("reproduceArticles") {
    group = "verification"
    description = "Reproduce the available article examples with a fixed, bounded experiment plan"
    dependsOn(tasks.classes)
    classpath = sourceSets["main"].runtimeClasspath
    mainClass = "MCD.consistency.reproduction.ArticleReproduction"
    javaLauncher = javaToolchains.launcherFor {
        languageVersion = JavaLanguageVersion.of(17)
    }
    systemProperty("file.encoding", "UTF-8")
}

// Heavy measurements are deliberately independent of build/test.
fun benchmark(profile: String, suffix: String, plots: Boolean) {
    val measurement = tasks.register<JavaExec>("runBenchmark$suffix") {
        group = "experiments"
        description = "Execute the fixed $profile comparison plan"
        dependsOn(tasks.classes)
        classpath = sourceSets["main"].runtimeClasspath
        mainClass = "MCD.consistency.experiments.BenchmarkRunner"
        args(profile)
        maxHeapSize = "512m"
        systemProperty("file.encoding", "UTF-8")
    }
    val plotting = if (plots) tasks.register<Exec>("plot$suffix") {
        group = "experiments"
        description = "Rebuild $profile figures from saved measurements only"
        val localPython = file("experiments/comparison/.venv/bin/python")
        commandLine(if (localPython.exists()) localPython.absolutePath else "python3",
            "experiments/comparison/scripts/plot_results.py", "--profile", profile)
    } else null
    plotting?.configure { mustRunAfter(measurement) }
    tasks.register("benchmark$suffix") {
        group = "experiments"
        description = "Execute $profile measurements" + if (plots) " and plot their results" else ""
        dependsOn(measurement)
        if (plotting != null) {
            dependsOn(plotting)
        }
    }
}

benchmark("SMOKE", "Smoke", true)
benchmark("PILOT", "Pilot", false)
benchmark("REPORT", "Report", true)
benchmark("FULL", "Full", true)

tasks.register<JavaExec>("verifyComparison") {
    group = "experiments"
    description = "Reload and verify saved REPORT matrices and aggregates without optimization"
    dependsOn(tasks.classes)
    classpath = sourceSets["main"].runtimeClasspath
    mainClass = "MCD.consistency.experiments.BenchmarkVerifier"
    args("REPORT")
    maxHeapSize = "512m"
}

tasks.register<JavaExec>("runDecision") {
    group = "application"
    description = "Compare alternative rankings before and after three independent PCM corrections"
    dependsOn(tasks.classes)
    classpath = sourceSets["main"].runtimeClasspath
    mainClass = "MCD.consistency.decision.DecisionCli"
    maxHeapSize = "512m"
    systemProperty("file.encoding", "UTF-8")
}

fun kurennykhTask(taskName: String, entry: String, profile: String) = tasks.register<JavaExec>(taskName) {
    group = "experiments"
    description = "Kurennykh 2018 approximate reproduction: $profile / $entry"
    dependsOn(tasks.classes)
    classpath = sourceSets["main"].runtimeClasspath
    mainClass = "MCD.consistency.reproduction.kurennykh.$entry"
    args(profile)
    maxHeapSize = "512m"
    systemProperty("file.encoding", "UTF-8")
}
kurennykhTask("reproduceKurennykhSmoke", "KurennykhReproduction", "SMOKE")
kurennykhTask("reproduceKurennykh2018", "KurennykhReproduction", "REPORT")
kurennykhTask("verifyKurennykh2018", "KurennykhVerifier", "REPORT")
kurennykhTask("compareKurennykh2018", "KurennykhComparison", "REPORT")
tasks.register<Exec>("plotKurennykh2018") {
    group = "experiments"
    description = "Plot saved Kurennykh results without running the correction algorithm"
    val python = file("experiments/comparison/.venv/bin/python")
    commandLine(if (python.exists()) python.absolutePath else "python3", "experiments/kurennykh2018/scripts/plot_results.py")
}
