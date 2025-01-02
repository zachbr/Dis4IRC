import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import java.io.ByteArrayOutputStream
import org.gradle.api.JavaVersion
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "2.0.20"

    id("org.cadixdev.licenser") version "0.6.1"
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

group = "io.zachbr"
version = "1.6.5-SNAPSHOT"

val targetJVM = JavaVersion.VERSION_11.toString()

repositories {
    maven("https://m2.dv8tion.net/releases")
    maven("https://repo.spongepowered.org/maven")
    mavenCentral()
}

dependencies {
    implementation("org.kitteh.irc:client-lib:9.0.0")
    implementation("club.minnced:discord-webhooks:0.8.4")
    implementation("net.dv8tion:JDA:5.2.1") {
        exclude(module = "opus-java")
    }

    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    implementation("org.json:json:20230227")
    implementation("org.spongepowered:configurate-hocon:4.1.2")
    implementation("com.atlassian.commonmark:commonmark:0.15.2")
    implementation("com.atlassian.commonmark:commonmark-ext-gfm-strikethrough:0.15.2")

    implementation("org.slf4j:slf4j-api:2.0.13")
    implementation("ch.qos.logback:logback-core:1.5.6")
    implementation("ch.qos.logback:logback-classic:1.5.6")

    testImplementation("org.junit.jupiter:junit-jupiter-api:5.10.0")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.10.0")
}

tasks {
    build {
        dependsOn(shadowJar)
    }

    withType<ShadowJar> {
        manifest {
            attributes["Main-Class"] = "io.zachbr.dis4irc.Dis4IRCKt"
            attributes["Multi-Release"] = "true"
        }

        from(file("LICENSE.md"))
        archiveClassifier.set("")
        isPreserveFileTimestamps = false
        isReproducibleFileOrder = true
    }

    // make compileKotlin task be quiet
    withType<JavaCompile> {
        targetCompatibility = targetJVM
    }

    withType<KotlinCompile> {
        kotlinOptions {
            jvmTarget = targetJVM
        }
    }

    processResources {
        inputs.property("suffix", project.findProperty("suffix") ?: "") // track suffix value as input
        expand(
            "projectName" to rootProject.name,
            "projectVersion" to version,
            "projectSuffix" to getSuffix(),
            "projectSourceRepo" to "https://github.com/zachbr/Dis4IRC"
        )
    }

    test {
        testLogging.showStandardStreams = true
        useJUnitPlatform()
    }
}

// updateLicenses | checkLicenses
license {
    header(project.file("HEADER.txt"))
    ext {
        set("name", "Dis4IRC")
        set("year", "2018-2024")
    }
}

fun getSuffix(): String {
    // If suffix was specified at build-time, use that.
    // ./gradlew build -P suffix="suffixValue15"
    project.findProperty("suffix")?.toString()?.let { return it }

    // Fall back to git hash if suffix not set
    return ByteArrayOutputStream().let { stdout ->
        runCatching {
            exec {
                commandLine("git", "rev-parse", "--short", "HEAD")
                standardOutput = stdout
                errorOutput = ByteArrayOutputStream()
                isIgnoreExitValue = true
            }
            stdout.toString().trim().takeIf { it.isNotEmpty() }
        }.getOrNull() ?: "0"  // fall back to 0 if git falls through
    }
}
