#!/usr/bin/env bash
set -e

(( $# == 1 )) || (echo "usage: release.sh <appname>" ; exit 1)

NAME="$1"
GIT_ROOT="$(git rev-parse --show-toplevel)"
ROOT="${GIT_ROOT}"/tools/spice

# keep in sync with third_party/rules/release/bazel_maven_repository/BUILD.bazel
ARTIFACT="${NAME}.jar"
(
  # build and test
  cd "$ROOT"
  echo "build and test"
  bazel test //... && bazel build //apps/"${NAME}"
  echo "Copy jar"
  install "bazel-bin/apps/${NAME}/${NAME}" "${ROOT}/bin/${ARTIFACT}"
  (cd "${ROOT}/bin" && ln -s -f "${ARTIFACT}" "${NAME}" )
)
