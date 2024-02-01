#!/bin/bash
VERSION=$(curl -s "https://repo1.maven.org/maven2/cloud/mindbox/mobile-sdk/maven-metadata.xml" | grep latest | sed -e 's,.*<latest>\([^<]*\)</latest>.*,\1,g')
sed -i -e "s|https://search.maven.org/artifact/cloud.mindbox/mobile-sdk/[0-9].[0-9].[0-9]/aar|https://search.maven.org/artifact/cloud.mindbox/mobile-sdk/${VERSION}/aar|g" README.md