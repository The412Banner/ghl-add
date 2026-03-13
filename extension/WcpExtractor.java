package app.revanced.extension.gamehub;

import android.content.ContentResolver;
import android.net.Uri;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Extracts WCP (zstd/XZ tar) and ZIP archives into a component directory.
 *
 * <p>Uses reflection for GameHub's obfuscated runtime classes:
 * <ul>
 *   <li>{@code com.github.luben.zstd.ZstdInputStreamNoFinalizer} (JNI, @Keep, not obfuscated)</li>
 *   <li>{@code org.tukaani.xz.XZInputStream} (not obfuscated; constructor takes InputStream + int)</li>
 *   <li>{@code org.apache.commons.compress.archivers.tar.TarArchiveInputStream}
 *       (R8-obfuscated; {@code getNextTarEntry()} renamed to {@code s()})</li>
 *   <li>{@code org.apache.commons.compress.archivers.tar.TarArchiveEntry}
 *       ({@code getName()} kept via ArchiveEntry interface; {@code isDirectory()} obfuscated
 *       — detect by {@code getName().endsWith("/")} instead)</li>
 * </ul>
 * </p>
 */
@SuppressWarnings("unused")
public final class WcpExtractor {

    private static final String TAG = "BannerHub";
    private static final int BUF = 8192;

    private WcpExtractor() {}

    /**
     * Main entry point. Clears destDir, auto-detects format from the first 4 bytes,
     * then extracts ZIP/zstd-tar/XZ-tar accordingly.
     */
    public static void extract(ContentResolver cr, Uri uri, File destDir)
            throws Exception {

        clearDir(destDir);
        destDir.mkdirs();

        InputStream raw = cr.openInputStream(uri);
        if (raw == null) throw new IOException("Cannot open URI: " + uri);

        BufferedInputStream bis = new BufferedInputStream(raw);

        // Peek at 4-byte magic
        bis.mark(4);
        byte[] hdr = new byte[4];
        int read = bis.read(hdr, 0, 4);
        bis.reset();
        if (read < 2) throw new IOException("File too short");

        int b0 = hdr[0] & 0xFF, b1 = hdr[1] & 0xFF,
            b2 = hdr[2] & 0xFF, b3 = hdr[3] & 0xFF;

        if (b0 == 0x50 && b1 == 0x4B) {
            // ZIP: PK magic
            extractZip(bis, destDir);
            bis.close();

        } else if (b0 == 0x28 && b1 == 0xB5 && b2 == 0x2F && b3 == 0xFD) {
            // zstd tar
            InputStream zstd = openZstd(bis);
            extractTar(zstd, destDir);
            zstd.close();
            bis.close();

        } else if (b0 == 0xFD && b1 == 0x37 && b2 == 0x7A && b3 == 0x58) {
            // XZ tar
            InputStream xz = openXz(bis);
            extractTar(xz, destDir);
            xz.close();
            bis.close();

        } else {
            bis.close();
            throw new Exception(String.format(
                    "Unknown format (magic: %02X %02X %02X %02X)", b0, b1, b2, b3));
        }
    }

    // ── Format openers via reflection ──────────────────────────────────────────

    private static InputStream openZstd(InputStream in) throws Exception {
        Class<?> cls = Class.forName("com.github.luben.zstd.ZstdInputStreamNoFinalizer");
        Constructor<?> ctor = cls.getConstructor(InputStream.class);
        return (InputStream) ctor.newInstance(in);
    }

    private static InputStream openXz(InputStream in) throws Exception {
        Class<?> cls = Class.forName("org.tukaani.xz.XZInputStream");
        Constructor<?> ctor = cls.getConstructor(InputStream.class, int.class);
        return (InputStream) ctor.newInstance(in, -1); // -1 = unlimited memory
    }

    // ── ZIP extraction (flat — basename only) ─────────────────────────────────

    private static void extractZip(InputStream in, File destDir) throws IOException {
        byte[] buf = new byte[BUF];
        ZipInputStream zip = new ZipInputStream(in);
        ZipEntry entry;
        while ((entry = zip.getNextEntry()) != null) {
            if (entry.isDirectory()) {
                zip.closeEntry();
                continue;
            }
            // Flatten to basename
            String name = new File(entry.getName()).getName();
            File out = new File(destDir, name);
            try (FileOutputStream fos = new FileOutputStream(out)) {
                pipe(zip, fos, buf);
            }
            zip.closeEntry();
        }
        zip.close();
    }

    // ── Tar extraction ────────────────────────────────────────────────────────

    /**
     * Extracts a tar stream. Reads profile.json first pass to detect FEXCore
     * (flattenToRoot=true). All other types preserve system32/syswow64 structure.
     *
     * <p>TarArchiveInputStream is obfuscated in GameHub's dex:
     * getNextTarEntry() → s(); getName() kept; isDirectory() obfuscated → use endsWith("/").
     */
    private static void extractTar(InputStream in, File destDir) throws Exception {
        Class<?> tarClass = Class.forName(
                "org.apache.commons.compress.archivers.tar.TarArchiveInputStream");
        Constructor<?> tarCtor = tarClass.getConstructor(InputStream.class);
        Object tar = tarCtor.newInstance(in);

        Method nextEntry = tarClass.getMethod("s"); // obfuscated getNextTarEntry()
        Method getName = null;                        // resolved on first entry

        byte[] buf = new byte[BUF];
        boolean flattenToRoot = false;

        // First pass: scan for profile.json to detect component type
        Object entry;
        while ((entry = nextEntry.invoke(tar)) != null) {
            if (getName == null) {
                getName = entry.getClass().getMethod("getName");
            }
            String name = (String) getName.invoke(entry);
            if (name == null) continue;

            if (name.endsWith("/")) continue; // directory — skip

            if (name.endsWith("profile.json")) {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                pipeReflected(tar, baos, buf);
                String json = baos.toString("UTF-8");
                if (json.contains("FEXCore")) {
                    flattenToRoot = true;
                }
                continue; // profile.json itself is metadata — don't extract
            }

            // Strip leading "./"
            if (name.startsWith("./")) name = name.substring(2);
            if (name.isEmpty()) continue;

            File dest;
            if (flattenToRoot) {
                dest = new File(destDir, new File(name).getName());
            } else {
                dest = new File(destDir, name);
                File parent = dest.getParentFile();
                if (parent != null) parent.mkdirs();
            }

            try (FileOutputStream fos = new FileOutputStream(dest)) {
                pipeReflected(tar, fos, buf);
            }
        }

        // Close via reflection (close() is kept — not obfuscated)
        tarClass.getMethod("close").invoke(tar);
    }

    // ── I/O helpers ───────────────────────────────────────────────────────────

    private static void pipe(InputStream in, OutputStream out, byte[] buf) throws IOException {
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
    }

    /**
     * Reads from a TarArchiveInputStream (which is an InputStream at runtime) using the
     * standard InputStream.read([B)I method (which is not obfuscated).
     */
    private static void pipeReflected(Object tar, OutputStream out, byte[] buf) throws Exception {
        // TarArchiveInputStream is an InputStream — read([B)I is kept
        Method readMethod = tar.getClass().getMethod("read", byte[].class);
        int n;
        while ((n = (int) readMethod.invoke(tar, (Object) buf)) > 0) {
            out.write(buf, 0, n);
        }
    }

    // ── Directory utils ───────────────────────────────────────────────────────

    /** Recursively deletes all contents of dir (keeps dir itself). */
    static void clearDir(File dir) {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory()) {
                clearDir(f);
            }
            f.delete();
        }
    }
}
