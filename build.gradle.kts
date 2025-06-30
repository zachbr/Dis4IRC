import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import java.io.ByteArrayOutputStream
import org.gradle.api.JavaVersion
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    // Kotlin 2.1.20 causes build failures on riscv64 and other archs
    // see GH-86.
    kotlin("jvm") version "2.1.10"

    id("org.cadixdev.licenser") version "0.6.1"
    id("com.gradleup.shadow") version "8.3.5"
}

group = "io.zachbr"
version = "1.7.0-SNAPSHOT"

val targetJVM = JavaVersion.VERSION_11.toString()

repositories {
    maven("https://m2.dv8tion.net/releases")
    maven("https://repo.spongepowered.org/maven")
    mavenCentral()
}

dependencies {
    implementation("org.kitteh.irc:client-lib:9.0.0")
    implementation("club.minnced:discord-webhooks:0.8.4")
    implementation("net.dv8tion:JDA:5.6.1") {
        exclude(module = "opus-java")
        exclude(module = "tink")
    }

    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    implementation("org.json:json:20250107")
    implementation("org.spongepowered:configurate-hocon:4.2.0")
    implementation("org.commonmark:commonmark:0.24.0")
    implementation("org.commonmark:commonmark-ext-gfm-strikethrough:0.24.0")

    implementation("org.slf4j:slf4j-api:2.0.17")
    implementation("ch.qos.logback:logback-core:1.5.18")
    implementation("ch.qos.logback:logback-classic:1.5.18")

    testImplementation(platform("org.junit:junit-bom:5.12.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
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
        compilerOptions {
            jvmTarget.set(JvmTarget.fromTarget(targetJVM))
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
