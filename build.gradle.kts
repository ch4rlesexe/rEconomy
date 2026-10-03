plugins {
    java
    id("com.gradleup.shadow") version "9.4.3"
}

group = "net.ch4rlesexe"
version = "1.9.16"

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://jitpack.io")
    maven("https://repo.extendedclip.com/content/repositories/placeholderapi/")
}

val paperApi = "io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT"

sourceSets {
    create("zheadStubs") {
        java.setSrcDirs(listOf("stubs/zhead"))
    }
}

dependencies {
    "zheadStubsCompileOnly"(paperApi)

    compileOnly(paperApi)
    compileOnly("com.github.MilkBowl:VaultAPI:1.7.1")
    compileOnly(sourceSets["zheadStubs"].output)
    compileOnly("me.clip:placeholderapi:2.11.6")
    implementation("com.zaxxer:HikariCP:5.1.0")
    implementation("com.mysql:mysql-connector-j:8.3.0")
    implementation("com.google.code.gson:gson:2.11.0")
}

tasks {
    compileJava {
        dependsOn(named("zheadStubsClasses"))
    }
    processResources {
        val props = mapOf("version" to version)
        filesMatching("plugin.yml") {
            expand(props)
        }
    }
    shadowJar {
        archiveClassifier.set("")
        archiveFileName.set("rEconomy.jar")
        relocate("com.zaxxer.hikari", "net.ch4rlesexe.economy.lib.hikari")
        relocate("com.mysql", "net.ch4rlesexe.economy.lib.mysql")
        relocate("com.google.gson", "net.ch4rlesexe.economy.lib.gson")
    }
    build {
        dependsOn(shadowJar)
    }
    withType<JavaCompile> {
        options.encoding = "UTF-8"
    }
}
