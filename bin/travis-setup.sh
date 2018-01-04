#!/usr/bin/env bash

# Enable strict mode and fail the script on non-zero exit code,
# unresolved variable or pipe failure.
set -euo pipefail
IFS=$'\n\t'

if [ "$(uname)" == "Darwin" ]; then

    brew update
    brew install sbt
fi
