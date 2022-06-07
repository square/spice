"""
Provides JUnit5 support for running kotlin tests.

This is a shim until rules_kotlin supports JUnit 5.
"""

load("@io_bazel_rules_kotlin//kotlin:jvm.bzl", "kt_jvm_library")

def kt_jvm_test(name, test_class, data = [], main_class = "", friends = [], size = "medium", **kwargs):
    kt_jvm_library(
        name = name + "_kt_lib",
        testonly = True,
        associates = friends,
        **kwargs
    )

    pkg, _, _ = test_class.rpartition(".")

    native.java_test(
        name = name,
        use_testrunner = False,
        main_class = "org.junit.platform.console.ConsoleLauncher",
        data = data,
        size = size,
        args = ["--select-package", pkg, "--disable-banner", "--fail-if-no-tests", "--disable-ansi-colors", "--details", "summary"],
        runtime_deps = [
            "@maven//org/junit/jupiter:junit-jupiter-api",
            "@maven//org/junit/jupiter:junit-jupiter-engine",
            "@maven//org/junit/platform:junit-platform-commons",
            "@maven//org/junit/platform:junit-platform-console",
            "@maven//org/junit/platform:junit-platform-engine",
            "@maven//org/junit/platform:junit-platform-launcher",
            ":" + name + "_kt_lib",
        ],
    )
