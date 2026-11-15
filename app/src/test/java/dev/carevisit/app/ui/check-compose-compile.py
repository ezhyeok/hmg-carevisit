#!/usr/bin/env python3
"""Compile every UI source against the app's real Compose artifacts and Android API jar.

Uses Kotlin + the Compose compiler in an isolated JVM Gradle build, without AGP/aapt.
This catches syntax, imports, lambda types and Compose API errors; it does NOT build
an APK or verify the Activity, Android resources, SDK adapters or device rendering.
Only the generated R.string.app_name ID is a stand-in for Android resource processing.
"""
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path
import re
import shutil
import subprocess
import tempfile
import urllib.request
import xml.etree.ElementTree as ET
import zipfile

REPO = next(p for p in Path(__file__).resolve().parents if (p / "gradlew").is_file())
APP = (REPO / "app/build.gradle.kts").read_text(encoding="utf-8")
ROOT = (REPO / "build.gradle.kts").read_text(encoding="utf-8")
BOM = re.search(r'compose-bom:([^"]+)', APP).group(1)
KOTLIN = re.search(r'id\("org.jetbrains.kotlin.plugin.compose"\) version "([^"]+)"', ROOT).group(1)
SDK = re.search(r'compileSdk\s*=\s*(\d+)', APP).group(1)
WORK = Path(tempfile.mkdtemp(prefix="carevisit-compose-check."))
CACHE = Path(tempfile.gettempdir()) / "carevisit-compose-api-cache"
CACHE.mkdir(exist_ok=True)
API = WORK / "api"
API.mkdir()
GOOGLE = "https://dl.google.com/dl/android/maven2/"


def download(url, name):
    target = CACHE / name
    if not target.exists():
        with urllib.request.urlopen(url, timeout=90) as response:
            data = response.read()
        with tempfile.NamedTemporaryFile(dir=CACHE, delete=False) as temporary:
            temporary.write(data)
            stage = Path(temporary.name)
        stage.replace(target)
    return target


def prepare():
    bom = ET.parse(download(GOOGLE + f"androidx/compose/compose-bom/{BOM}/compose-bom-{BOM}.pom", f"bom-{BOM}.pom"))
    ns = {"m": "http://maven.apache.org/POM/4.0.0"}
    versions = {
        (d.findtext("m:groupId", namespaces=ns), d.findtext("m:artifactId", namespaces=ns)): d.findtext("m:version", namespaces=ns)
        for d in bom.findall(".//m:dependency", ns)
    }
    artifacts = {
        "ui": ["ui", "ui-unit", "ui-geometry", "ui-graphics", "ui-text", "ui-util", "ui-tooling-preview"],
        "foundation": ["foundation", "foundation-layout"],
        "runtime": ["runtime", "runtime-saveable"],
        "animation": ["animation", "animation-core"],
        "material": ["material-ripple"],
        "material3": ["material3"],
    }

    def extract_artifact(pair):
        group, name = pair
        version = versions[(f"androidx.compose.{group}", name)]
        filename = f"{name}-android-{version}.aar"
        aar = download(GOOGLE + f"androidx/compose/{group}/{name}-android/{version}/{filename}", filename)
        with zipfile.ZipFile(aar) as archive:
            (API / f"{name}.jar").write_bytes(archive.read("classes.jar"))
        return f"{name} {version}"

    with ThreadPoolExecutor(max_workers=6) as pool:
        for result in pool.map(extract_artifact, [(group, name) for group, names in artifacts.items() for name in names]):
            print(result, flush=True)

    metadata = ET.parse(download("https://dl.google.com/android/repository/repository2-1.xml", "repository2-1.xml"))
    platform = next(p for p in metadata.iter("remotePackage") if p.get("path") == f"platforms;android-{SDK}")
    platform_url = next(platform.iter("url")).text
    sdk_zip = download("https://dl.google.com/android/repository/" + platform_url, platform_url)
    with zipfile.ZipFile(sdk_zip) as archive:
        jar = next(n for n in archive.namelist() if n.endswith("/android.jar"))
        (API / "android.jar").write_bytes(archive.read(jar))
    print(f"Android API {SDK}; Kotlin/Compose compiler {KOTLIN}", flush=True)


def main():
    prepare()
    # Isolate core outputs from CORE's concurrent tests; use its actual, unmodified sources.
    core = WORK / "visit-core"
    shutil.copytree(REPO / "visit-core/src/main", core / "src/main")
    for filename in ("build.gradle.kts", "settings.gradle.kts", "gradle.properties"):
        shutil.copy2(REPO / "visit-core" / filename, core / filename)
    (WORK / "settings.gradle.kts").write_text('''
pluginManagement { repositories { gradlePluginPortal(); mavenCentral() } }
dependencyResolutionManagement { repositories { mavenCentral() } }
rootProject.name = "carevisit-compose-source-check"
includeBuild("visit-core")
''')
    (WORK / "build.gradle.kts").write_text('''
plugins {
    kotlin("jvm") version "%s"
    id("org.jetbrains.kotlin.plugin.compose") version "%s"
}
dependencies {
    implementation("dev.carevisit:visit-core:0.1.0")
    implementation(files(fileTree("api") { include("*.jar") }))
}
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
java { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
sourceSets.main {
    kotlin.srcDir(System.getenv("CAREVISIT_UI_REPO") + "/app/src/main/java")
    kotlin.include("dev/carevisit/app/ui/**/*.kt", "dev/carevisit/app/UiContract.kt", "dev/carevisit/app/AppIdentity.kt")
}
''' % (KOTLIN, KOTLIN))
    (WORK / "gradle.properties").write_text("kotlin.compiler.execution.strategy=in-process\norg.gradle.jvmargs=-Xmx1g\norg.gradle.workers.max=2\n")
    resource = WORK / "src/main/java/dev/carevisit/app/R.java"
    resource.parent.mkdir(parents=True)
    strings = ET.parse(REPO / "app/src/main/res/values/strings.xml")
    assert strings.find(".//string[@name='app_name']") is not None
    resource.write_text("package dev.carevisit.app; public final class R { public static final class string { public static final int app_name = 1; } }\n")
    import os
    env = dict(os.environ, CAREVISIT_UI_REPO=str(REPO))
    result = subprocess.run([str(REPO / "gradlew"), "-p", str(WORK), "compileKotlin", "--console=plain", "--no-watch-fs"], env=env)
    if result.returncode == 0:
        ui_files = list((REPO / "app/src/main/java/dev/carevisit/app/ui").rglob("*.kt"))
        outputs = WORK / "build/classes/kotlin/main/dev/carevisit/app/ui"
        for source in ui_files:
            assert (outputs / (source.stem + "Kt.class")).is_file(), f"UI source was not compiled: {source}"
        print(f"Compiled all {len(ui_files)} UI source files with the Compose compiler.", flush=True)
    print(f"Compose source-check build: {WORK}", flush=True)
    raise SystemExit(result.returncode)


if __name__ == "__main__":
    main()
