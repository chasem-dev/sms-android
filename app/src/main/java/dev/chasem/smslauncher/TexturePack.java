package dev.chasem.smslauncher;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry;
import org.apache.commons.compress.archivers.sevenz.SevenZFile;

/**
 * The Super Mario Sunshine UHD Texture Pack and the port's HD texture extras, downloaded from where
 * their authors publish them and installed under the game's mods/textures/ as the port's
 * tools/mods/get.py installs them. Neither is part of the APK.
 */
final class TexturePack {
    interface Progress { void update(String text); }

    static final class Release {
        final String name, url, file, md5, prefix;
        final long size;
        Release(String name, String url, String file, String md5, long size, String prefix) {
            this.name = name; this.url = url; this.file = file; this.md5 = md5; this.size = size; this.prefix = prefix;
        }
    }

    // The release tools/mods/get.py pins, by qashto and razius.
    static final Release UHD = new Release("Super Mario Sunshine UHD Texture Pack v2.1.1",
        "https://github.com/qashto/Super_Mario_Sunshine_UHD_Texture_Pack/releases/download/2.1.1/GMS.7z",
        "GMS.7z", "274985dba9214b857b3e5605d2ee49b2", 986431331L, "GMS/Textures/GMS/");
    static final long UHD_UNPACKED = 3218563135L;
    // The release the game's tools/mods/texture-extras.json pins (read at build time).
    static final Release EXTRAS = new Release(BuildConfig.TEXTURE_EXTRAS_NAME, BuildConfig.TEXTURE_EXTRAS_URL,
        BuildConfig.TEXTURE_EXTRAS_FILE, BuildConfig.TEXTURE_EXTRAS_MD5, BuildConfig.TEXTURE_EXTRAS_SIZE, "GMS/");
    static final long DOWNLOAD_BYTES = UHD.size + EXTRAS.size;
    // Both archives, both unpacked, and a margin.
    static final long FREE_BYTES_NEEDED = UHD.size + UHD_UNPACKED + 3 * EXTRAS.size + (256L << 20);

    private final File game, textures, downloads, mipCache;
    private volatile boolean stopped;

    TexturePack(File game) {
        this.game = game;
        textures = new File(game, "mods/textures");
        downloads = new File(game, "mods/.downloads");
        // Levels the game makes for the pack's images (gx_hires.cpp, under XDG_CACHE_HOME).
        mipCache = new File(game, "cache/sms-port/texture-mips");
    }

    private File uhdDir() { return new File(textures, "GMS"); }
    private File extrasDir() { return new File(textures, "sms-hd-texture-extras"); }

    boolean uhdInstalled() { return uhdDir().isDirectory(); }
    boolean extrasInstalled() {
        try (InputStream in = new FileInputStream(new File(extrasDir(), ".release"))) {
            return EXTRAS.md5.equals(TexturePackLayout.releaseMd5(new String(readAll(in), StandardCharsets.UTF_8)));
        } catch (IOException e) { return false; }
    }
    boolean installed() { return uhdInstalled() && extrasInstalled(); }
    /** Bytes of the archives downloaded so far but not yet installed. */
    long partialBytes() {
        long n = 0;
        File[] files = downloads.listFiles();
        if (files != null) for (File f : files) n += f.length();
        return n;
    }
    long installedBytes() { return size(textures) + size(mipCache); }
    void stop() { stopped = true; }

    /** Downloads (resuming a stopped download) and installs whatever is missing. */
    void install(Progress progress) throws IOException {
        stopped = false;
        boolean uhd = !uhdInstalled(), extras = !extrasInstalled();
        long need = (uhd ? UHD.size + UHD_UNPACKED : 0) + (extras ? 3 * EXTRAS.size : 0) + (256L << 20) - partialBytes();
        long free = game.getUsableSpace();
        if (need > free)
            throw new IOException("HD textures need " + mb(need) + " MB of free space, and " + mb(free) + " MB is free.");
        downloads.mkdirs(); textures.mkdirs();
        File uhdArchive = uhd ? fetch(UHD, progress) : null;
        File extrasArchive = extras ? fetch(EXTRAS, progress) : null;
        if (uhdArchive != null) { unpackUhd(uhdArchive, progress); uhdArchive.delete(); }
        if (extrasArchive != null) { unpackExtras(extrasArchive, progress); extrasArchive.delete(); }
        downloads.delete();
    }

    /** Removes the installed pack, any partial download and the levels the game made for it. */
    void remove() throws IOException {
        erase(textures); erase(downloads); erase(mipCache);
    }

    private File fetch(Release r, Progress progress) throws IOException {
        File done = new File(downloads, r.file), part = new File(downloads, r.file + ".part");
        if (done.isFile() && done.length() == r.size) return done;
        IOException failure = null;
        for (int attempt = 0; attempt < 3 && part.length() < r.size; attempt++) {
            try { download(r, part, progress); failure = null; }
            catch (IOException e) { if (stopped) throw e; failure = e; }
        }
        if (part.length() != r.size)
            throw failure != null ? failure : new IOException("The " + r.name + " download stopped short.");
        progress.update("Checking the " + r.name + " download…");
        if (!r.md5.equals(md5(part))) {
            part.delete();
            throw new IOException("The " + r.name + " download did not match the expected release. Try again.");
        }
        if (!part.renameTo(done)) throw new IOException("The download could not be kept.");
        return done;
    }

