[![Maven Central](https://img.shields.io/maven-central/v/cloud.mindbox/mobile-sdk?label=Maven%20Central)](https://central.sonatype.com/artifact/cloud.mindbox/mobile-sdk)

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

### Initialization 

Initialize the Mindbox SDK in your Activity or Application class. Check documentation [here](https://developers.mindbox.ru/docs/android-sdk-initialization) for more details.


### Operations

Learn how to send events to Mindbox. Create a new Operation class object and set the respective parameters. Check the [documentation](https://developers.mindbox.ru/docs/android-integration-of-actions) for more details.

### Push Notifications

Mindbox SDK helps handle push notifications. Configuration and usage instructions can be found in the SDK documentation [here](https://developers.mindbox.ru/docs/huawei-send-push-notifications) and [here](https://developers.mindbox.ru/docs/firebase-send-push-notifications).

### Tracking Identifiers

The SDK reports the device tracking identifier (GAID from Google Mobile Services, OAID from
Huawei Mobile Services) alongside the regular application events. This is **on by default**. Turn
it off when building the configuration if your app must not collect it:

```kotlin
val configuration = MindboxConfiguration.Builder(this, "domain", "endpointId")
    .disableTrackingIds(true)
    .build()
```

The option is re-read on every initialization, so it can be turned on or off in a later app
version. The SDK never asks for a permission of its own — it reads only what the system already exposes:

* **Android 13+ (API 33) requires the `com.google.android.gms.permission.AD_ID` permission** for
  the GAID to be readable. The `mindbox-firebase` module declares it, so it reaches your app
  through manifest merging if — and only if — you integrate Firebase push. An integration built on
  RuStore or Huawei alone never receives it, and has nothing to declare about the advertising id
  in the Play Console Data Safety form.
* The identifier is re-read on every application event, so turning "Delete advertising ID" on or
  off in the system settings reaches Mindbox with the next event.
* RuStore devices supply no advertising identifier at all. The OAID on Huawei devices comes from
  HMS and is not gated by the Google permission.

To stop the Mindbox collection, use the configuration option above rather than stripping the
permission: removing it is app-wide, so it also cuts off every other SDK in your app that needs
the advertising id, and it leaves the OAID untouched.

**Apps under the Google Play Families policy** must not request `AD_ID` at all. Remove it in your
own manifest — the GAID then stays unavailable and the SDK reports nothing, regardless of this
option:

```xml
<uses-permission
    android:name="com.google.android.gms.permission.AD_ID"
    tools:node="remove" />
```

`deviceUUID` is unaffected — it stays the stable installation key it has always been.

### Migration

If migrating from an older version, follow the instructions [here](https://developers.mindbox.ru/docs/v1-v2-android-sdk) to upgrade to the new version.

## Troubleshooting

Refer to the [Example of integration](https://github.com/mindbox-cloud/android-sdk/tree/develop/example) in case of any issues.

## Further Help

Reach out to us for further help and we'll be glad to assist.

## License

The library is available as open source under the terms of the [License](https://github.com/mindbox-cloud/android-sdk/blob/master/LICENSE.md).

For a better understanding of this content, please familiarize yourself with the Mindbox [Android SDK](https://developers.mindbox.ru/docs/androidhuawei-native-sdk) documentation.
