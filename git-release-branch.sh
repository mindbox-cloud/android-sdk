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

if [[ $current_branch != "develop" && ! $current_branch =~ ^release/[0-9]+\.[0-9]+\.[0-9]+(-rc)?$ ]]; then
  echo "The current Git branch ($current_branch) is not 'develop' or in the format 'release/X.Y.Z' or 'release/X.Y.Z-rc'."
  exit 1
fi

# Create a branch with the version name
version=$1
branch_name="release/$version"
git branch $branch_name
git checkout $branch_name

# Add changelog to the index and create a commit
properties_file="gradle.properties"
current_version=$(grep -E '^SDK_VERSION_NAME=' gradle.properties | cut -d'=' -f2)
sed -i '' "s/^SDK_VERSION_NAME=.*/SDK_VERSION_NAME=$version/" $properties_file

build_gradle_example_path="example/app/build.gradle"
sed -i '' -E "s/cloud.mindbox:mobile-sdk:[0-9]+\.[0-9]+\.[0-9]+(-rc)?/cloud.mindbox:mobile-sdk:$version/" $build_gradle_example_path

echo "Bump SDK version from $current_version to $version."

git add $properties_file
git add -f $build_gradle_example_path
git commit -m "Bump SDK version to $version"

echo "Branch $branch_name has been created."
