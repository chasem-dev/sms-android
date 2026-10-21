package dev.chasem.smslauncher;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * A language pack from the game's tools/mods/language-packs.json: a language's text and pictures,
 * downloaded when it is chosen and installed in the game's mods/languages/<code> as the port's
 * tools/mods/get.py installs it. `language=<code>` in settings.txt plays the game in it.
 * Apart from Android so it can be tested on a desktop JDK.
 */
final class LanguagePack {
    final String code, name, release, url, file, md5;
    final long size;

    LanguagePack(String code, String name, String release, String url, String file, String md5, long size) {
        this.code = code; this.name = name; this.release = release; this.url = url; this.file = file; this.md5 = md5; this.size = size;
    }

    static File installDir(File game, String code) { return new File(game, "mods/languages/" + code); }

    /** Whether this release of the pack is installed. */
    boolean installed(File game) {
        try (InputStream in = new FileInputStream(new File(installDir(game, code), ".release"))) {
            return md5.equals(TexturePackLayout.releaseMd5(new String(readAll(in), StandardCharsets.UTF_8)));
        } catch (IOException e) { return false; }
    }

    /** About how big the download is, as "940 KB" or "1.2 MB". */
    String sizeText() {
        return size < 1000L * 1024 ? (size + 1023) / 1024 + " KB" : String.format(Locale.ROOT, "%.1f MB", size / 1048576.0);
    }

    /** Downloads the pack, checks it is the pinned release, and installs it in place of any earlier one. */
    void install(File game, Consumer<String> progress) throws IOException {
        File languages = installDir(game, code).getParentFile();
        languages.mkdirs();
        long need = 3 * size + (16L << 20), free = languages.getUsableSpace();
        if (need > free)
            throw new IOException("The " + name + " language pack needs " + need / 1048576 + " MB of free space, and "
                + free / 1048576 + " MB is free.");
        File archive = new File(languages, "." + code + ".zip.part");
        try {
            download(archive, progress);
            progress.accept("Checking the " + name + " language pack…");
            if (!md5.equals(md5(archive)))
                throw new IOException("The " + name + " language pack did not match the expected release. Try again.");
            progress.accept("Installing the " + name + " language pack…");
            installArchive(game, archive);
        } finally { archive.delete(); }
    }

    private void download(File part, Consumer<String> progress) throws IOException {
        URLConnection c = new URL(url).openConnection();
        try {
            c.setConnectTimeout(30000); c.setReadTimeout(60000);
            c.setRequestProperty("User-Agent", "SMS Launcher for Android");
            if (c instanceof HttpURLConnection) {
                int status = ((HttpURLConnection) c).getResponseCode();
                if (status != HttpURLConnection.HTTP_OK) throw new IOException("The download server answered " + status + ".");
            }
            try (InputStream in = c.getInputStream(); OutputStream out = new FileOutputStream(part)) {
                byte[] buf = new byte[1 << 16];
                long have = 0, shown = 0;
                for (int n; (n = in.read(buf)) > 0; ) {
                    out.write(buf, 0, n);
                    have += n;
                    if (have > size) throw new IOException("The " + name + " language pack is larger than expected.");
                    long now = System.nanoTime();
                    if (now - shown > 250_000_000L) {
                        shown = now;
                        progress.accept("Downloading " + name + "… " + have / 1024 + " of " + size / 1024 + " KB");
                    }
                }
                if (have != size) throw new IOException("The " + name + " download stopped short. Try again.");
            }
        } finally { if (c instanceof HttpURLConnection) ((HttpURLConnection) c).disconnect(); }
    }

    /** Unpacks a downloaded pack (<code>/files/...) into mods/languages/<code>, with its .release record. */
    void installArchive(File game, File archive) throws IOException {
        File install = installDir(game, code), tmp = new File(install.getParentFile(), "." + code + ".part");
        erase(tmp);
        try (ZipInputStream zip = new ZipInputStream(new BufferedInputStream(new FileInputStream(archive)))) {
            byte[] buf = new byte[1 << 16];
            int count = 0;
            for (ZipEntry e; (e = zip.getNextEntry()) != null; ) {
                if (e.isDirectory()) continue;
                String path = TexturePackLayout.entryPath(e.getName(), code + "/files/");
                if (path == null) throw new IOException("The " + name + " language pack is not laid out as expected.");
                File f = new File(tmp, "files/" + path);
                f.getParentFile().mkdirs();
                try (OutputStream out = new FileOutputStream(f)) {
                    for (int n; (n = zip.read(buf)) > 0; ) out.write(buf, 0, n);
                }
                count++;
            }
            if (count == 0) throw new IOException("The " + name + " language pack is empty.");
            try (OutputStream out = new FileOutputStream(new File(tmp, ".release"))) {
                out.write(TexturePackLayout.releaseRecord(release + " (" + name + ")", file, md5).getBytes(StandardCharsets.UTF_8));
            }
            erase(install);
            if (!tmp.renameTo(install)) throw new IOException("The " + name + " language pack could not be installed.");
        } finally { erase(tmp); }
    }

    static String md5(File f) throws IOException {
        try (InputStream in = new FileInputStream(f)) {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] buf = new byte[1 << 16];
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

    private static void erase(File f) throws IOException {
        if (!f.exists()) return;
        if (f.isDirectory()) { File[] files = f.listFiles(); if (files == null) throw new IOException("Cannot read " + f.getName()); for (File child : files) erase(child); }
        if (!f.delete()) throw new IOException("Cannot remove " + f.getName());
    }
}
