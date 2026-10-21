package dev.chasem.smslauncher;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * New versions on the GitHub releases page. Android won't let the launcher install its own
 * updates, so it says when one is out and links to it. A full release hears of full releases,
 * and a preview of previews too, as the desktop launcher's Stable and Beta channels do.
 * Apart from Android so it can be tested on a desktop JDK.
 */
final class UpdateCheck {
    static final String RELEASES = "https://api.github.com/repos/chasem-dev/sms-android/releases?per_page=30";
    // At most this often, as GitHub allows 60 requests an hour without signing in.
    static final long INTERVAL_MS = 6 * 3600 * 1000L;
    private static final Pattern TAG = Pattern.compile("v(\\d+)\\.(\\d+)\\.(\\d+)(?:-preview\\.(\\d+))?");

    static final class Release {
        final String tag, url;
        final boolean preview;
        Release(String tag, String url, boolean preview) { this.tag = tag; this.url = url; this.preview = preview; }
        String version() { return tag.substring(1); }
    }

    private UpdateCheck() {}

    /** The version code a release tag names, as app/build.gradle numbers versions (v1.1.1-preview.2 is 1010102), or -1. */
    static long versionCode(String tag) {
        Matcher m = TAG.matcher(tag == null ? "" : tag);
        if (!m.matches()) return -1;
        int minor = Integer.parseInt(m.group(2)), patch = Integer.parseInt(m.group(3)), preview = m.group(4) == null ? 0 : Integer.parseInt(m.group(4));
        if (minor > 99 || patch > 99 || preview > 99) return -1;
        return Long.parseLong(m.group(1)) * 1000000 + minor * 10000L + patch * 100L + preview;
    }

    /** Whether this build is a preview, whose players hear of newer previews too. */
    static boolean isPreview(String versionName) { return versionName.contains("-preview."); }

    /** The newest release above {@code installed} that this build should hear of, or null. */
    static Release newer(List<Release> releases, long installed, boolean previews) {
        Release best = null;
        for (Release r : releases) {
            long code = versionCode(r.tag);
            if (code <= installed || (r.preview && !previews)) continue;
            if (best == null || code > versionCode(best.tag)) best = r;
        }
        return best;
    }
}
