plugins {
    id("net.fabricmc.fabric-loom")
    id("me.modmuss50.mod-publish-plugin")
}

version = "${property("mod.version")}+${sc.current.version}"
base.archivesName = property("mod.id") as String

fun File.readDotEnv(): Map<String, String> {
    if (!isFile) return emptyMap()

    return readLines()
        .asSequence()
        .map(String::trim)
        .filter { it.isNotEmpty() && !it.startsWith("#") }
        .mapNotNull { line ->
            val separator = line.indexOf('=')
            if (separator <= 0) return@mapNotNull null

            val key = line.substring(0, separator).trim()
            val value = line.substring(separator + 1).trim()
                .removeSurrounding("\"")
                .removeSurrounding("'")

            key to value
        }
        .associate { it }
}

fun configuredProperty(name: String): String? =
    providers.gradleProperty(name).orNull
        ?.trim()
        ?.takeUnless { it.isEmpty() || it.startsWith("#") }

val dotenv = rootProject.file(".env").readDotEnv()
val modVersion = property("mod.version") as String
val modName = property("mod.name") as String
val modType = configuredProperty("mod.type")
    ?: throw GradleException("Missing mod.type. Expected one of: ALPHA, BETA, STABLE.")
val modrinthProjectId = configuredProperty("publish.modrinth")
val curseforgeProjectId = configuredProperty("publish.curseforge")
val modrinthToken = System.getenv("MODRINTH_TOKEN")?.trim()?.takeIf { it.isNotEmpty() }
    ?: dotenv["MODRINTH_TOKEN"]?.trim()?.takeIf { it.isNotEmpty() }
val curseforgeToken = System.getenv("CURSEFORGE_TOKEN")?.trim()?.takeIf { it.isNotEmpty() }
    ?: dotenv["CURSEFORGE_TOKEN"]?.trim()?.takeIf { it.isNotEmpty() }
val releaseNotes = rootProject.file("MODRINTH.md")
    .takeIf(File::isFile)
    ?.readText()
    ?.trim()
    ?.takeIf(String::isNotEmpty)
    ?: rootProject.file("CHANGELOG.md")
        .takeIf(File::isFile)
        ?.readText()
        ?.trim()
        ?.takeIf(String::isNotEmpty)
    ?: "$modName $modVersion"

val requiredJava = JavaVersion.VERSION_25
val minecraftTitle = project.property("mod.mc_title") as String
val targetMinecraftVersions = (project.property("mod.mc_targets") as String)
    .split(Regex("\\s+"))
    .filter(String::isNotBlank)
val devApiBaseUrl = providers.gradleProperty("devApiBaseUrl").orElse("http://localhost:8081")
val devApiProperties = layout.buildDirectory.file("generated/dev-api/marketguard-api.properties")

val releaseType = try {
    me.modmuss50.mpp.ReleaseType.of(modType.uppercase())
} catch (_: IllegalArgumentException) {
    throw GradleException("Invalid mod.type '$modType'. Expected one of: ALPHA, BETA, STABLE.")
}

repositories {
    mavenCentral()

    flatDir {
        name = "LocalTangosHudLib"
        dirs(rootProject.file("../TangosHudLib/versions/${sc.current.version}/build/libs"))
    }

    /**
     * Restricts dependency search of the given [groups] to the [maven URL][url],
     * improving the setup speed.
     */
    fun strictMaven(url: String, alias: String, vararg groups: String) = exclusiveContent {
        forRepository { maven(url) { name = alias } }
        filter { groups.forEach(::includeGroup) }
    }
    strictMaven("https://www.cursemaven.com", "CurseForge", "curse.maven")
    strictMaven("https://api.modrinth.com/maven", "Modrinth", "maven.modrinth")
}

