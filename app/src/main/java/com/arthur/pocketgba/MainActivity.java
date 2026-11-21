package com.arthur.pocketgba;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

public class MainActivity extends Activity {
    private static final String ORIGIN = "https://appassets.androidplatform.net";
    private static final int IMPORT_ROM = 201;
    private static final int MAX_ROM_BYTES = 32 * 1024 * 1024;
    private static final String[] SLOTS = {"mario3", "marioworld", "hook", "bomber2", "bomber3"};
    private static final String[] TITLES = {
        "Super Mario Advance 4: Super Mario Bros. 3",
        "Super Mario Advance 2: Super Mario World",
        "Hook (Peter Pan)",
        "Bomberman 2 — title to confirm",
        "Bomberman 3 — title to confirm"
    };
    private static final String[] NOTES = {
        "GBA · 2003", "GBA · 2001", "SNES / original Game Boy, not GBA",
        "Exact GBA edition uncertain", "Exact GBA edition uncertain"
    };

    private WebView webView;
    private String pendingSlot;

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.rgb(18, 20, 35));
        getWindow().setNavigationBarColor(Color.rgb(18, 20, 35));
        webView = new WebView(this);
        webView.setBackgroundColor(Color.rgb(18, 20, 35));
        setContentView(webView);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        webView.addJavascriptInterface(new LibraryBridge(), "PocketAndroid");
        webView.setWebChromeClient(new WebChromeClient());
        webView.setWebViewClient(new WebViewClient() {
            @Override public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                return serveLocal(request.getUrl());
            }
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                return !"https".equals(uri.getScheme()) || !"appassets.androidplatform.net".equals(uri.getHost());
            }
            @Override public void onPageFinished(WebView view, String url) {
                boolean playing = url.contains("/assets/play.html");
                if (playing) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            }
        });
        home();
    }

    private void home() {
        webView.loadUrl(ORIGIN + "/assets/index.html");
    }

    private WebResourceResponse serveLocal(Uri uri) {
        if (!"https".equals(uri.getScheme()) || !"appassets.androidplatform.net".equals(uri.getHost())) {
            return notFound(); // Never fetch remote assets, ROMs or tracking resources.
        }
        String path = uri.getPath();
        if (path == null || path.contains("..") || path.indexOf('\\') >= 0) return notFound();
        try {
            if (path.startsWith("/assets/")) {
                String name = path.substring("/assets/".length());
                if (name.isEmpty()) return notFound();
                return response(name, getAssets().open(name));
            }
            if (path.startsWith("/roms/")) {
                String file = path.substring("/roms/".length());
                if (!file.matches("[a-z0-9-]+\\.gba")) return notFound();
                File rom = new File(romDir(), file);
                if (!rom.isFile()) return notFound();
                return response(file, new FileInputStream(rom));
            }
        } catch (IOException ignored) {
            return notFound();
        }
        return notFound();
    }

    private WebResourceResponse response(String name, InputStream input) {
        String mime;
        if (name.endsWith(".js") || name.endsWith(".mjs")) mime = "text/javascript";
        else if (name.endsWith(".css")) mime = "text/css";
        else if (name.endsWith(".html")) mime = "text/html";
        else if (name.endsWith(".json")) mime = "application/json";
        else if (name.endsWith(".wasm")) mime = "application/wasm";
        else if (name.endsWith(".svg")) mime = "image/svg+xml";
        else if (name.endsWith(".png")) mime = "image/png";
        else if (name.endsWith(".woff2")) mime = "font/woff2";
        else if (name.endsWith(".woff")) mime = "font/woff";
        else mime = "application/octet-stream";
        return new WebResourceResponse(mime, null, input);
    }

    private WebResourceResponse notFound() {
        WebResourceResponse response = new WebResourceResponse("text/plain", "UTF-8",
            new ByteArrayInputStream("Not found".getBytes(StandardCharsets.UTF_8)));
        response.setStatusCodeAndReasonPhrase(404, "Not Found");
        return response;
    }

    private File romDir() {
        File dir = new File(getFilesDir(), "roms");
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    private boolean isSlot(String id) {
        for (String slot : SLOTS) if (slot.equals(id)) return true;
        return false;
    }

    private String titleFor(String id) {
        for (int i = 0; i < SLOTS.length; i++) if (SLOTS[i].equals(id)) return TITLES[i];
        try {
            JSONArray extras = extras();
            for (int i = 0; i < extras.length(); i++) {
                JSONObject item = extras.getJSONObject(i);
                if (id.equals(item.optString("id"))) return item.optString("title", "Game");
            }
        } catch (JSONException ignored) {}
        return "GBA Game";
    }

    private JSONArray extras() {
        try {
            return new JSONArray(getPreferences(MODE_PRIVATE).getString("games", "[]"));
        } catch (JSONException ignored) { return new JSONArray(); }
    }

    private void saveExtras(JSONArray games) {
        getPreferences(MODE_PRIVATE).edit().putString("games", games.toString()).apply();
    }

    private JSONArray library() {
        JSONArray items = new JSONArray();
        for (int i = 0; i < SLOTS.length; i++) {
            JSONObject entry = new JSONObject();
            try {
                entry.put("id", SLOTS[i]);
                entry.put("title", TITLES[i]);
                entry.put("note", NOTES[i]);
                entry.put("ready", new File(romDir(), SLOTS[i] + ".gba").isFile());
                entry.put("featured", true);
                items.put(entry);
            } catch (JSONException ignored) {}
        }
        JSONArray extras = extras();
        for (int i = 0; i < extras.length(); i++) {
            JSONObject entry = extras.optJSONObject(i);
            if (entry != null && new File(romDir(), entry.optString("id") + ".gba").isFile()) {
                items.put(entry);
            }
        }
        return items;
    }

    private void startImport(String slot) {
        pendingSlot = isSlot(slot) ? slot : "new";
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("*/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(intent, IMPORT_ROM);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != IMPORT_ROM || resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        String filename = "";
        try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (idx >= 0) filename = cursor.getString(idx);
            }
        } catch (Exception ignored) {}
        if (filename == null || !filename.toLowerCase(java.util.Locale.ROOT).endsWith(".gba")) {
            Toast.makeText(this, "Choose a .gba game file", Toast.LENGTH_LONG).show();
            return;
        }
        String id = "new".equals(pendingSlot) ? UUID.randomUUID().toString() : pendingSlot;
        File target = new File(romDir(), id + ".gba");
        File staging = new File(romDir(), id + ".part");
        try (InputStream src = getContentResolver().openInputStream(uri);
             FileOutputStream dst = new FileOutputStream(staging)) {
            if (src == null) throw new IOException("Cannot open file");
            byte[] buffer = new byte[65536];
            int size = 0;
            int count;
            while ((count = src.read(buffer)) != -1) {
                size += count;
                if (size > MAX_ROM_BYTES) throw new IOException("ROM exceeds 32 MiB");
                dst.write(buffer, 0, count);
            }
            if (size < 192) throw new IOException("File is too small to be a GBA ROM");
        } catch (Exception ex) {
            staging.delete();
            Toast.makeText(this, "Import failed: " + ex.getMessage(), Toast.LENGTH_LONG).show();
            return;
        }
        if (!staging.renameTo(target)) {
            staging.delete();
            Toast.makeText(this, "Could not save the ROM", Toast.LENGTH_LONG).show();
            return;
        }
        if ("new".equals(pendingSlot)) {
            JSONObject entry = new JSONObject();
            try {
                entry.put("id", id);
                entry.put("title", filename.substring(0, filename.length() - 4));
                entry.put("note", "Imported GBA game");
                entry.put("ready", true);
                entry.put("featured", false);
                JSONArray existing = extras();
                existing.put(entry);
                saveExtras(existing);
            } catch (JSONException ignored) {}
        }
        Toast.makeText(this, "Game imported", Toast.LENGTH_SHORT).show();
        webView.evaluateJavascript("if (window.refreshLibrary) window.refreshLibrary()", null);
    }

    private void remove(String id) {
        if (!id.matches("[a-z0-9-]+")) return;
        new File(romDir(), id + ".gba").delete();
        JSONArray old = extras();
        JSONArray updated = new JSONArray();
        for (int i = 0; i < old.length(); i++) {
            JSONObject entry = old.optJSONObject(i);
            if (entry != null && !id.equals(entry.optString("id"))) updated.put(entry);
        }
        saveExtras(updated);
        webView.evaluateJavascript("if (window.refreshLibrary) window.refreshLibrary()", null);
    }

    public final class LibraryBridge {
        @JavascriptInterface public String getLibrary() { return library().toString(); }
        @JavascriptInterface public String getGameName(String id) { return titleFor(id); }
        @JavascriptInterface public void importRom(String slot) {
            runOnUiThread(() -> startImport(slot));
        }
        @JavascriptInterface public void play(String id) {
            if (!id.matches("[a-z0-9-]+") || !new File(romDir(), id + ".gba").isFile()) return;
            runOnUiThread(() -> webView.loadUrl(ORIGIN + "/assets/play.html?id=" + id));
        }
        @JavascriptInterface public void removeRom(String id) {
            runOnUiThread(() -> remove(id));
        }
        @JavascriptInterface public void exitGame() { runOnUiThread(MainActivity.this::home); }
    }

    @Override public void onBackPressed() {
        if (webView.getUrl() != null && webView.getUrl().contains("/assets/play.html")) home();
        else super.onBackPressed();
    }

    @Override protected void onPause() {
        if (webView != null) webView.onPause();
        super.onPause();
    }
    @Override protected void onResume() {
        super.onResume();
        if (webView != null) webView.onResume();
    }
    @Override protected void onDestroy() {
        if (webView != null) webView.destroy();
        super.onDestroy();
    }
}
