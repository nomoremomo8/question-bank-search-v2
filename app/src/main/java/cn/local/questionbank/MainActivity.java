package cn.local.questionbank;

import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStreamReader;

public class MainActivity extends Activity {
    private static final int PICK_QUESTION_FILE = 1001;
    private WebView webView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        webView = new WebView(this);
        setContentView(webView);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);

        webView.setWebViewClient(new WebViewClient());
        webView.addJavascriptInterface(new AndroidBridge(), "Android");
        webView.loadUrl("file:///android_asset/index.html");
    }

    public class AndroidBridge {
        @JavascriptInterface
        public void pickQuestionFile() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    intent.setType("*/*");
                    intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
                        "application/json", "text/csv", "text/plain", "application/octet-stream"
                    });
                    try {
                        startActivityForResult(intent, PICK_QUESTION_FILE);
                    } catch (Exception e) {
                        Toast.makeText(MainActivity.this,
                            "无法打开文件选择器：" + e.getMessage(),
                            Toast.LENGTH_LONG).show();
                    }
                }
            });
        }

        @JavascriptInterface
        public void saveText(final String name, final String text, final String type) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    try {
                        File dir = new File(getCacheDir(), "exports");
                        if (!dir.exists()) dir.mkdirs();
                        File file = new File(dir, name);
                        FileOutputStream output = new FileOutputStream(file);
                        output.write(text.getBytes("UTF-8"));
                        output.close();

                        Uri uri = FileProvider.getUriForFile(
                            MainActivity.this, getPackageName() + ".files", file);
                        Intent share = new Intent(Intent.ACTION_SEND);
                        share.setType(type);
                        share.putExtra(Intent.EXTRA_STREAM, uri);
                        share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        startActivity(Intent.createChooser(share, "保存或分享文件"));
                    } catch (Exception e) {
                        Toast.makeText(MainActivity.this,
                            "导出失败：" + e.getMessage(),
                            Toast.LENGTH_LONG).show();
                    }
                }
            });
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != PICK_QUESTION_FILE || resultCode != RESULT_OK ||
            data == null || data.getData() == null) return;

        Uri uri = data.getData();
        try {
            String fileName = getDisplayName(uri);
            String fileText = readUtf8(uri);
            String encodedName = jsonQuote(fileName);
            String encodedText = jsonQuote(fileText);
            final String javascript = "window.receivePickedFile(" + encodedName + "," + encodedText + ");";
            webView.post(new Runnable() {
                @Override
                public void run() {
                    webView.evaluateJavascript(javascript, null);
                }
            });
        } catch (Exception e) {
            Toast.makeText(this, "读取题库失败：" + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private String getDisplayName(Uri uri) {
        String fileName = "题库.json";
        Cursor cursor = getContentResolver().query(uri, null, null, null, null);
        if (cursor != null) {
            try {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (cursor.moveToFirst() && index >= 0) fileName = cursor.getString(index);
            } finally {
                cursor.close();
            }
        }
        return fileName;
    }

    private String readUtf8(Uri uri) throws Exception {
        StringBuilder result = new StringBuilder();
        BufferedReader reader = new BufferedReader(new InputStreamReader(
            getContentResolver().openInputStream(uri), "UTF-8"));
        char[] buffer = new char[8192];
        int count;
        while ((count = reader.read(buffer)) != -1) result.append(buffer, 0, count);
        reader.close();
        return result.toString();
    }

    private String jsonQuote(String value) {
        if (value == null) return "\"\"";
        StringBuilder out = new StringBuilder(value.length() + 16);
        out.append('\"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '\\': out.append("\\\\"); break;
                case '\"': out.append("\\\""); break;
                case '\b': out.append("\\b"); break;
                case '\f': out.append("\\f"); break;
                case '\n': out.append("\\n"); break;
                case '\r': out.append("\\r"); break;
                case '\t': out.append("\\t"); break;
                default:
                    if (c < 0x20 || c == '\u2028' || c == '\u2029') {
                        out.append(String.format("\\u%04x", (int)c));
                    } else {
                        out.append(c);
                    }
            }
        }
        out.append('\"');
        return out.toString();
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }
}
