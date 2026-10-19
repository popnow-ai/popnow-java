package ai.popnow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Contract drift check: every contract fact hard-coded in this artifact must still match the
 * published contract.
 *
 * <p>Why it has to exist: the enums, error codes and limits in an SDK are all constants copied from
 * the contract. When the contract changes and these do not, nothing raises an error - requests
 * still go out, responses still parse, it is only that some new option gets rejected locally as an
 * illegal value, or some new error code is flattened into "unknown error". This kind of failure
 * emits no signal.
 *
 * <p>It compares against the published contract, which is public, so it needs no credentials at
 * all. Set {@code POPNOW_SKIP_LIVE_CONTRACT=1} to skip the network comparison when offline; the
 * enum checks against the pinned copy always run.
 */
class ContractDriftTest {

    private static final String LIVE_URL = "https://popnow.ai/docs/openapi.yaml";
    private static final Path PINNED = Path.of("contracts", "openapi.yaml");

    @Test
    @DisplayName("the pinned contract is byte-identical to the published one")
    void pinnedContractIsCurrent() throws Exception {
        assumeTrue(System.getenv("POPNOW_SKIP_LIVE_CONTRACT") == null,
                "POPNOW_SKIP_LIVE_CONTRACT is set");
        String live = fetchLiveContract();
        assumeTrue(live != null, "the published contract is unreachable; run with network access to verify drift");

        assertEquals(stripLeadingComments(live), Files.readString(PINNED, StandardCharsets.UTF_8),
                "contracts/openapi.yaml is stale against the published contract.\n"
                        + "refresh it:  curl -sH 'User-Agent: popnow-java-contract-check' " + LIVE_URL
                        + " | sed '/^#/d' > contracts/openapi.yaml\n"
                        + "then re-check every constant this package hard-codes against that diff");
    }

    @Test
    @DisplayName("TaskState matches TaskRecord.state in the contract")
    void taskStatesMatch() throws Exception {
        // UNKNOWN has an empty wire value; it is this client's own fallback rather than part of
        // the contract, so filter it out first.
        Set<String> ours = Arrays.stream(TaskState.values())
                .map(TaskState::wireValue)
                .filter(value -> !value.isEmpty())
                .collect(Collectors.toCollection(TreeSet::new));

        assertEquals(enumAfter("    TaskRecord:", "state:"), ours);
    }

    @Test
    @DisplayName("UploadContentType matches UploadURLRequest.contentType in the contract")
    void uploadContentTypesMatch() throws Exception {
        Set<String> ours = Arrays.stream(UploadContentType.values())
                .map(UploadContentType::mediaType)
                .collect(Collectors.toCollection(TreeSet::new));

        assertEquals(enumAfter("    UploadURLRequest:", "contentType:"), ours);
    }

    @Test
    @DisplayName("AssetErrorReason matches the asset_* values of ErrorEnvelope.data.reason in the contract")
    void assetErrorReasonsMatch() throws Exception {
        Set<String> ours = Arrays.stream(AssetErrorReason.values())
                .map(AssetErrorReason::wireValue)
                .collect(Collectors.toCollection(TreeSet::new));
        // The envelope's reason set also covers failures outside the asset library; those reach
        // callers verbatim through PopNowApiException.reason() and are not part of this enum.
        Set<String> theirs = enumAfter("    ErrorEnvelope:", "reason:").stream()
                .filter(value -> value.startsWith("asset_"))
                .collect(Collectors.toCollection(TreeSet::new));

        assertEquals(theirs, ours);
    }

    @Test
    @DisplayName("AssetSort matches the sort parameter of listAssets in the contract")
    void assetSortsMatch() throws Exception {
        Set<String> ours = Arrays.stream(AssetSort.values())
                .map(AssetSort::wireValue)
                .collect(Collectors.toCollection(TreeSet::new));

        assertEquals(enumAfter("operationId: listAssets", "name: sort"), ours);
    }

    @Test
    @DisplayName("AssetKind and AssetSource match Asset.kind and Asset.source in the contract")
    void assetKindsAndSourcesMatch() throws Exception {
        Set<String> kinds = Arrays.stream(AssetKind.values())
                .map(AssetKind::wireValue)
                .filter(value -> !value.isEmpty())
                .collect(Collectors.toCollection(TreeSet::new));
        Set<String> sources = Arrays.stream(AssetSource.values())
                .map(AssetSource::wireValue)
                .filter(value -> !value.isEmpty())
                .collect(Collectors.toCollection(TreeSet::new));

        assertEquals(enumAfter("    Asset:", "kind:"), kinds);
        assertEquals(enumAfter("    Asset:", "source:"), sources);
        assertEquals(enumAfter("    AssetCreateRequest:", "source:"), sources);
    }

