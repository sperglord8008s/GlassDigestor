#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-only
# Sourced by host tests; Android provides org.json at runtime.
source scripts/fetch-verified.sh
load_json_test_dependency() {
  json_test_jar="${JSON_JAR:-$1/json.jar}"
  fetch_verified_maven_dependency "$json_test_jar" 3cf6cd6892e32e2b4c1c39e0f52f5248a2f5b37646fdfbb79a66b46b618414ed \
    org/json/json/20240303/json-20240303.jar
}
