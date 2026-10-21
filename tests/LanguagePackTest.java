package dev.chasem.smslauncher;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public final class LanguagePackTest {
    private static int failures;
    private static void expect(boolean ok, String what) {
        System.out.println((ok ? "PASS " : "FAIL ") + what);
        if (!ok) failures++;
    }
    private static File zip(File dir, String name, String... entries) throws IOException {
        File f = new File(dir, name);
        try (ZipOutputStream z = new ZipOutputStream(new FileOutputStream(f))) {
            for (String entry : entries) {
                z.putNextEntry(new ZipEntry(entry));
                if (!entry.endsWith("/")) z.write(entry.getBytes(StandardCharsets.UTF_8));
                z.closeEntry();
            }
        }
        return f;
    }
    private static LanguagePack pack(File archive) throws IOException {
        return new LanguagePack("fr", "Français", "Super Mario Sunshine language packs v1.1.0", archive.toURI().toString(),
            "sms-language-fr-1.1.0.zip", LanguagePack.md5(archive), archive.length());
    }
    private static boolean refused(LanguagePack pack, File game, File archive) {
        try { pack.installArchive(game, archive); return false; }
        catch (IOException e) { return true; }
    }

    public static void main(String[] args) throws IOException {
        File work = Files.createTempDirectory(new File(args[0]).toPath(), "language").toFile();
        File game = new File(work, "game");
        File good = zip(work, "good.zip", "fr/", "fr/files/", "fr/files/data/scene/bianco0.szs/map/message.bmg", "fr/files/data/title.arc");
        LanguagePack fr = pack(good);
        expect(!fr.installed(game), "a pack not yet installed is not installed");
        fr.install(game, text -> {});
        File install = LanguagePack.installDir(game, "fr");
        expect(new File(install, "files/data/scene/bianco0.szs/map/message.bmg").isFile(), "the pack's files unpack under mods/languages/fr/files");
        expect(new File(install, "files/data/title.arc").isFile(), "every file in the pack is installed");
        expect(fr.installed(game), "an installed pack with its release record is installed");
        expect(TexturePackLayout.releaseMd5(new String(Files.readAllBytes(new File(install, ".release").toPath()), StandardCharsets.UTF_8))
            .equals(fr.md5), "the release record names the download's MD5");
        String[] left = install.getParentFile().list();
        expect(left != null && left.length == 1, "the download and unpacking folders are cleaned up");

        LanguagePack newer = new LanguagePack("fr", fr.name, fr.release, fr.url, fr.file, "0123456789abcdef0123456789abcdef", fr.size);
        expect(!newer.installed(game), "a pack from another release is not current, so it downloads again");
        try { newer.install(game, text -> {}); expect(false, "a download with the wrong MD5 is refused"); }
        catch (IOException e) { expect(true, "a download with the wrong MD5 is refused"); }
        expect(fr.installed(game), "a refused download leaves the installed pack alone");

        File replacement = zip(work, "replacement.zip", "fr/files/data/other.bmg");
        pack(replacement).installArchive(game, replacement);
        expect(new File(install, "files/data/other.bmg").isFile() && !new File(install, "files/data/title.arc").exists(),
            "a new install replaces the earlier one");

        expect(refused(fr, game, zip(work, "outside.zip", "fr/files/a.bmg", "fr/readme.txt")), "a file outside <code>/files is refused");
        expect(refused(fr, game, zip(work, "other.zip", "de/files/a.bmg")), "another language's pack is refused");
        expect(refused(fr, game, zip(work, "escape.zip", "fr/files/../../../evil.so")), "a name leaving the pack is refused");
        expect(refused(fr, game, zip(work, "empty.zip", "fr/", "fr/files/")), "an empty pack is refused");
        expect(new File(install, "files/data/other.bmg").isFile(), "a refused pack leaves the installed one alone");

        expect("162 KB".equals(new LanguagePack("pl", "Polski", "", "", "", "", 165187).sizeText()), "a small pack's size reads in KB");
        expect("937 KB".equals(new LanguagePack("fr", "Français", "", "", "", "", 958617).sizeText()), "a pack under 1000 KB reads in KB");
        expect("1.2 MB".equals(new LanguagePack("xx", "X", "", "", "", "", 1258291).sizeText()), "a larger pack's size reads in MB");
        System.out.println("Language pack tests: " + failures + " failures");
        if (failures != 0) System.exit(1);
    }
}