    @Test
    @DisplayName("the asset routes this client calls are all in the pinned contract")
    void assetRoutesExist() throws Exception {
        String contract = Files.readString(PINNED, StandardCharsets.UTF_8);
        for (String route : new String[] {"  /api/v1/assets:", "  /api/v1/assets/usage:",
                "  /api/v1/assets/{assetId}:", "  /api/v1/assets/entities:", "  /api/v1/assets/entities/{entityId}:"}) {
            assertTrue(contract.contains("\n" + route + "\n"), "the contract has no " + route.trim() + " route");
        }
    }

    @Test
    @DisplayName("a dropped enum value is caught, and the failure names the missing value")
    void theCheckCanGoRed() throws Exception {
        // The counter-proof. A check that can never go red is not a check, and the only way to
        // answer "would it go red" is to actually hand it a contract with an entry removed.
        String original = Files.readString(PINNED, StandardCharsets.UTF_8);
        int record = original.indexOf("    TaskRecord:");
        int expired = original.indexOf("            - expired\n", record);
        String mutilated = original.substring(0, expired)
                + original.substring(expired + "            - expired\n".length());
        assertTrue(mutilated.length() < original.length(), "the mutation has to remove something");
        Set<String> theirs = enumAfter(mutilated, "    TaskRecord:", "state:");
        Set<String> ours = Arrays.stream(TaskState.values())
                .map(TaskState::wireValue)
                .filter(value -> !value.isEmpty())
                .collect(Collectors.toCollection(TreeSet::new));

        AssertionError failure = assertThrows(AssertionError.class, () -> assertEquals(theirs, ours));
        assertTrue(failure.getMessage().contains("expired"), "the diff has to name what moved");
    }

    private static Set<String> enumAfter(String anchor, String field) throws IOException {
        return enumAfter(Files.readString(PINNED, StandardCharsets.UTF_8), anchor, field);
    }

    private static Set<String> enumAfter(String contract, String anchor, String field) {
        int start = contract.indexOf(anchor);
        if (start < 0) {
            throw new AssertionError("the contract has no " + anchor.trim() + " schema");
        }
        String window = contract.substring(start, Math.min(start + 6000, contract.length()));
        int at = window.indexOf(field);
        if (at < 0) {
            throw new AssertionError(anchor.trim() + " has no " + field + " property");
        }
        String fieldBlock = window.substring(at);
        int enumAt = fieldBlock.indexOf("enum:\n");
        if (enumAt < 0 || enumAt > 1500) {
            throw new AssertionError(anchor.trim() + " " + field + " has no enum");
        }
        // Block-style YAML: one "- value" line per entry at a single indentation; the first line
        // that is not one ends the list.
        Set<String> values = new TreeSet<>();
        int dashIndent = -1;
        for (String line : fieldBlock.substring(enumAt + "enum:\n".length()).split("\n")) {
            String trimmed = line.stripLeading();
            int indent = line.length() - trimmed.length();
            if (!trimmed.startsWith("- ") || (dashIndent >= 0 && indent != dashIndent)) {
                break;
            }
            dashIndent = indent;
            values.add(trimmed.substring(2).trim().replaceAll("^['\"]|['\"]$", ""));
        }
        if (values.isEmpty()) {
            throw new AssertionError(anchor.trim() + " " + field + " has an empty enum");
        }
        return values;
    }

    // The published file starts with generated-header comment lines that the pinned copy leaves
    // out; everything after them must match byte for byte.
    private static String stripLeadingComments(String text) {
        String rest = text;
        while (rest.startsWith("#")) {
            int newline = rest.indexOf('\n');
            rest = newline < 0 ? "" : rest.substring(newline + 1);
        }
        return rest;
    }

    /**
     * Fetches the published contract, returning null when it cannot be reached.
     *
     * <p><b>A User-Agent is mandatory</b>: the site's edge protection answers 403 to requests with
     * no UA or a default one. Without that one header this test goes red in the name of "contract
     * drift" for a reason that has nothing to do with the contract.
     */
    private static String fetchLiveContract() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(LIVE_URL))
                .header("User-Agent", "popnow-java-contract-check")
                .timeout(Duration.ofSeconds(30))
                .GET()
                .build();
        // No try-with-resources here: HttpClient only implements AutoCloseable from Java 21, and
        // this artifact's floor is 17, where that simply would not compile.
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new AssertionError("the published contract answered " + response.statusCode()
                        + "; a 403 here usually means the User-Agent header was dropped");
            }
            return response.body();
        } catch (IOException | InterruptedException unreachable) {
            if (unreachable instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return null;
        }
    }
}
