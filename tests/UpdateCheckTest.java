package dev.chasem.smslauncher;

import java.util.Arrays;
import java.util.List;

public final class UpdateCheckTest {
    private static int failures;
    private static void expect(boolean ok, String what) {
        System.out.println((ok ? "PASS " : "FAIL ") + what);
        if (!ok) failures++;
    }
    private static UpdateCheck.Release release(String tag, boolean preview) {
        return new UpdateCheck.Release(tag, "https://github.com/chasem-dev/sms-android/releases/tag/" + tag, preview);
    }
    private static String newer(List<UpdateCheck.Release> releases, String installedTag, boolean previews) {
        UpdateCheck.Release r = UpdateCheck.newer(releases, UpdateCheck.versionCode(installedTag), previews);
        return r == null ? null : r.tag;
    }

    public static void main(String[] args) {
        expect(UpdateCheck.versionCode("v1.1.0") == 1010000, "a full release's tag reads as its version code");
        expect(UpdateCheck.versionCode("v1.1.1-preview.2") == 1010102, "a preview's tag reads as its version code");
        expect(UpdateCheck.versionCode("v0.2.0-preview.11") == 20011, "an early preview's tag reads");
        expect(UpdateCheck.versionCode("1.1.0") == -1 && UpdateCheck.versionCode("v1.1") == -1
            && UpdateCheck.versionCode("v1.1.0-beta") == -1 && UpdateCheck.versionCode(null) == -1, "other tags are not versions");
        expect(UpdateCheck.versionCode("v1.100.0") == -1, "a number past 99 is not a version");
        expect(UpdateCheck.isPreview("1.1.1-preview.3") && !UpdateCheck.isPreview("1.1.0"), "preview builds are told apart by name");
        expect("1.1.1-preview.2".equals(release("v1.1.1-preview.2", true).version()), "a release's version has no v");

        List<UpdateCheck.Release> releases = Arrays.asList(release("v1.1.1-preview.2", true), release("v1.1.1-preview.1", true),
            release("v1.1.0", false), release("v1.0.1-preview.1", true), release("v1.0.0", false), release("nightly", true));
        expect(newer(releases, "v1.1.0", false) == null, "a full release does not hear of previews");
        expect("v1.1.0".equals(newer(releases, "v1.0.0", false)), "an older full release hears of the newest full release");
        expect("v1.1.1-preview.2".equals(newer(releases, "v1.0.1-preview.1", true)), "a preview hears of the newest preview");
        expect("v1.1.1-preview.2".equals(newer(releases, "v1.1.1-preview.1", true)), "a preview hears of the next preview");
        expect(newer(releases, "v1.1.1-preview.2", true) == null, "the newest preview hears of nothing");
        expect("v1.1.0".equals(newer(Arrays.asList(release("v1.0.1-preview.1", true), release("v1.1.0", false)), "v1.0.1-preview.1", true)),
            "a preview hears of the full release that follows it");
        expect(newer(releases, "v1.2.0-preview.1", true) == null, "a build newer than every release hears of nothing");
        System.out.println("Update check tests: " + failures + " failures");
        if (failures != 0) System.exit(1);
    }
}
