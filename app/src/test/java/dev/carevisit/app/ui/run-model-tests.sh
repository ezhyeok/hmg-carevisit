#!/usr/bin/env bash
# Pure Kotlin UI policy tests on the server. Does not configure AGP or require Android SDK.
set -euo pipefail
visit_repo=$(git -C "$(dirname "${BASH_SOURCE[0]}")" rev-parse --show-toplevel)
ui_test_dir=$(mktemp -d /tmp/carevisit-ui-tests.XXXXXX)
export CAREVISIT_UI_REPO="$visit_repo"
# Compile an unchanged source snapshot: parallel agents must not share visit-core/build.
mkdir -p "$ui_test_dir/visit-core/src"
cp -a "$visit_repo/visit-core/src/main" "$ui_test_dir/visit-core/src/"
cp "$visit_repo/visit-core/build.gradle.kts" "$visit_repo/visit-core/settings.gradle.kts" "$visit_repo/visit-core/gradle.properties" "$ui_test_dir/visit-core/"
cat > "$ui_test_dir/settings.gradle.kts" <<'KOTLIN'
pluginManagement { repositories { gradlePluginPortal(); mavenCentral() } }
dependencyResolutionManagement { repositories { mavenCentral() } }
rootProject.name = "carevisit-ui-model-tests"
includeBuild("visit-core")
KOTLIN
cat > "$ui_test_dir/build.gradle.kts" <<'KOTLIN'
plugins { kotlin("jvm") version "2.0.0" }
dependencies {
    implementation("dev.carevisit:visit-core:0.1.0")
    testImplementation("junit:junit:4.13.2")
}
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
java { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
val repo = System.getenv("CAREVISIT_UI_REPO")
sourceSets {
    main { kotlin.srcDir("$repo/app/src/main/java/dev/carevisit/app/ui"); kotlin.include("AppModels.kt") }
    test { kotlin.srcDir("$repo/app/src/test/java/dev/carevisit/app/ui") }
}
tasks.test { useJUnit(); testLogging { events("passed", "failed", "skipped") } }
KOTLIN
cat > "$ui_test_dir/gradle.properties" <<'PROPERTIES'
kotlin.compiler.execution.strategy=in-process
org.gradle.jvmargs=-Xmx1g
org.gradle.workers.max=2
PROPERTIES
"$visit_repo/gradlew" -p "$ui_test_dir" test --console=plain --no-watch-fs "$@"
printf 'UI model test report: %s/build/reports/tests/test/index.html\n' "$ui_test_dir"
