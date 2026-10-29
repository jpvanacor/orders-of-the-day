package io.github.jpvanacor.orders;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * The alarm screen, shown over the lock screen while the alarm rings.
 * A small page (assets/wake) runs the camera and reads the QR code; the code is checked here.
 */
public class AlarmActivity extends Activity {
    private static final String HOST = "alarm.orders.local";
    private static final int CAMERA_REQUEST = 7301;
    private static final int BG = 0xFF0E1317;

    private WebView web;
    private PermissionRequest pending;
    private boolean stopped;
    private final Handler handler = new Handler(Looper.getMainLooper());

    /** Close the screen when the ringing ended some other way (the one-hour limit). */
    private final Runnable watch = new Runnable() {
        @Override
        public void run() {
            if (!stopped && !WakeAlarm.isRinging(AlarmActivity.this)) {
                finish();
                return;
            }
            handler.postDelayed(this, 2000);
        }
    };

    @Override
    @SuppressWarnings("deprecation")
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        } else {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        if (!WakeAlarm.isRinging(this)) {
            finish();
            return;
        }
        if (Build.VERSION.SDK_INT >= 33) {
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT,
                new android.window.OnBackInvokedCallback() {
                    @Override
                    public void onBackInvoked() {
                        // Back does nothing here: the code or the sentence.
                    }
                }
            );
        }

        web = new WebView(this);
        web.setBackgroundColor(BG);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        web.addJavascriptInterface(new Bridge(), "Alarm");
        web.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                return serve(request.getUrl());
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return true;
            }
        });
        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onPermissionRequest(PermissionRequest request) {
                askCamera(request);
            }
        });

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(BG);
        root.addView(web, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        root.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            @Override
            public WindowInsets onApplyWindowInsets(View v, WindowInsets insets) {
                return applyInsets(v, insets);
            }
        });
        setContentView(root);
        web.loadUrl("https://" + HOST + "/alarm.html");
        handler.postDelayed(watch, 2000);
    }

    @SuppressWarnings("deprecation")
    private WindowInsets applyInsets(View v, WindowInsets insets) {
        int l, t, r, b;
        if (Build.VERSION.SDK_INT >= 30) {
            android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            android.graphics.Insets ime = insets.getInsets(WindowInsets.Type.ime());
            l = bars.left;
            t = bars.top;
            r = bars.right;
            b = Math.max(bars.bottom, ime.bottom);
        } else {
            l = insets.getSystemWindowInsetLeft();
            t = insets.getSystemWindowInsetTop();
            r = insets.getSystemWindowInsetRight();
            b = insets.getSystemWindowInsetBottom();
        }
        v.setPadding(l, t, r, b);
        return insets;
    }

    /** Serves the alarm page, the QR reader and the app fonts from the APK, under an https address. */
    private WebResourceResponse serve(Uri url) {
        if (url == null || !HOST.equals(url.getHost())) return notFound();
        String path = url.getPath() == null ? "" : url.getPath();
        String asset;
        String mime;
        if (path.equals("/alarm.html")) {
            asset = "wake/alarm.html";
            mime = "text/html";
        } else if (path.equals("/jsQR.js")) {
            asset = "wake/jsQR.js";
            mime = "application/javascript";
        } else if (path.matches("/fonts/[a-z0-9-]+\\.woff2")) {
            asset = "public" + path;
            mime = "font/woff2";
        } else {
            return notFound();
        }
        try {
            InputStream in = getAssets().open(asset, AssetManager.ACCESS_STREAMING);
            Map<String, String> headers = new HashMap<>();
            headers.put("Cache-Control", "no-store");
            return new WebResourceResponse(mime, "utf-8", 200, "OK", headers, in);
        } catch (IOException e) {
            return notFound();
        }
    }

    private static WebResourceResponse notFound() {
        return new WebResourceResponse("text/plain", "utf-8", 404, "Not Found", new HashMap<String, String>(), new ByteArrayInputStream(new byte[0]));
    }

    private void askCamera(PermissionRequest request) {
        boolean video = false;
        for (String r : request.getResources()) {
            if (PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(r)) video = true;
        }
        Uri origin = request.getOrigin();
        if (!video || origin == null || !HOST.equals(origin.getHost())) {
            request.deny();
            return;
        }
        if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            request.grant(new String[] { PermissionRequest.RESOURCE_VIDEO_CAPTURE });
            return;
        }
        if (pending != null) pending.deny();
        pending = request;
        requestPermissions(new String[] { Manifest.permission.CAMERA }, CAMERA_REQUEST);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode != CAMERA_REQUEST || pending == null) return;
        if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) {
            pending.grant(new String[] { PermissionRequest.RESOURCE_VIDEO_CAPTURE });
        } else {
            pending.deny();
        }
        pending = null;
    }

    /** What the page may ask: the sentence to type, and whether a code or a sentence is right. */
    final class Bridge {
        @JavascriptInterface
        public String info() {
            JSONObject o = new JSONObject();
            try {
                o.put("sentence", WakeAlarm.SENTENCE);
                o.put("test", WakeAlarm.isTest(AlarmActivity.this));
            } catch (JSONException ignored) {
                // stays empty
            }
            return o.toString();
        }

        @JavascriptInterface
        public boolean check(String text) {
            if (!WakeAlarm.matches(text)) return false;
            runOnUiThread(stopper);
            return true;
        }

        @JavascriptInterface
        public boolean escape(String text) {
            if (!WakeAlarm.sentenceMatches(text)) return false;
            runOnUiThread(stopper);
            return true;
        }
    }

    private final Runnable stopper = new Runnable() {
        @Override
        public void run() {
            stopAlarm();
        }
    };

    private final Runnable leave = new Runnable() {
        @Override
        public void run() {
            try {
                startActivity(new Intent(AlarmActivity.this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (RuntimeException ignored) {
                // the app opens next time anyway
            }
            finish();
        }
    };

    private void stopAlarm() {
        if (stopped) return;
        stopped = true;
        stopService(new Intent(this, WakeAlarmService.class));
        WakeAlarm.markRinging(this, false, false);
        android.app.NotificationManager nm = (android.app.NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm != null) nm.cancel(WakeAlarmService.NOTIF_ID);
        handler.postDelayed(leave, 1800);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        // Back does nothing here: the code or the sentence.
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_MUTE) {
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (web != null) web.onResume();
    }

    @Override
    protected void onPause() {
        if (web != null) web.onPause();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (pending != null) {
            pending.deny();
            pending = null;
        }
        if (web != null) {
            web.removeJavascriptInterface("Alarm");
            web.destroy();
            web = null;
        }
        super.onDestroy();
    }
}
