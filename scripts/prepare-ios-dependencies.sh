#!/usr/bin/env bash

set -euo pipefail

repository_root="$(cd "$(dirname "$0")/.." && pwd -P)"
engine_version=0.1.6
engine_checksum=f556718ec0fd122a61bc5408307b19fcd54397595954c46c6ff8db1e5a179421
engine_root="${KAPLAY_ENGINE_ROOT:-${repository_root}/../kaplay-engine}"
engine_framework="${engine_root}/platform/apple/KAPlayEngine.xcframework"

if [[ ! -f "${repository_root}/MPVKit/Package.swift" ]]; then
    git -C "${repository_root}" submodule update --init --depth 1 MPVKit
fi

if [[ -f "${engine_framework}/Info.plist" ]]; then
    exit 0
fi

temporary_directory="$(mktemp -d "${TMPDIR:-/tmp}/kaplay-ios-dependencies.XXXXXX")"
trap 'rm -rf "${temporary_directory}"' EXIT

archive="${temporary_directory}/kaplay-engine-apple-${engine_version}.zip"
curl --fail --location --retry 5 --retry-all-errors --silent --show-error \
    --output "${archive}" \
    "https://github.com/KAPlayMedia/kaplay-engine/releases/download/v${engine_version}/kaplay-engine-apple-${engine_version}.zip"

actual_checksum="$(shasum -a 256 "${archive}" | awk '{print $1}')"
if [[ "${actual_checksum}" != "${engine_checksum}" ]]; then
    echo "KAPlay Engine Apple package checksum mismatch." >&2
    exit 1
fi

extraction_root="${temporary_directory}/extracted"
mkdir -p "${extraction_root}"
unzip -q "${archive}" -d "${extraction_root}"
source_framework="$(find "${extraction_root}" -type d -name KAPlayEngine.xcframework -print -quit)"
if [[ -z "${source_framework}" || ! -f "${source_framework}/Info.plist" ]]; then
    echo "KAPlay Engine Apple package does not contain KAPlayEngine.xcframework." >&2
    exit 1
fi
if [[ ! -f "${source_framework}/ios-arm64/libCKAPlayEngine.a" ]]; then
    echo "KAPlay Engine Apple package does not contain the iOS arm64 library." >&2
    exit 1
fi

mkdir -p "$(dirname "${engine_framework}")"
ditto "${source_framework}" "${engine_framework}"
