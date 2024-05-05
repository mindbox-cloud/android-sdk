plugins {
    id("maven-publish")
}

group = "cloud.mindbox"
version = findProperty("SDK_VERSION_NAME") as String




afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("mindbox-bom") {
                pom.withXml {
                    asNode().appendNode("dependencyManagement").apply {
                        appendNode("dependencies").apply {
                            fun dependency(groupId: String, artifactId: String, version: String) {
                                appendNode("dependency").apply {
                                    appendNode("groupId", groupId)
                                    appendNode("artifactId", artifactId)
                                    appendNode("version", version)
                                }
                            }

                            dependency("cloud.mindbox", "mobile-sdk", version)
                            dependency("cloud.mindbox", "mindbox-firebase", version)
                            dependency("cloud.mindbox", "mindbox-huawei", version)
                        }
                    }
                }
            }
        }
    }
}