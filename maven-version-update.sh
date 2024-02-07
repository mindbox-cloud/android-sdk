#!/bin/bash
GRADLE_PROPERTIES_FILE=gradle.properties
function getProperty {
    PROP_KEY=SDK_VERSION_NAME
    PROP_VALUE=`cat $GRADLE_PROPERTIES_FILE | grep -w "$PROP_KEY" | cut -d'=' -f2`
    echo $PROP_VALUE
}
SDK_VERSION_NAME=$(getProperty "SDK_VERSION_NAME")
sed -i -e "s|implementation 'cloud.mindbox:mobile_sdk:[0-9].[0-9].[0-9]'|implementation 'cloud.mindbox:mobile_sdk:${SDK_VERSION_NAME}'|" README.md