    private void download(Release r, File part, Progress progress) throws IOException {
        long have = part.length();
        HttpURLConnection c = (HttpURLConnection) new URL(r.url).openConnection();
        try {
            c.setInstanceFollowRedirects(true);
            c.setConnectTimeout(30000); c.setReadTimeout(60000);
            c.setRequestProperty("User-Agent", "SMS Launcher for Android");
            if (have > 0) c.setRequestProperty("Range", "bytes=" + have + "-");
            int code = c.getResponseCode();
            if (code != HttpURLConnection.HTTP_OK && code != HttpURLConnection.HTTP_PARTIAL)
                throw new IOException("The download server answered " + code + ".");
            if (code == HttpURLConnection.HTTP_OK) have = 0;  // no resume: start over
            try (InputStream in = c.getInputStream(); OutputStream out = new FileOutputStream(part, have > 0)) {
                byte[] buf = new byte[1 << 18];
                long shown = 0;
                for (int n; (n = in.read(buf)) > 0; ) {
                    if (stopped) throw new IOException("Download stopped. Download again to continue.");
                    out.write(buf, 0, n);
                    have += n;
                    if (have > r.size) { out.close(); part.delete(); throw new IOException("The " + r.name + " download is larger than expected."); }
                    long now = System.nanoTime();
                    if (now - shown > 250_000_000L) {
                        shown = now;
                        progress.update("Downloading HD textures… " + mb(downloadedSoFar(r, have)) + " of " + mb(DOWNLOAD_BYTES) + " MB");
                    }
                }
            }
        } finally { c.disconnect(); }
    }

    private long downloadedSoFar(Release r, long have) {
        return r == UHD ? have : (uhdInstalled() || new File(downloads, UHD.file).isFile() ? UHD.size : 0) + have;
    }

    private void unpackUhd(File archive, Progress progress) throws IOException {
        File tmp = new File(textures, "GMS.part");
        erase(tmp);
        int count = 0;
        long done = 0, shown = 0;
        try (SevenZFile z = SevenZFile.builder().setFile(archive).get()) {
            byte[] buf = new byte[1 << 18];
            for (SevenZArchiveEntry e; (e = z.getNextEntry()) != null; ) {
                if (e.isDirectory()) continue;
                String path = TexturePackLayout.entryPath(e.getName(), UHD.prefix);
                if (path == null) continue;
                File f = new File(tmp, path);
                f.getParentFile().mkdirs();
                try (OutputStream out = new FileOutputStream(f)) {
                    for (int n; (n = z.read(buf)) > 0; ) {
                        if (stopped) throw new IOException("Stopped while unpacking. Download again to finish.");
                        out.write(buf, 0, n);
                        done += n;
                        long now = System.nanoTime();
                        if (now - shown > 250_000_000L) {
                            shown = now;
                            progress.update("Unpacking HD textures… " + mb(done) + " of " + mb(UHD_UNPACKED) + " MB");
                        }
                    }
                }
                count++;
            }
            if (count == 0) throw new IOException("The " + UHD.name + " download is not laid out as expected.");
            erase(uhdDir());
            if (!tmp.renameTo(uhdDir())) throw new IOException("The HD textures could not be installed.");
        } finally { erase(tmp); }
    }

    private void unpackExtras(File archive, Progress progress) throws IOException {
        progress.update("Unpacking the HD texture extras…");
        File tmp = new File(textures, "sms-hd-texture-extras.part");
        erase(tmp);
        try (ZipInputStream zip = new ZipInputStream(new BufferedInputStream(new FileInputStream(archive)))) {
            byte[] buf = new byte[1 << 18];
            int count = 0;
            for (ZipEntry e; (e = zip.getNextEntry()) != null; ) {
                String path = TexturePackLayout.entryPath(e.getName(), EXTRAS.prefix);
                if (e.isDirectory()) continue;
                if (path == null) throw new IOException("The " + EXTRAS.name + " download is not laid out as expected.");
                File f = new File(tmp, path);
                f.getParentFile().mkdirs();
                try (OutputStream out = new FileOutputStream(f)) {
                    for (int n; (n = zip.read(buf)) > 0; ) out.write(buf, 0, n);
                }
                count++;
            }
            if (count == 0) throw new IOException("The " + EXTRAS.name + " download is empty.");
            try (OutputStream out = new FileOutputStream(new File(tmp, ".release"))) {
                out.write(TexturePackLayout.releaseRecord(EXTRAS.name, EXTRAS.file, EXTRAS.md5).getBytes(StandardCharsets.UTF_8));
            }
            erase(extrasDir());
            if (!tmp.renameTo(extrasDir())) throw new IOException("The HD texture extras could not be installed.");
        } finally { erase(tmp); }
    }

    static long mb(long bytes) { return bytes / 1048576; }

    static String md5(File f) throws IOException {
        try (InputStream in = new FileInputStream(f)) {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] buf = new byte[1 << 18];
            for (int n; (n = in.read(buf)) > 0; ) md.update(buf, 0, n);
            StringBuilder s = new StringBuilder();
            for (byte b : md.digest()) s.append(String.format(Locale.ROOT, "%02x", b));
            return s.toString();
        } catch (java.security.NoSuchAlgorithmException e) { throw new IOException(e); }
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
        return out.toByteArray();
    }

    private static long size(File f) {
        if (f.isFile()) return f.length();
        long n = 0;
        File[] files = f.listFiles();
        if (files != null) for (File child : files) n += size(child);
        return n;
    }

    private static void erase(File f) throws IOException {
        if (!f.exists()) return;
        if (f.isDirectory()) { File[] files = f.listFiles(); if (files == null) throw new IOException("Cannot read " + f.getName()); for (File child : files) erase(child); }
        if (!f.delete()) throw new IOException("Cannot remove " + f.getName());
    }
}
