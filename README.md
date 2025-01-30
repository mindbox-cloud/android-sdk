[![Maven Central](https://maven-badges.herokuapp.com/maven-central/cloud.mindbox/mobile-sdk/badge.svg)](https://central.sonatype.com/artifact/cloud.mindbox/mobile-sdk)

# Mindbox SDK for Android

The Mindbox SDK allows you to integrate mobile push-notifications, in-app messages and client events into your Android projects.

## Getting Started

These instructions will help you integrate the Mindbox SDK into your Android app.

### Installation

To integrate Mindbox SDK into your Android app, follow the installation process detailed [here](https://developers.mindbox.ru/docs/androidhuawei-native-sdk). Here is an overview:

1. Add Mindbox's Maven repository to your project build.gradle file:
```markdown
    repositories {
        maven()
    }
```
2. Now, add the Mindbox SDK to your app-level build.gradle file:
```markdown
    dependencies {
        implementation 'cloud.mindbox:mobile-sdk:{use-latest-version}}'
    }
```
111
### Initialization

Initialize the Mindbox SDK in your Activity or Application class. Check documentation [here](https://developers.mindbox.ru/docs/android-sdk-initialization) for more details.


### Operations

Learn how to send events to Mindbox. Create a new Operation class object and set the respective parameters. Check the [documentation](https://developers.mindbox.ru/docs/android-integration-of-actions) for more details.

### Push Notifications

Mindbox SDK helps handle push notifications. Configuration and usage instructions can be found in the SDK documentation [here](https://developers.mindbox.ru/docs/huawei-send-push-notifications) and [here](https://developers.mindbox.ru/docs/firebase-send-push-notifications).

### Migration

If migrating from an older version, follow the instructions [here](https://developers.mindbox.ru/docs/v1-v2-android-sdk) to upgrade to the new version.

## Troubleshooting

Refer to the [Example of integration](https://github.com/mindbox-cloud/android-sdk/tree/develop/example) in case of any issues.

## Further Help

Reach out to us for further help and we'll be glad to assist.

## License

The library is available as open source under the terms of the [License](https://github.com/mindbox-cloud/android-sdk/blob/master/LICENSE.md).

For a better understanding of this content, please familiarize yourself with the Mindbox [Android SDK](https://developers.mindbox.ru/docs/androidhuawei-native-sdk) documentation.
