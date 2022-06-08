#!/usr/bin/env bash

bazel_bin="${GITHUB_WORKSPACE}/bin/bazel"

set -x
timeout 60 "${bazel_bin}" sync
while [ $? -ne 0 ]; do
  timeout 60 "${bazel_bin}" sync
done
