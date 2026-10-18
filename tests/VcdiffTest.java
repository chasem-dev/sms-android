package dev.chasem.smslauncher;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public final class VcdiffTest {
    private static int failures;
    private static void expect(boolean ok, String what) {
        System.out.println((ok ? "PASS " : "FAIL ") + what);
        if (!ok) failures++;
    }

    // Applied to "abcdefghij", this gives "abcdeXYZXYZXYZQQQQabcd": a window with an Adler-32
    // checksum that copies from the source, adds, copies over its own output and runs, then a
    // VCD_TARGET window copying from what the first wrote. The game's tools/mods/vcdiff.py
    // decodes it to the same.
    private static final int[] DELTA = {
        0xd6, 0xc3, 0xc4, 0x00, 0x00, 0x05, 0x0a, 0x00, 0x14, 0x12, 0x00, 0x04, 0x05, 0x02, 0x3e, 0x45,
        0x06, 0x55, 0x58, 0x59, 0x5a, 0x51, 0x15, 0x04, 0x16, 0x00, 0x04, 0x00, 0x0f, 0x02, 0x04, 0x00,
        0x07, 0x04, 0x00, 0x00, 0x01, 0x01, 0x24, 0x04,
    };
    private static final int DATA_X = 18, COPY_ADDRESS = 28;

    private static byte[] delta() {
        byte[] b = new byte[DELTA.length];
        for (int i = 0; i < b.length; i++) b[i] = (byte) DELTA[i];
        return b;
    }

    private static String apply(File dir, byte[] delta) throws IOException {
        File source = new File(dir, "source"), out = new File(dir, "out");
        Files.write(source.toPath(), "abcdefghij".getBytes(StandardCharsets.US_ASCII));
        Vcdiff.apply(source, new ByteArrayInputStream(delta), out, read -> { });
        return new String(Files.readAllBytes(out.toPath()), StandardCharsets.US_ASCII);
    }

    private static boolean refused(File dir, byte[] delta) {
        try { apply(dir, delta); return false; }
        catch (IOException e) { return true; }
    }

    public static void main(String[] args) throws IOException {
        File dir = Files.createTempDirectory("vcdiff").toFile();
        try {
            expect("abcdeXYZXYZXYZQQQQabcd".equals(apply(dir, delta())),
                "source, add, overlapping, run and target copies decode as vcdiff.py decodes them");

            byte[] d = delta(); d[DATA_X] = 'x';
            expect(refused(dir, d), "a window whose output fails its checksum is refused");
            d = delta(); d[0] = 0;
            expect(refused(dir, d), "a file that is not VCDIFF is refused");
            d = delta(); d[4] = 1;
            expect(refused(dir, d), "secondary compression is refused");
            d = delta(); d[COPY_ADDRESS] = 0x7f;
            expect(refused(dir, d), "a copy from past the data decoded so far is refused");
            expect(refused(dir, java.util.Arrays.copyOf(delta(), 30)), "a truncated delta is refused");
        } finally {
            File[] files = dir.listFiles();
            if (files != null) for (File f : files) f.delete();
            dir.delete();
        }
        if (failures > 0) { System.out.println(failures + " failed"); System.exit(1); }
    }
}
