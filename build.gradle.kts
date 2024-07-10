plugins {
    id("org.jetbrains.kotlin.jvm") version "1.9.23"
    id("org.jetbrains.kotlin.plugin.allopen") version "1.9.23"
    id("com.google.devtools.ksp") version "1.9.23-1.0.19"
    id("com.github.johnrengelman.shadow") version "8.1.1"
    id("io.micronaut.application") version "4.4.0"
    id("io.micronaut.aot") version "4.4.0"
}

version = "0.1"
group = "com.whatever"

val kotlinVersion=project.properties.get("kotlinVersion")
repositories {
    mavenCentral()
    maven { url = uri("https://jitpack.io") }
    maven { url = uri("https://oss.sonatype.org/content/repositories/snapshots") }
}

dependencies {

    ksp("io.micronaut:micronaut-http-validation")
    ksp("io.micronaut.serde:micronaut-serde-processor")
    implementation ("com.github.walkyst:lavaplayer-fork:1.4.3")
    implementation ("ch.qos.logback:logback-classic:1.2.11") // Use the latest stable version
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("de.sciss:jump3r:1.0.5")
    implementation("dev.kord:kord-core:0.13.0")
    implementation("net.dv8tion:JDA:5.0.0-beta.24")
    implementation("com.aallam.openai:openai-client:3.6.2")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.15.2")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
    implementation("io.github.kotlin-telegram-bot.kotlin-telegram-bot:telegram:6.1.0")
    implementation("com.squareup.okhttp3:okhttp:4.9.3")
    implementation("io.micronaut:micronaut-jackson-databind")
    implementation("io.micronaut.kotlin:micronaut-kotlin-extension-functions")
    implementation("io.micronaut.kotlin:micronaut-kotlin-runtime")
    implementation("io.micronaut.serde:micronaut-serde-jackson")
    implementation("jakarta.annotation:jakarta.annotation-api")
    implementation("org.jetbrains.kotlin:kotlin-reflect:${kotlinVersion}")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8:${kotlinVersion}")
//    runtimeOnly("ch.qos.logback:logback-classic")
    runtimeOnly("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.3.2")
    runtimeOnly("org.yaml:snakeyaml")
}


application {
    mainClass = "com.whatever.ApplicationKt"
}
java {
    sourceCompatibility = JavaVersion.toVersion("21")
}


graalvmNative.toolchainDetection = false
micronaut {
    runtime("netty")
    testRuntime("kotest5")
    processing {
        incremental(true)
        annotations("com.whatever.*")
    }
    aot {
    // Please review carefully the optimizations enabled below
    // Check https://micronaut-projects.github.io/micronaut-aot/latest/guide/ for more details
        optimizeServiceLoading = false
        convertYamlToJava = false
        precomputeOperations = true
        cacheEnvironment = true
        optimizeClassLoading = true
        deduceEnvironment = true
        optimizeNetty = true
        replaceLogbackXml = true
    }
}

graalvmNative {
    agent{
        defaultMode = "direct"
        enabled = true
        modes {
            direct {
                options.add("config-merge-dir=build/native/agent-output/tmp")
            }
        }
    }
    toolchainDetection = false
    binaries.all {
        buildArgs.add("-H:+AddAllCharsets")
        buildArgs.add("-H:EnableURLProtocols=http,https")
        buildArgs.add("-H:NumberOfThreads=12")
        buildArgs.add("-J-Xmx24g")
        buildArgs.add("-R:MaxHeapSize=256m")
        buildArgs.add("--strict-image-heap")
        buildArgs.add("-Ob")

    }
}
tasks.named<io.micronaut.gradle.docker.NativeImageDockerfile>("dockerfileNative") {
    jdkVersion = "21"
}


