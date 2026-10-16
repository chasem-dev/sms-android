package dev.chasem.smslauncher;

import java.io.IOException;

public final class TexturePackLayoutTest {
    private static int failures;
    private static void expect(boolean ok, String what) {
        System.out.println((ok ? "PASS " : "FAIL ") + what);
        if (!ok) failures++;
    }
    private static boolean refused(String name, String prefix) {
        try { TexturePackLayout.entryPath(name, prefix); return false; }
        catch (IOException e) { return true; }
    }

    public static void main(String[] args) throws IOException {
        String uhd = "GMS/Textures/GMS/";
        expect("Delfino/tex1_64x64_abc_14.dds".equals(TexturePackLayout.entryPath("GMS/Textures/GMS/Delfino/tex1_64x64_abc_14.dds", uhd)),
            "a UHD pack file unpacks below GMS/Textures/GMS");
        expect("a/b.dds".equals(TexturePackLayout.entryPath("GMS\\Textures\\GMS\\a\\b.dds", uhd)),
            "backslash separators are read as folders");
        expect(TexturePackLayout.entryPath("GMS/Textures/GMS/", uhd) == null, "the install folder itself is not a file");
        expect("a".equals(TexturePackLayout.entryPath("GMS/Textures/GMS/a/", uhd)), "a folder entry loses its trailing slash");
        expect(TexturePackLayout.entryPath("GMS/readme.txt", uhd) == null, "files outside the pack folder are left out");
        expect(refused("GMS/Textures/GMS/../../evil.so", uhd), "a parent folder in a name is refused");
        expect(refused("GMS/Textures/GMS/a//b.dds", uhd), "an empty folder name is refused");
        expect(refused("GMS/./x.dds", "GMS/"), "a current folder in a name is refused");
        expect(TexturePackLayout.entryPath("/GMS/x.dds", "GMS/") == null, "an absolute name is outside the pack");
        String record = TexturePackLayout.releaseRecord("extras", "extras.zip", "a658c43cce0751e585e991f8563e5f41");
        expect("a658c43cce0751e585e991f8563e5f41".equals(TexturePackLayout.releaseMd5(record)), "a written release record reads back");
        expect("a658c43cce0751e585e991f8563e5f41".equals(TexturePackLayout.releaseMd5(
            "{\n  \"name\": \"x\",\n  \"file\": \"y\",\n  \"md5\": \"A658C43CCE0751E585E991F8563E5F41\"\n}")),
            "get.py's release record reads, whatever the MD5's case");
        expect(TexturePackLayout.releaseMd5("{\"name\": \"x\"}") == null, "a record without an MD5 is not current");
        System.out.println("Texture pack layout tests: " + failures + " failures");
        if (failures != 0) System.exit(1);
    }
}
