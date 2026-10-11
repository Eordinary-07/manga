package com.sultan.mangareader;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.webkit.WebViewAssetLoader;

import java.io.InputStream;

public class MainActivity extends Activity {
    private static final int PICK_FILES = 1001;
    private static final int STORAGE_PERM = 1002;
    private static final String HOST = "appassets.androidplatform.net";

    private WebView web;
    private ValueCallback<Uri[]> pending;

    // A zip handed to the app from outside ("Open with" or Share).
    private volatile Uri sharedUri;
    private volatile String sharedName = "";

    private class Bridge {
        @JavascriptInterface
        public String sharedName() { return sharedName; }

        @JavascriptInterface
        public void sharedDone() { sharedUri = null; sharedName = ""; }
    }

    private void takeIntent(Intent in) {
        if (in == null) return;
        Uri u = null;
        if (Intent.ACTION_VIEW.equals(in.getAction())) {
            u = in.getData();
        } else if (Intent.ACTION_SEND.equals(in.getAction())) {
            u = in.getParcelableExtra(Intent.EXTRA_STREAM);
        }
        if (u == null) return;
        sharedUri = u;
        sharedName = nameOf(u);
    }

    private String nameOf(Uri u) {
        String n = null;
        if ("content".equals(u.getScheme())) {
            try (Cursor c = getContentResolver().query(u, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
                if (c != null && c.moveToFirst()) n = c.getString(0);
            } catch (Exception ignored) { }
        }
        if (n == null || n.isEmpty()) n = u.getLastPathSegment();
        return (n == null || n.isEmpty()) ? "manga.zip" : n;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final WebViewAssetLoader loader = new WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
                .addPathHandler("/shared/", path -> {
                    Uri u = sharedUri;
                    if (u == null) return null;
                    try {
                        InputStream in = getContentResolver().openInputStream(u);
                        if (in == null) return null;
                        return new WebResourceResponse("application/octet-stream", null, in);
                    } catch (Exception e) {
                        return null;
                    }
                })
                .build();

        web = new WebView(this);
        web.setBackgroundColor(0xFF0C0C0E);
        setContentView(web);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(true);
        s.setSupportZoom(true);
        s.setBuiltInZoomControls(true);
        s.setDisplayZoomControls(false);

        web.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                return loader.shouldInterceptRequest(request.getUrl());
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                // Keep everything inside the app; never navigate to outside sites.
                return !HOST.equals(request.getUrl().getHost());
            }
        });

        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback,
                                             FileChooserParams params) {
                if (pending != null) {
                    pending.onReceiveValue(null);
                }
                pending = callback;
                // Open the system file picker for any file type; the page itself
                // only keeps .zip/.cbz files. This avoids pickers that grey out
                // zips when the page's accept list contains bare extensions.
                Intent pick = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                pick.addCategory(Intent.CATEGORY_OPENABLE);
                pick.setType("*/*");
                pick.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                try {
                    startActivityForResult(pick, PICK_FILES);
                } catch (Exception e1) {
                    try {
                        startActivityForResult(params.createIntent(), PICK_FILES);
                    } catch (Exception e2) {
                        pending = null;
                        return false;
                    }
                }
                return true;
            }
        });

        // Ask for storage access on Android 12 and older. Android 13+ does not
        // need it because the system picker hands the app the chosen files.
        if (Build.VERSION.SDK_INT < 33
                && checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, STORAGE_PERM);
        }

        web.addJavascriptInterface(new Bridge(), "Android");
        takeIntent(getIntent());
        web.loadUrl("https://" + HOST + "/assets/index.html");
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        takeIntent(intent);
        if (web != null) {
            web.evaluateJavascript("window.openSharedZip && window.openSharedZip()", null);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == PICK_FILES) {
            if (pending != null) {
                pending.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(resultCode, data));
                pending = null;
            }
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void onBackPressed() {
        if (web != null && web.canGoBack()) {
            web.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        if (web != null) {
            web.destroy();
        }
        super.onDestroy();
    }
}
