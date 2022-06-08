#!/usr/bin/env bash

bazel_bin="${GITHUB_WORKSPACE}/bin/bazel"
timeout=120

set -x
timeout ${timeout} "${bazel_bin}" sync
while [ $? -ne 0 ]; do
  timeout ${timeout} "${bazel_bin}" sync
done
