package ai.popnow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Repository hygiene: every tracked text file is English, and no file names a third-party
 * execution backend.
 *
 * <p>Both checks read the files git tracks, and both carry a counter-check that enough files were
 * actually read, so a check that silently reads nothing cannot pass.
 */
class RepositoryGuardTest {

    // Han, Hiragana/Katakana and Hangul.
    private static final Pattern CJK = Pattern.compile("[\\x{4e00}-\\x{9fff}\\x{3040}-\\x{30ff}\\x{ac00}-\\x{d7af}]");

    // Assembled from parts so this file does not match its own scan.
    private static final Pattern VENDORS = Pattern.compile(String.join("|",
            "spi" + "cy", "k" + "ie\\.ai", "\\bk" + "ie\\b", "wave" + "speed", "atlas" + "cloud",
            "found" + "ation/"), Pattern.CASE_INSENSITIVE);

    private static final int MIN_FILES = 20;

    @Test
    @DisplayName("no tracked file contains CJK characters")
    void noCjk() throws Exception {
        List<String> offenders = scan(CJK);
        assertEquals(List.of(), offenders, "tracked files must be English only");
    }

    @Test
    @DisplayName("no tracked file names a third-party backend")
    void noVendorNames() throws Exception {
        List<String> offenders = scan(VENDORS);
        assertEquals(List.of(), offenders);
    }

    @Test
    @DisplayName("the scan can go red: a CJK sample is caught")
    void theScanCanGoRed() {
        assertTrue(CJK.matcher("cafe " + (char) 0x6f22).find());
        assertTrue(!CJK.matcher("" + (char) 0x0416 + (char) 0x03a9 + Character.toString(0x1f600)).find(),
                "Cyrillic, Greek and emoji are not CJK");
        assertTrue(VENDORS.matcher("api.K" + "ie.ai").find());
    }

    private static List<String> scan(Pattern pattern) throws Exception {
        List<String> files = trackedFiles();
        assumeTrue(files != null, "not a git checkout, or git is unavailable");
        assertTrue(files.size() >= MIN_FILES, "only " + files.size() + " tracked files were found");
        List<String> offenders = new ArrayList<>();
        int read = 0;
        for (String name : files) {
            Path file = Path.of(name);
            if (!Files.isRegularFile(file) || binary(name)) {
                continue;
            }
            String text = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
            read++;
            if (pattern.matcher(text).find()) {
                offenders.add(name);
            }
        }
        assertTrue(read >= MIN_FILES, "only " + read + " files were read; the scan proves nothing");
        return offenders;
    }

    private static boolean binary(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".webp") || lower.endsWith(".jar");
    }

    // The files git tracks, or null outside a checkout. Before the first commit the index is still
    // listed, so a freshly staged tree is checked too.
    private static List<String> trackedFiles() throws InterruptedException {
        try {
            Process git = new ProcessBuilder("git", "ls-files", "-z").redirectErrorStream(false).start();
            byte[] out = git.getInputStream().readAllBytes();
            if (git.waitFor() != 0) {
                return null;
            }
            List<String> names = new ArrayList<>();
            for (String name : new String(out, StandardCharsets.UTF_8).split("\0")) {
                if (!name.isEmpty()) {
                    names.add(name);
                }
            }
            return names;
        } catch (IOException noGit) {
            return null;
        }
    }
}
