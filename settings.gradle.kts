pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google(); mavenCentral()
        maven { url = uri("https://nexus-playground.pleos.ai/repository/maven-releases/") }
    }
}
rootProject.name = "visit"
rootDir.parentFile?.resolve("gradle-home")?.takeIf { it.isDirectory }?.let { projectHome ->
    if (gradle.gradleUserHomeDir.canonicalFile != projectHome.canonicalFile)
        throw GradleException("tools/laptop/gradle.ps1로 실행하세요. GRADLE_USER_HOME=$projectHome 필요")
}
includeBuild("visit-core")
include(":pleos-kit", ":app")
