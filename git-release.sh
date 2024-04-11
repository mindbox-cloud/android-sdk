#!/bin/bash
set -e
version=$(cat gradle.properties | grep SDK_VERSION_NAME | cut -f2 -d"=")
token=$1
user=$2
branch=${GITHUB_REF#refs/heads/}
repo_full_name=$(git config --get remote.origin.url | sed 's/.*:\/\/github.com\///;s/.git$//' | cut -f2 -d":")
generate_post_data() {
  cat <<EOF
{
  "tag_name": "$version",
  "target_commitish": "$branch",
  "name": "$name",
  "body": "$body",
  "draft": false,
  "prerelease": $prerelease
}
EOF
}
set-tag() {
  set-local-tag
  #set-remote-tag
}
prepare-release-data() {
  if [[ $version =~ ^[0-9.]+$ ]]; then
    name="Release-$version"
#    release_notes_1=$(curl -s --show-error --user "$user:$token" -H "Accept: application/vnd.github.v3+json" "https://api.github.com/repos/$repo_full_name/pulls?base=$branch" | grep "\"body\":" | cut -f2- -d:)
#    release_notes="${release_notes_1:2:${#release_notes_1}-4}"
    release_notes=""
    body="# What's new:\n$release_notes"
    prerelease=false
  else
    name="Pre-release-$version"
    body="Automatically generated pre-release"
    prerelease=true
  fi
}

set-local-tag() {
  echo "Setting local tag"
  git tag -f "$version"
}
set-remote-tag() {
  if [ $(git ls-remote --tags https://$user:$token@github.com/$repo_full_name.git | cut -f3 -d"/" | grep $version) ]; then
    echo "Remote tag cleanup"
    git push --delete https://$user:$token@github.com/$repo_full_name.git $version || exit 1
    git push https://$user:$token@github.com/$repo_full_name.git $version || exit 1
  else
    git push https://$user:$token@github.com/$repo_full_name.git $version || exit 1
  fi
}
echo "Create release $version for repo: $repo_full_name branch: $branch"
echo "Release settings: $(generate_post_data)"
post-request() {
   response=$(curl -s -f --show-error --user "$user:$token" --data "$(generate_post_data)" "https://api.github.com/repos/$repo_full_name/releases")

    status=$?

    if [ $status -ne 0 ]; then
        echo "Error: Unable to get successful HTTP response from https://api.github.com/repos/$repo_full_name/releases"
        exit 1
    fi

    echo $response
}
set-tag
prepare-release-data
post-request
