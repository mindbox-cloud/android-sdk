package cloud.mindbox.mobile_sdk.inapp.presentation.view;

import android.annotation.SuppressLint;
import android.webkit.JavascriptInterface;

public class WebAppInterface {

    Runnable runnable;

    public WebAppInterface(Runnable runnable) {
        this.runnable = runnable;
    }

    @SuppressLint("JavascriptInterface")
    @JavascriptInterface
    public void onCloseButtonClicked() {
        runnable.run();
    }
}
