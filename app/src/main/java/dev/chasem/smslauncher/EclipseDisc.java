package dev.chasem.smslauncher;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry;
import org.apache.commons.compress.archivers.sevenz.SevenZFile;

/**
 * Super Mario Eclipse, made from the player's own Super Mario Sunshine disc as the game's
 * tools/mods/get.py makes it: the Eclipse team's release is downloaded and its patch applied to the
 * imported disc, giving the same image Eclipse's own patcher makes. None of it is part of the APK.
 */
final class EclipseDisc {
    static final String NAME = "Super Mario Eclipse v1.1.0";
    // The GameBanana release, mirrored for SMS Launcher; GameBanana itself is the fallback.
    static final String[] URLS = {
        "https://sms-eclipse.tekrantgaming.com/files/super_mario_eclipse_v110.7z",
        "https://gamebanana.com/dl/1729332",
    };
    static final String FILE = "super_mario_eclipse_v110.7z", MD5 = "37c1805ab88b2a3bb2a96bcdf6884433";
    static final long SIZE = 892257115L;
    static final String PATCH = "patches/v1.1.0.xdelta";
    static final long PATCH_SIZE = 1191516980L;
    // The North American disc Eclipse's own patcher requires, and what it makes of it.
    static final String SOURCE_MD5 = "0c6d2edae9fdf40dfc410ff1623e4119", RESULT_MD5 = "caa546309e0443f7b47632b040a250d7";
    static final long RESULT_SIZE = 1459978240L;

    private final File sunshine, eclipse, downloads;
    private volatile boolean stopped;

    /** eclipse is the disc the game plays; the Sunshine disc it is made from is kept beside it. */
    EclipseDisc(File eclipse) {
        this.eclipse = eclipse;
        sunshine = new File(eclipse.getParentFile(), "sunshine.iso");
        downloads = new File(eclipse.getParentFile(), "downloads");
    }

    File sunshineDisc() { return sunshine; }
    boolean installed() { return eclipse.isFile(); }
    boolean sunshineImported() { return sunshine.isFile(); }
    /** Bytes of the release downloaded so far. */
    long partialBytes() {
        long n = 0;
        File[] files = downloads.listFiles();
        if (files != null) for (File f : files) n += f.length();
        return n;
    }
    void stop() { stopped = true; }

    /** Downloads the release (resuming a stopped download) and patches the imported Sunshine disc. */
    void install(TexturePack.Progress progress) throws IOException {
        stopped = false;
        if (!sunshine.isFile()) throw new IOException("Import your Super Mario Sunshine disc image first.");
        File archive = new File(downloads, FILE);
        boolean downloaded = archive.isFile() && archive.length() == SIZE;
        long need = (downloaded ? 0 : SIZE - partialBytes()) + RESULT_SIZE + (64L << 20);
        long free = eclipse.getParentFile().getUsableSpace();
        if (need > free)
            throw new IOException("Super Mario Eclipse needs " + TexturePack.mb(need) + " MB of free space, and "
                + TexturePack.mb(free) + " MB is free.");
        downloads.mkdirs();
        if (!downloaded) archive = fetch(progress);
        File part = new File(eclipse.getParentFile(), "eclipse.iso.part");
        try {
            progress.update("Patching your disc…");
            long[] shown = {0};
            patch(archive, sunshine, part, read -> {
                if (stopped) throw new IOException("Stopped while patching. Download again to finish.");
                long now = System.nanoTime();
                if (now - shown[0] > 250_000_000L) {
                    shown[0] = now;
                    progress.update("Patching your disc… " + (100 * read / PATCH_SIZE) + "%");
                }
            });
            progress.update("Checking the patched disc…");
            if (!RESULT_MD5.equals(TexturePack.md5(part)))
                throw new IOException("The patched disc is not the expected one. Import your Super Mario Sunshine disc image again.");
            Files.move(part.toPath(), eclipse.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } finally { part.delete(); }
        // Neither is needed to play, and together they take 2.2 GB.
        archive.delete(); downloads.delete(); sunshine.delete();
    }

    /** Writes out = the release's patch applied to the Sunshine disc, reading the patch out of the archive. */
    static void patch(File archive, File sunshine, File out, Vcdiff.Progress progress) throws IOException {
        try (SevenZFile z = SevenZFile.builder().setFile(archive).get()) {
            for (SevenZArchiveEntry e; (e = z.getNextEntry()) != null; ) {
                if (!e.getName().replace('\\', '/').equals(PATCH)) continue;
                try (InputStream in = z.getInputStream(e)) { Vcdiff.apply(sunshine, in, out, progress); }
                return;
            }
        }
        throw new IOException("The " + NAME + " download has no patch in it.");
    }

    private File fetch(TexturePack.Progress progress) throws IOException {
        File done = new File(downloads, FILE), part = new File(downloads, FILE + ".part");
        IOException failure = null;
        for (String url : URLS) {
            for (int attempt = 0; attempt < 3 && part.length() < SIZE; attempt++) {
                try { download(url, part, progress); failure = null; }
                catch (IOException e) { if (stopped) throw e; failure = e; }
            }
            if (part.length() == SIZE) break;
        }
        if (part.length() != SIZE)
            throw failure != null ? failure : new IOException("The " + NAME + " download stopped short.");
        progress.update("Checking the " + NAME + " download…");
        if (!MD5.equals(TexturePack.md5(part))) {
            part.delete();
            throw new IOException("The " + NAME + " download did not match the expected release. Try again.");
        }
        if (!part.renameTo(done)) throw new IOException("The download could not be kept.");
        return done;
    }

    private void download(String url, File part, TexturePack.Progress progress) throws IOException {
        long have = part.length();
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
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
                    if (have > SIZE) { out.close(); part.delete(); throw new IOException("The " + NAME + " download is larger than expected."); }
                    long now = System.nanoTime();
                    if (now - shown > 250_000_000L) {
                        shown = now;
                        progress.update("Downloading Super Mario Eclipse… " + TexturePack.mb(have) + " of " + TexturePack.mb(SIZE) + " MB");
                    }
                }
            }
        } finally { c.disconnect(); }
    }
}
