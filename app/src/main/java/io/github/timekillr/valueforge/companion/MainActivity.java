package io.github.timekillr.valueforge.companion;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.view.WindowInsets;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import org.json.JSONObject;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity {
    private WebView web;
    private PairStore store;
    private final ExecutorService reads = Executors.newSingleThreadExecutor();
    private final ExecutorService writes = Executors.newSingleThreadExecutor();
    private volatile Transport.Cancellation readCancellation = new Transport.Cancellation();
    private synchronized void cancelReads() {
        readCancellation.cancel();
        readCancellation = new Transport.Cancellation();
    }
    private static final String LOCAL_NETWORK = "android.permission.ACCESS_LOCAL_NETWORK";
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        if (Build.VERSION.SDK_INT >= 30) getWindow().setDecorFitsSystemWindows(false);
        store = new PairStore(this);
        web = new WebView(this); web.setBackgroundColor(Color.rgb(13, 20, 32));
        WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(true); settings.setAllowFileAccess(false); settings.setAllowContentAccess(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setSupportMultipleWindows(false); settings.setDomStorageEnabled(false);
        web.addJavascriptInterface(new Bridge(), "ForgeNative");
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) { return true; }
        });
        // Inset the WebView's actual bounds: WebView padding does not reliably
        // move its HTML viewport below Android's status bar on edge-to-edge apps.
        FrameLayout content = new FrameLayout(this);
        content.setBackgroundColor(Color.rgb(13, 20, 32));
        content.addView(web, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        content.setOnApplyWindowInsetsListener((view, insets) -> {
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout() | WindowInsets.Type.ime());
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
                return WindowInsets.CONSUMED;
            }
            view.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(), insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            return insets.consumeSystemWindowInsets();
        });
        setContentView(content);
        content.requestApplyInsets();
        web.loadUrl("file:///android_asset/index.html");
    }
    private void reply(String id, JSONObject data, Exception error) {
        if (isFinishing() || isDestroyed()) return;
        JSONObject result = new JSONObject();
        try {
            result.put("ok", error == null);
            if (error == null) result.put("data", data);
            else {
                result.put("error", error.getMessage() == null ? "Connection lost. Check the Steam Machine and your home network." : error.getMessage());
                result.put("status", error instanceof Transport.Failure ? ((Transport.Failure) error).status : 0);
            }
        } catch (Exception ignored) { return; }
        runOnUiThread(() -> web.evaluateJavascript("window.ForgeReply(" + JSONObject.quote(id) + "," + result + ")", null));
    }
    private JSONObject settings() throws Exception {
        JSONObject pairing = store.load();
        return new JSONObject().put("paired", pairing != null).put("endpoint", pairing == null ? "" : pairing.getString("endpoint"))
                .put("network_permission", Build.VERSION.SDK_INT < 37 || checkSelfPermission(LOCAL_NETWORK) == PackageManager.PERMISSION_GRANTED);
    }
    private void requireNetwork() throws Exception {
        if (Build.VERSION.SDK_INT >= 37 && checkSelfPermission(LOCAL_NETWORK) != PackageManager.PERMISSION_GRANTED)
            throw new Transport.Failure(403, "Allow local network access in Connection before connecting.");
    }
    private void pair(String id, JSONObject args) throws Exception {
        requireNetwork();
        Endpoint endpoint = Endpoint.parse(args.getString("endpoint"));
        String code = args.getString("code");
        if (!code.matches("[0-9]{6}")) throw new IllegalArgumentException("Enter the six-digit pairing code shown in Decky.");
        JSONObject info = Transport.probe(endpoint);
        String pin = info.getString("fingerprint");
        runOnUiThread(() -> new AlertDialog.Builder(this).setTitle("Verify your Steam Machine")
                .setMessage("Check that this fingerprint matches Phone companion in the Decky plugin:\n\n" + Transport.shortFingerprint(pin) + "\n\nOnly pair if they match.")
                .setNegativeButton("Cancel", (dialog, which) -> reply(id, null, new Exception("Pairing cancelled.")))
                .setOnCancelListener(dialog -> reply(id, null, new Exception("Pairing cancelled.")))
                .setPositiveButton("Match & pair", (dialog, which) -> writes.execute(() -> {
                    try {
                        JSONObject paired = Transport.request(endpoint, pin, null, "POST", "/v1/pair", new JSONObject().put("code", code).put("device_name", Build.MANUFACTURER + " " + Build.MODEL));
                        paired.put("endpoint", endpoint.authority()); paired.put("pin", pin); store.save(paired);
                        reply(id, settings(), null);
                    } catch (Exception error) { reply(id, null, error); }
                })).show());
    }
    final class Bridge {
        @JavascriptInterface public void request(String id, String action, String text) {
            if (!id.matches("[A-Za-z0-9_-]{1,64}")) return;
            // Do this directly on the bridge thread: the read/write executors
            // may be blocked on network IO or a long-running scan command.
            if (action.equals("cancel_reads")) { cancelReads(); reply(id, new JSONObject(), null); return; }
            Transport.Cancellation requestCancellation = readCancellation;
            ExecutorService pool = action.equals("state") || action.equals("processes") || action.equals("ping") ? reads : writes;
            pool.execute(() -> {
                try {
                    JSONObject args = new JSONObject(text);
                    if (action.equals("settings")) { reply(id, settings(), null); return; }
                    if (action.equals("permission")) {
                        runOnUiThread(() -> { if (Build.VERSION.SDK_INT >= 37) requestPermissions(new String[]{LOCAL_NETWORK}, 7); });
                        reply(id, settings(), null); return;
                    }
                    if (action.equals("pair")) { pair(id, args); return; }
                    if (action.equals("forget")) {
                        JSONObject old = store.load(); store.forget(); reply(id, settings(), null);
                        if (old != null) reads.execute(() -> {
                            try { Transport.request(Endpoint.parse(old.getString("endpoint")), old.getString("pin"), old.getString("token"), "POST", "/v1/unpair", new JSONObject()); }
                            catch (Exception ignored) { /* Offline devices can also be removed in Decky. */ }
                        });
                        return;
                    }
                    JSONObject pairing = store.load();
                    if (pairing == null) throw new Transport.Failure(401, "Pair this phone first.");
                    if (action.equals("address")) {
                        Endpoint next = Endpoint.parse(args.getString("endpoint"));
                        // Validate the remembered pin and token at the new address before saving it.
                        Transport.request(next, pairing.getString("pin"), pairing.getString("token"), "GET", "/v1/ping", null);
                        pairing.put("endpoint", next.authority()); store.save(pairing); reply(id, settings(), null); return;
                    }
                    requireNetwork();
                    Endpoint endpoint = Endpoint.parse(pairing.getString("endpoint"));
                    String path, method = "GET"; JSONObject body = null;
                    switch (action) {
                        case "state": path = "/v1/state?refresh=" + (args.optBoolean("refresh") ? "1" : "0"); break;
                        case "processes": path = "/v1/processes"; break;
                        case "ping": path = "/v1/ping"; break;
                        case "command": path = "/v1/command"; method = "POST"; body = args; body.put("request_id", UUID.randomUUID().toString()); break;
                        default: throw new IllegalArgumentException("Unsupported app request.");
                    }
                    boolean read = method.equals("GET");
                    int timeout = Math.max(1, Math.min(8000, args.optInt("timeout_ms", 8000)));
                    reply(id, Transport.request(endpoint, pairing.getString("pin"), pairing.getString("token"), method, path, body,
                            read ? requestCancellation : null, timeout), null);
                } catch (Exception error) {
                    if (error instanceof Transport.Failure && ((Transport.Failure) error).status == 401) store.forget();
                    reply(id, null, error);
                }
            });
        }
    }
    @Override public void onRequestPermissionsResult(int request, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(request, permissions, results);
        web.evaluateJavascript("window.ForgePermissionsChanged&&window.ForgePermissionsChanged()", null);
    }
    @Override protected void onResume() { super.onResume(); if (web != null) web.evaluateJavascript("window.ForgeLifecycle&&window.ForgeLifecycle(true)", null); }
    @Override protected void onPause() { if (web != null) web.evaluateJavascript("window.ForgeLifecycle&&window.ForgeLifecycle(false)", null); super.onPause(); }
    @Override protected void onDestroy() { cancelReads(); reads.shutdownNow(); writes.shutdownNow(); if (web != null) { web.removeJavascriptInterface("ForgeNative"); web.destroy(); } super.onDestroy(); }
}
