workspace(name = "spice")

load("@bazel_tools//tools/build_defs/repo:http.bzl", "http_archive")

# Rules and tooling versions
RULES_KOTLIN_VERSION = "v1.5.0-beta-4"

RULES_KOTLIN_SHA = "6cbd4e5768bdfae1598662e40272729ec9ece8b7bded8f0d2c81c8ff96dc139d"

MAVEN_REPOSITORY_RULES_VERSION = "2.0.0-alpha-5"

MAVEN_REPOSITORY_RULES_SHA = "fde80cafa02a2c034cc8086c158f500e7b6ceb16d251273a6cc82f1c0723e0e8"

KOTLIN_VERSION = "1.5.30"

KOTLINC_ROOT = "https://github.com/JetBrains/kotlin/releases/download"

KOTLINC_URL = "{root}/v{v}/kotlin-compiler-{v}.zip".format(
    root = KOTLINC_ROOT,
    v = KOTLIN_VERSION,
)

KOTLINC_SHA = "ccd0db87981f1c0e3f209a1a4acb6778f14e63fe3e561a98948b5317e526cc6c"

RETROFIT_VERSION = "2.7.2"

# Rules and tools repositories
http_archive(
    name = "io_bazel_rules_kotlin",
    sha256 = RULES_KOTLIN_SHA,
    urls = ["https://github.com/bazelbuild/rules_kotlin/releases/download/%s/rules_kotlin_release.tgz" % RULES_KOTLIN_VERSION],
)

http_archive(
    name = "maven_repository_rules",
    sha256 = MAVEN_REPOSITORY_RULES_SHA,
    strip_prefix = "bazel_maven_repository-%s" % MAVEN_REPOSITORY_RULES_VERSION,
    type = "zip",
    urls = ["https://github.com/square/bazel_maven_repository/archive/%s.zip" % MAVEN_REPOSITORY_RULES_VERSION],
)

# Setup Kotlin
load("@io_bazel_rules_kotlin//kotlin:repositories.bzl", "kotlin_repositories")

kotlin_repositories()

load("@io_bazel_rules_kotlin//kotlin:core.bzl", "kt_register_toolchains")

kt_register_toolchains()

# Setup maven repository handling.
load("@maven_repository_rules//maven:maven.bzl", "maven_repository_specification")

maven_repository_specification(
    name = "maven",
    artifacts = {
        "com.fasterxml.jackson.core:jackson-annotations:2.11.0": {"insecure": True},
        "com.fasterxml.jackson.core:jackson-core:2.11.0": {"insecure": True},
        "com.fasterxml.jackson.core:jackson-databind:2.11.0": {"insecure": True},
        "com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.11.0": {"insecure": True},
        "com.fasterxml.jackson.module:jackson-module-kotlin:2.11.0": {"insecure": True},
        "com.google.auto.value:auto-value-annotations:1.6.3": {"insecure": True},
        "com.google.code.findbugs:jsr305:3.0.2": {"insecure": True},
        "com.google.errorprone:error_prone_annotations:2.3.1": {"insecure": True},
        "com.google.guava:failureaccess:1.0.1": {"insecure": True},
        "com.google.guava:guava:27.0.1-android": {
            "insecure": True,
            "exclude": ["com.google.guava:listenablefuture"],
        },
        "com.google.j2objc:j2objc-annotations:1.1": {"insecure": True},
        "com.google.truth:truth:1.1.3": {
            "insecure": True,
            "exclude": ["junit:junit"],
        },
        "com.googlecode.java-diff-utils:diffutils:1.3.0": {"insecure": True},
        "com.squareup.okhttp3:okhttp:4.5.0": {"insecure": True},
        "com.squareup.okio:okio:2.4.1": {"insecure": True},
        "com.squareup.retrofit2:converter-jackson:%s" % RETROFIT_VERSION: {"insecure": True},
        "com.squareup.retrofit2:converter-wire:%s" % RETROFIT_VERSION: {"insecure": True},
        "com.squareup.retrofit2:retrofit:%s" % RETROFIT_VERSION: {"insecure": True},
        "com.squareup.wire:wire-runtime:3.0.0": {"insecure": True},
        "com.xenomachina:kotlin-argparser:2.0.7": {"insecure": True},
        "com.xenomachina:xenocom:0.0.7": {"insecure": True},
        "org.apiguardian:apiguardian-api:1.1.2": {"insecure": True},
        "org.checkerframework:checker-compat-qual:2.5.5": {"insecure": True},
        "org.codehaus.mojo:animal-sniffer-annotations:1.17": {"insecure": True},
        "org.jetbrains.kotlin:kotlin-reflect:%s" % KOTLIN_VERSION: {"insecure": True},
        "org.jetbrains.kotlin:kotlin-stdlib-common:%s" % KOTLIN_VERSION: {"insecure": True},
        "org.jetbrains.kotlin:kotlin-stdlib:%s" % KOTLIN_VERSION: {"insecure": True},
        "org.jetbrains.kotlin:kotlin-stdlib-jdk8:%s" % KOTLIN_VERSION: {"insecure": True},
        "org.jetbrains.kotlin:kotlin-stdlib-jdk7:%s" % KOTLIN_VERSION: {"insecure": True},
        "org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.5.2": {"insecure": True},
        "org.jetbrains:annotations:13.0": {"insecure": True},
        "org.junit.jupiter:junit-jupiter-api:5.8.2": {"insecure": True},
        "org.junit.jupiter:junit-jupiter-engine:5.8.2": {"insecure": True},
        "org.junit.platform:junit-platform-commons:1.8.2": {"insecure": True},
        "org.junit.platform:junit-platform-console:1.8.2": {"insecure": True},
        "org.junit.platform:junit-platform-reporting:1.8.2": {"insecure": True},
        "org.junit.platform:junit-platform-launcher:1.8.2": {"insecure": True},
        "org.junit.platform:junit-platform-engine:1.8.2": {"insecure": True},
        "org.opentest4j:opentest4j:1.2.0": {"insecure": True},
        "org.yaml:snakeyaml:1.26": {"insecure": True},
        "org.checkerframework:checker-qual:3.13.0": {"insecure": True},
        "org.ow2.asm:asm:9.1": {"insecure": True},
    },
)