dependencies {
    val scamscreenerVersion = "2.2.0+26.1"
    val localHudLibVersion = "1.2.0+${sc.current.version}"
    val midnightLibVersion = when (sc.current.version) {
        "26.1.2" -> "1.9.3+26.1-fabric"
        "26.2" -> "1.9.3+26.2-fabric"
        "26.3" -> "1.9.3+26.3-fabric"
        else -> throw GradleException("Unsupported MidnightLib target: ${sc.current.version}")
    }
    // TODO: Replace this temporary local Jar-in-Jar source with the HudLib Modrinth Maven dependency.
    val localHudLibJar = rootProject.file(
        "../TangosHudLib/versions/${sc.current.version}/build/libs/tangoshudlib-1.2.0+${sc.current.version}.jar"
    )

    if (!localHudLibJar.isFile) {
        throw GradleException("Missing local Tango's HudLib build: ${localHudLibJar.path}")
    }

    // deps.minecraft lets a target build against a pre-release until the final version is published.
    minecraft("com.mojang:minecraft:${findProperty("deps.minecraft") ?: sc.current.version}")
    implementation("net.fabricmc:fabric-loader:${property("deps.fabric_loader")}")
    implementation("net.fabricmc.fabric-api:fabric-api:${property("deps.fabric_api")}")
    compileOnly("org.projectlombok:lombok:${property("deps.lombok")}")
    annotationProcessor("org.projectlombok:lombok:${property("deps.lombok")}")
    testImplementation(platform("org.junit:junit-bom:5.12.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("org.mockito:mockito-core:5.17.0")
    compileOnly("maven.modrinth:scamscreener:$scamscreenerVersion")
    implementation("local.tango:tangoshudlib:$localHudLibVersion")
    include("local.tango:tangoshudlib:$localHudLibVersion")
    implementation("maven.modrinth:midnightlib:$midnightLibVersion")
    include("maven.modrinth:midnightlib:$midnightLibVersion")
    implementation("org.xerial:sqlite-jdbc:3.46.1.0")
    include("org.xerial:sqlite-jdbc:3.46.1.0")
}

loom {
    fabricModJsonPath = rootProject.file("src/main/resources/fabric.mod.json") // Useful for interface injection
    accessWidenerPath = rootProject.file("src/main/resources/marketguard.accesswidener")

    decompilerOptions.named("vineflower") {
        options.put("mark-corresponding-synthetics", "1") // Adds names to lambdas - useful for mixins
    }

    runConfigs.all {
        ideConfigGenerated(true)
        vmArgs("-Dmixin.debug.export=true") // Exports transformed classes for debugging
        runDir = "../../run" // Shares the run directory between versions
    }
}

java {
    withSourcesJar()
    toolchain.languageVersion = JavaLanguageVersion.of(25)
    targetCompatibility = requiredJava
    sourceCompatibility = requiredJava
}

publishMods {
    file.set(project.tasks.named("jar", org.gradle.jvm.tasks.Jar::class.java).flatMap { it.archiveFile })
    changelog.set(releaseNotes)
    displayName.set("$modName $modVersion ($minecraftTitle)")
    type.set(releaseType)
    modLoaders.add("fabric")

    modrinth {
        modrinthProjectId?.let(projectId::set)
        modrinthToken?.let(accessToken::set)
        minecraftVersions.addAll(targetMinecraftVersions)
        embeds("midnightlib")
        embeds("dynamic-hudlib")
        requires("fabric-api")
    }

    curseforge {
        curseforgeProjectId?.let(projectId::set)
        curseforgeToken?.let(accessToken::set)
        minecraftVersions.addAll(targetMinecraftVersions)
        javaVersions.add(requiredJava)
        clientRequired = true
        serverRequired = false
        embeds("midnightlib")
        requires("fabric-api")
    }
}

// A target built against a pre-release (deps.minecraft) cannot be uploaded: neither platform lists its release tag yet.
if (findProperty("deps.minecraft") != null) {
    tasks.named("publishModrinth") { enabled = false }
    tasks.named("publishCurseforge") { enabled = false }
}

tasks {
    val generateDevApiProperties = register<org.gradle.api.tasks.WriteProperties>("generateDevApiProperties") {
        destinationFile.set(devApiProperties)
        property("baseUrl", devApiBaseUrl.get())
    }
    val releaseJar = named<org.gradle.jvm.tasks.Jar>("jar")
    val devManifest = layout.buildDirectory.file("generated/dev-manifest/META-INF/MANIFEST.MF")
    val extractDevManifest = register<org.gradle.api.tasks.Copy>("extractDevManifest") {
        dependsOn(releaseJar)
        from(releaseJar.map { zipTree(it.archiveFile) }) {
            include("META-INF/MANIFEST.MF")
        }
        into(layout.buildDirectory.dir("generated/dev-manifest"))
    }

    register<org.gradle.jvm.tasks.Jar>("devJar") {
        group = "build"
        description = "Builds an installable development JAR that uses the local MarketGuard API."

        dependsOn(releaseJar, extractDevManifest, generateDevApiProperties)
        archiveClassifier.set("dev")
        from(releaseJar.map { zipTree(it.archiveFile) }) {
            exclude("META-INF/MANIFEST.MF")
        }
        from(devApiProperties)
        manifest.from(devManifest)
    }

    withType<org.gradle.api.tasks.testing.Test>().configureEach {
        useJUnitPlatform()
        jvmArgs("-Dnet.bytebuddy.experimental=true", "-XX:+EnableDynamicAgentLoading")
        testLogging {
            events(
                org.gradle.api.tasks.testing.logging.TestLogEvent.PASSED,
                org.gradle.api.tasks.testing.logging.TestLogEvent.SKIPPED,
                org.gradle.api.tasks.testing.logging.TestLogEvent.FAILED
            )
        }
    }

    processResources {
        val props = mapOf(
            "id" to project.property("mod.id"),
            "name" to project.property("mod.name"),
            "version" to project.property("mod.version"),
            "loader_version" to project.property("deps.fabric_loader"),
            "minecraft" to project.property("mod.mc_dep")
        )
        inputs.properties(props)

        filesMatching("fabric.mod.json") { expand(props) }

        val mixinJava = "JAVA_${requiredJava.majorVersion}"
        filesMatching("mixins.marketguard.json") { expand("java" to mixinJava) }
    }

    // Builds the version into a shared folder in `build/libs/${mod version}/`
    register<Copy>("buildAndCollect") {
        group = "build"
        from(
            project.tasks.named("jar", org.gradle.jvm.tasks.Jar::class.java).flatMap { it.archiveFile },
            project.tasks.named("sourcesJar", org.gradle.jvm.tasks.Jar::class.java).flatMap { it.archiveFile }
        )
        into(rootProject.layout.buildDirectory.file("libs/${project.property("mod.version")}"))
        dependsOn("build")
    }
}
