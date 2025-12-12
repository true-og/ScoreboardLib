/* This is free and unencumbered software released into the public domain */

import org.gradle.kotlin.dsl.provideDelegate

/* ------------------------------ Plugins ------------------------------ */
plugins {
    id("java") // Import Java plugin.
    id("java-library") // Import Java Library plugin.
    id("com.diffplug.spotless") version "8.1.0" // Import Spotless plugin.
    id("com.gradleup.shadow") version "8.3.9" // Import Shadow plugin.
    id("checkstyle") // Import Checkstyle plugin.
    eclipse // Import Eclipse plugin.
    kotlin("jvm") version "2.1.21" // Import Kotlin JVM plugin.
}

/* --------------------------- JDK / Kotlin ---------------------------- */
java {
    sourceCompatibility = JavaVersion.VERSION_17 // Compile with JDK 17 compatibility.
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17)) // Use JDK 17.
        vendor.set(JvmVendorSpec.GRAAL_VM) // Use GraalVM CE.
    }
}

kotlin { jvmToolchain(17) }

/* ----------------------------- Metadata ------------------------------ */
group = "me.tigerhix.lib" // From pom.xml

version = "1.1.0-SNAPSHOT" // From pom.xml

val apiVersion = "1.19" // Minecraft API version (used for plugin.yml if present).

/* ----------------------------- Resources ----------------------------- */
tasks.named<ProcessResources>("processResources") {
    // Emulate Maven resource filtering for .yml and .properties
    val props = mapOf("version" to version, "apiVersion" to apiVersion)
    inputs.properties(props)

    filesMatching("**/*.yml") { expand(props) }
    filesMatching("**/*.properties") { expand(props) }

    // Keep your template behavior of bundling LICENSE if present
    from("LICENSE") { into("/") }
}

/* ---------------------------- Repos ---------------------------------- */
repositories {
    mavenCentral()
    gradlePluginPortal()

    // Purpur snapshots (for Purpur API 1.19.4)
    maven { url = uri("https://repo.purpurmc.org/snapshots") }

    // From pom.xml
    maven {
        name = "funkemunky-releases"
        url = uri("https://nexus.funkemunky.cc/content/repositories/releases/")
    }
    maven {
        name = "papermc"
        url = uri("https://repo.papermc.io/repository/maven-public/")
    }
    // Snapshot repo from distributionManagement (useful if you ever publish snapshots from Gradle)
    maven {
        name = "funkemunky-snapshots"
        url = uri("https://nexus.funkemunky.cc/content/repositories/snapshots")
    }

    // Local Maven caches (template behavior)
    maven { url = uri("file://${System.getProperty("user.home")}/.m2/repository") }
    System.getProperty("SELF_MAVEN_LOCAL_REPO")?.let { path ->
        val dir = file(path)
        if (dir.isDirectory) {
            println("Using SELF_MAVEN_LOCAL_REPO at: $path")
            maven { url = uri("file://${dir.absolutePath}") }
        } else {
            logger.error("TrueOG Bootstrap not found, defaulting to ~/.m2 for mavenLocal()")
            mavenLocal()
        }
    }
        ?: run {
            logger.error("TrueOG Bootstrap not found, defaulting to ~/.m2 for mavenLocal()")
            mavenLocal()
        }
}

/* ---------------------- Java project deps ---------------------------- */
val purpurApiVersion = "1.19.4-R0.1-SNAPSHOT"

dependencies {
    // Use Purpur 1.19.4 as requested
    compileOnly("org.purpurmc.purpur:purpur-api:$purpurApiVersion")

    // Pulled from pom.xml (kept as compileOnly like Maven's <scope>provided</scope>)
    compileOnly("io.papermc.paper:paper-api:1.19.4-R0.1-SNAPSHOT")
    compileOnly("org.github.paperspigot:1.19.4:1.19.4")
}

/* ---------------------- Reproducible jars ---------------------------- */
tasks.withType<AbstractArchiveTask>().configureEach {
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}

/* ----------------------------- Shadow -------------------------------- */
tasks.shadowJar {
    // Produce a single shaded plugin/library jar as the main artifact
    archiveClassifier.set("")
    // No relocations/minimize by default; you can add them later if you start shading deps
}

tasks.jar {
    // Let shadowJar produce the main artifact; disable the plain jar
    enabled = false
}

tasks.build { dependsOn(tasks.shadowJar) }

/* --------------------------- Javac opts ------------------------------- */
tasks.withType<JavaCompile>().configureEach {
    // From your template + Maven compiler plugin behavior
    options.encoding = "UTF-8"
    options.isFork = true

    // Maven had -Xlint:all and -Xlint:-path, plus showWarnings/showDeprecation
    options.compilerArgs.addAll(listOf("-parameters", "-Xlint:all", "-Xlint:-path", "-Xlint:deprecation"))
}

/* ----------------------------- Auto Formatting ------------------------ */
spotless {
    java {
        eclipse().configFile("config/formatter/eclipse-java-formatter.xml")
        leadingTabsToSpaces()
        removeUnusedImports()
    }
    kotlinGradle {
        ktfmt().kotlinlangStyle().configure { it.setMaxWidth(120) }
        target("build.gradle.kts", "settings.gradle.kts")
    }
}

checkstyle {
    toolVersion = "10.18.1"
    configFile = file("config/checkstyle/checkstyle.xml")
    isIgnoreFailures = true
    isShowViolations = true
}

tasks.named("compileJava") { dependsOn("spotlessApply") }

tasks.named("spotlessCheck") { dependsOn("spotlessApply") }
