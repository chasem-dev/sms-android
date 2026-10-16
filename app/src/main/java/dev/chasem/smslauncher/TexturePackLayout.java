package dev.chasem.smslauncher;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Where a texture pack archive's files go, apart from Android so it can be tested on a desktop JDK. */
final class TexturePackLayout {
    private TexturePackLayout() {}

    /**
     * The path below {@code prefix} that archive entry {@code name} unpacks to, or null for an entry
     * outside it. A name that would leave the install folder is refused.
     */
    static String entryPath(String name, String prefix) throws IOException {
        String path = name.replace('\\', '/');
        if (!path.startsWith(prefix)) return null;
        path = path.substring(prefix.length());
        while (path.endsWith("/")) path = path.substring(0, path.length() - 1);
        if (path.isEmpty()) return null;
        for (String part : path.split("/", -1))
            if (part.isEmpty() || part.equals(".") || part.equals(".."))
                throw new IOException("The download is not laid out as expected.");
        return path;
    }

    private static final Pattern MD5 = Pattern.compile("\"md5\"\\s*:\\s*\"([0-9a-fA-F]{32})\"");

    /** The MD5 a .release record (as tools/mods/get.py writes it) names, or null. */
    static String releaseMd5(String record) {
        Matcher m = MD5.matcher(record);
        return m.find() ? m.group(1).toLowerCase(java.util.Locale.ROOT) : null;
    }

    static String releaseRecord(String name, String file, String md5) {
        return "{\n  \"name\": \"" + name + "\",\n  \"file\": \"" + file + "\",\n  \"md5\": \"" + md5 + "\"\n}\n";
    }
}
