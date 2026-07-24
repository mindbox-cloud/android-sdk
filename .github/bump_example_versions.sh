#!/usr/bin/env bash
set -euo pipefail

# Bumps the example app version for a Google Play release:
#   - versionCode: current value in example/app/build.gradle + 1
#   - versionName: synced with SDK_VERSION_NAME from gradle.properties
# Creates branch example-release/<versionName>-vc<versionCode>, commits and pushes it.
# Writes version_code / version_name / release_branch to GITHUB_OUTPUT when available.

build_gradle="example/app/build.gradle"
properties_file="gradle.properties"

current_code=$(grep -E '^[[:space:]]*versionCode[[:space:]]+[0-9]+' "$build_gradle" | grep -oE '[0-9]+' | head -n 1)
if [ -z "$current_code" ]; then
    echo "Failed to read versionCode from $build_gradle"
    exit 1
fi

version_name=$(grep -E '^SDK_VERSION_NAME=' "$properties_file" | cut -d'=' -f2)
if [ -z "$version_name" ]; then
    echo "Failed to read SDK_VERSION_NAME from $properties_file"
    exit 1
fi

new_code=$((current_code + 1))
release_branch="example-release/${version_name}-vc${new_code}"

if [ -n "$(git ls-remote --heads origin "refs/heads/$release_branch")" ]; then
    echo "Branch $release_branch already exists on origin."
    echo "A previous release run probably failed after pushing. Merge or delete its branch/PR and re-run."
    exit 1
fi

echo "Bump example versionCode from $current_code to $new_code, versionName to $version_name."

git checkout -b "$release_branch"

sed -i -E "s/^([[:space:]]*)versionCode[[:space:]]+[0-9]+/\1versionCode $new_code/" "$build_gradle"
sed -i -E "s/^([[:space:]]*)versionName[[:space:]]+\"[^\"]*\"/\1versionName \"$version_name\"/" "$build_gradle"

git add -f "$build_gradle"
if git diff --cached --quiet; then
    echo "Nothing to commit — the version bump produced no changes. Aborting instead of pushing an empty branch."
    exit 1
fi
git commit -m "Bump example app to versionName $version_name, versionCode $new_code"

echo "Pushing changes to branch: $release_branch"
if ! git push origin "$release_branch"; then
    echo "Failed to push changes to the origin $release_branch"
    exit 1
fi

if [ -n "${GITHUB_OUTPUT:-}" ]; then
    {
        echo "version_code=$new_code"
        echo "version_name=$version_name"
        echo "release_branch=$release_branch"
    } >> "$GITHUB_OUTPUT"
fi
