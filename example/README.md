
To run the example application with functioning mobile push notifications (complete only step 4 for in-app functionality to work), follow these steps:

1) Change the package identifier in the **app/build.gradle** file if needed

2) Add your application to either Firebase, Huawei, RuStore project, following the instructions provided at:
[Firebase Key Generation](https://developers.mindbox.ru/docs/firebase-get-keys) /
[Huawei Key Generation](https://developers.mindbox.ru/docs/huawei-get-keys) /
[RuStore Key Generation](https://developers.mindbox.ru/docs/rustore-get-keys) /
or add app in your existing project

3) Configure Push Notification Services:
* For Firebase:
Copy the **google-services.json** file into the **app/** folder of your project.

* For Huawei:
Copy the **agconnect-services.json** file into the **app/** folder of your project.

4) Create **example.properties** in the root of the example project (next to `settings.gradle`).
This file is gitignored — fill in your own values:
```
mindbox.domain=your.domain.here
mindbox.endpointId=your-endpoint-id
mindbox.ruStoreProjectId=your-rustore-project-id
```
These values are injected into `BuildConfig` at build time and read by
[ExampleApplication](https://github.com/mindbox-cloud/android-sdk/blob/develop/example/app/src/main/java/com/mindbox/example/ExampleApplication.kt).
Alternatively, you can set the values directly in that file as string literals.

5) Run the application

6) After 5 minutes check your user in your Mindbox admin site

7) Run in-app and send mobile push
