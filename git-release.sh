#!/bin/bash

version=$(cat gradle.properties |grep SDK_VERSION_NAME | cut -f2 -d"=")
text=$1
token=$2
branch=$(git rev-parse --abbrev-ref HEAD)
repo_full_name=$(git config --get remote.origin.url | sed 's/.*:\/\/github.com\///;s/.git$//' | cut -f2 -d":")
git_org=
generate_post_data()
{
  cat <<EOF
{
  "tag_name": "$version",
  "target_commitish": "$branch",
  "name": "Pre-release-$version",
  "body": "$text",
  "draft": false,
  "prerelease": true
}
EOF
}
echo "Setting tags"
git tag -d $version
git push --delete origin $version
git tag $version
git push origin $version
echo "Create release $version for repo: $repo_full_name branch: $branch"
curl --data "$(generate_post_data)" "https://api.github.com/repos/$repo_full_name/releases?access_token=$token"