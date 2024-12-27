#!/bin/bash

# Check if the parameter is provided
if [ $# -eq 0 ]; then
  echo "Please provide the release version number as a parameter."
  exit 1
fi

# Check if the version number matches the semver format
if ! [[ $1 =~ ^[0-9]+\.[0-9]+\.[0-9]+(-rc)?$ ]]; then
  echo "The release version number does not match the semver format (X.Y.Z or X.Y.Z-rc)."
  exit 1
fi

# Check the current Git branch
current_branch=$(git symbolic-ref --short HEAD)
echo "Currently on branch: $current_branch"

if [[ ! $current_branch =~ ^(release|support)/[0-9]+\.[0-9]+\.[0-9]+(-rc)?$ ]]; then
    echo "The current Git branch ($current_branch) is not in the format 'release/X.Y.Z', 'release/X.Y.Z-rc', 'support/X.Y.Z' or 'support/X.Y.Z-rc'."
    exit 1
fi

version=$1

# Show the current directory and its files
echo "Current directory: $(pwd)"
echo "Files in the current directory:"
ls -l

# Add changelog to the index and create a commit
properties_file="gradle.properties"
current_version=$(grep -E '^SDK_VERSION_NAME=' gradle.properties | cut -d'=' -f2)
sed -i "s/^SDK_VERSION_NAME=.*/SDK_VERSION_NAME=$version/" $properties_file
sed -i "s/^SDK_VERSION_NAME=.*/SDK_VERSION_NAME=$version/" $properties_file
build_gradle_example_path="example/app/build.gradle"
sed -i -E "s/cloud.mindbox:mobile-sdk:[0-9]+\.[0-9]+\.[0-9]+(-rc)?/cloud.mindbox:mobile-sdk:$version/" $build_gradle_example_path

echo "Bump SDK version from $current_version to $version."

git add $properties_file
git add -f $build_gradle_example_path
git commit -m "Bump SDK version to $version"

echo "Pushing changes to branch: $current_branch"
if ! git push origin $current_branch; then
    echo "Failed to push changes to the origin $current_branch"
    exit 1
fi
