package app.revanced.extension.gamehub;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.util.Log;
import android.view.Gravity;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Component Manager Activity — injected into GameHub's dex via ReVanced extension.
 *
 * <p>Two-level ListView UI:
 * <ol>
 *   <li>Component list — shows all subdirs of {@code getFilesDir()/usr/home/components/}.
 *       Appends {@code [-> filename]} for any component that has been previously injected.</li>
 *   <li>Options menu (per component) — Inject file / Backup / Back</li>
 * </ol>
 *
 * <p>Injected filenames are persisted in SharedPreferences ({@code "bh_injected"}) so the
 * label survives app restarts.</p>
 *
 * <p>Must be registered in AndroidManifest.xml (done by the resource patch).</p>
 */
@SuppressWarnings("unused")
public class ComponentManagerActivity extends Activity {

    private static final String TAG = "BannerHub";
    private static final int REQUEST_CODE_PICK_WCP = 1001;
    private static final String PREFS_INJECTED = "bh_injected";

    private File componentsDir;
    private SharedPreferences injectedPrefs;
    private String selectedComponent;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        componentsDir = new File(getFilesDir(), "usr/home/components");
        injectedPrefs = getSharedPreferences(PREFS_INJECTED, MODE_PRIVATE);
        showComponents();
    }

    // ── Screen: component list ────────────────────────────────────────────────

    private void showComponents() {
        List<String> rows = new ArrayList<>();
        if (componentsDir.isDirectory()) {
            File[] dirs = componentsDir.listFiles(File::isDirectory);
            if (dirs != null) {
                Arrays.sort(dirs);
                for (File d : dirs) {
                    String name = d.getName();
                    String injected = injectedPrefs.getString(name, null);
                    rows.add(injected != null ? name + " [-> " + injected + "]" : name);
                }
            }
        }
        if (rows.isEmpty()) rows.add("(no components found)");

        LinearLayout root = buildRoot();
        ListView listView = buildList(root, rows);
        setContentView(root);

        listView.setOnItemClickListener((parent, view, position, id) -> {
            String raw = rows.get(position);
            if (raw.equals("(no components found)")) return;
            // Strip "[-> filename]" suffix to recover the bare component name
            int bracket = raw.indexOf(" [-> ");
            selectedComponent = bracket >= 0 ? raw.substring(0, bracket) : raw;
            showOptions();
        });
    }

    // ── Screen: per-component options ─────────────────────────────────────────

    private void showOptions() {
        List<String> options = new ArrayList<>();
        options.add("Inject file");
        options.add("Backup");
        options.add("Back");

        LinearLayout root = buildRoot();

        TextView subtitle = new TextView(this);
        subtitle.setText(selectedComponent);
        subtitle.setTextSize(14f);
        subtitle.setPadding(0, 0, 0, 24);
        root.addView(subtitle);

        ListView listView = buildList(root, options);
        setContentView(root);

        listView.setOnItemClickListener((parent, view, position, id) -> {
            switch (position) {
                case 0: openFilePicker(); break;
                case 1: backupComponent(selectedComponent); break;
                case 2: showComponents(); break;
            }
        });
    }

    // ── File injection ─────────────────────────────────────────────────────────

    private void openFilePicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        startActivityForResult(intent, REQUEST_CODE_PICK_WCP);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_CODE_PICK_WCP || resultCode != RESULT_OK
                || data == null || data.getData() == null) return;

        Uri uri = data.getData();
        File destDir = new File(componentsDir, selectedComponent);
        final String componentName = selectedComponent;

        Handler uiHandler = new Handler(Looper.getMainLooper());
        new Thread(() -> {
            try {
                WcpExtractor.extract(getContentResolver(), uri, destDir);
                String filename = getFileName(uri);
                if (filename != null) {
                    injectedPrefs.edit().putString(componentName, filename).apply();
                }
                uiHandler.post(() -> {
                    Toast.makeText(this, "Injected successfully", Toast.LENGTH_SHORT).show();
                    showComponents();
                });
            } catch (Throwable t) {
                Log.e(TAG, "Extraction failed", t);
                String msg = t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName();
                uiHandler.post(() ->
                        Toast.makeText(this, "Inject failed: " + msg, Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    private String getFileName(Uri uri) {
        try (Cursor cursor = getContentResolver().query(
                uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                return cursor.getString(0);
            }
        } catch (Exception e) {
            Log.e(TAG, "getFileName failed", e);
        }
        return null;
    }

    // ── Backup ────────────────────────────────────────────────────────────────

    private void backupComponent(String name) {
        File src = new File(componentsDir, name);
        if (!src.isDirectory()) {
            Toast.makeText(this, "Component directory not found", Toast.LENGTH_SHORT).show();
            return;
        }

        File backupRoot = new File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "BannerHub/" + name);
        backupRoot.mkdirs();

        Handler uiHandler = new Handler(Looper.getMainLooper());
        new Thread(() -> {
            try {
                copyDir(src, backupRoot);
                uiHandler.post(() ->
                        Toast.makeText(this, "Backed up to Downloads/BannerHub/" + name,
                                Toast.LENGTH_SHORT).show());
            } catch (Exception e) {
                Log.e(TAG, "Backup failed", e);
                uiHandler.post(() ->
                        Toast.makeText(this, "Backup failed: " + e.getMessage(),
                                Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private static void copyDir(File src, File dst) throws Exception {
        dst.mkdirs();
        File[] files = src.listFiles();
        if (files == null) return;
        byte[] buf = new byte[8192];
        for (File f : files) {
            if (f.isDirectory()) {
                copyDir(f, new File(dst, f.getName()));
            } else {
                try (InputStream in = new FileInputStream(f);
                     OutputStream out = new FileOutputStream(new File(dst, f.getName()))) {
                    int n;
                    while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                }
            }
        }
    }

    // ── UI helpers ────────────────────────────────────────────────────────────

    /** Returns a root LinearLayout with the "Banners Component Injector" title pre-added. */
    private LinearLayout buildRoot() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(48, 48, 48, 48);

        TextView title = new TextView(this);
        title.setText("Banners Component Injector");
        title.setTextSize(20f);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, 0, 0, 24);
        root.addView(title);

        return root;
    }

    /** Adds a full-height ListView backed by {@code items} to {@code root} and returns it. */
    private ListView buildList(LinearLayout root, List<String> items) {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this, android.R.layout.simple_list_item_1, items);
        ListView listView = new ListView(this);
        listView.setAdapter(adapter);
        root.addView(listView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        return listView;
    }
}
