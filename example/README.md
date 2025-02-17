
To run the example application with functioning mobile push notifications (complete only step 4 for in-app functionality to work), follow these steps:

1) Change the package identifier in the **app/build.gradle** file

2) Add your application to either Firebase, Huawei, RuStore project, following the instructions provided at:
[Firebase Key Generation](https://developers.mindbox.ru/docs/firebase-get-keys) / 
[Huawei Key Generation](https://developers.mindbox.ru/docs/huawei-get-keys) /
[RuStore Key Generation](https://developers.mindbox.ru/docs/huawei-get-keys) /
or add app in your existing project

3) Configure Push Notification Services:
* For Firebase:
Copy the **google-services.json** file into the app folder of your project.

* For Huawei:
Copy the **agcconnect-services.json** file into the app folder of your project.

* For RuStore:
Change RU_STORE_PROJECT_ID <meta-data> in **app/src/main/AndroidManifest.xml**. Replacing RU_STORE_PROJECT_ID with your actual RuStore project ID.

4) Set your domain and endpoint in the [ExampleApplication](https://github.com/mindbox-cloud/android-sdk/blob/develop/example/app/src/main/java/com/mindbox/example/ExampleApplication.kt) class within the configuration builder

5) Run the application

6) After 5 minutes check your user in your Mindbox admin site

7) Run in-app and send mobile push
