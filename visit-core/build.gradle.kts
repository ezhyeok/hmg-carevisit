plugins {
    kotlin("jvm") version "2.0.0"
    kotlin("plugin.serialization") version "2.0.0"
}
group = "dev.carevisit"
version = "0.1.0"
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
java { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
dependencies {
    api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.1")
    testImplementation(kotlin("test-junit"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
}
tasks.test { useJUnit(); testLogging { events("failed", "skipped"); exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL } }

// The actual Android-free adapter sources and app tests can also run on this server.
// No Android stubs or SDK replacements are used; Activity/Compose/SDK implementations stay laptop-only.
val adaptersTest by sourceSets.creating
kotlin.sourceSets.getByName("adaptersTest").kotlin.apply {
    srcDir("../app/src/main/java")
    srcDir("../app/src/test/java")
    srcDir("../pleos-kit/src/main/java")
    include("dev/carevisit/app/VoiceAgent.kt", "dev/carevisit/app/UiContract.kt", "dev/carevisit/app/HttpFamilyLink.kt")
    include("dev/carevisit/app/VoiceAgentTest.kt", "dev/carevisit/app/StoreTest.kt", "dev/carevisit/app/ReleaseSurfaceTest.kt", "dev/carevisit/app/HttpFamilyLinkTest.kt")
    include("dev/hmg/pleoskit/SpeechContracts.kt")
    include("dev/carevisit/app/SecureWindowPolicy.kt", "dev/carevisit/app/SecureWindowPolicyTest.kt", "dev/carevisit/app/DemoPrivacyTest.kt")
    include("dev/carevisit/app/VisitRepositories.kt")
}
configurations[adaptersTest.implementationConfigurationName].extendsFrom(configurations.testImplementation.get())
configurations[adaptersTest.runtimeOnlyConfigurationName].extendsFrom(configurations.testRuntimeOnly.get())
dependencies {
    add(adaptersTest.implementationConfigurationName, sourceSets.main.get().output)
    add(adaptersTest.implementationConfigurationName, "org.json:json:20240303")
}
tasks.register<Test>("adapterTest") {
    description = "Runs the actual Android-free app adapter and release-surface tests without the Android SDK"
    testClassesDirs = adaptersTest.output.classesDirs
    classpath = adaptersTest.runtimeClasspath
    useJUnit()
    testLogging { events("failed", "skipped"); exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL }
}
