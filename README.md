<div align="center">
  <a href="https://popnow.ai">
    <picture>
      <source media="(prefers-color-scheme: dark)" srcset="https://raw.githubusercontent.com/popnow-ai/popnow-java/main/assets/popnow-wordmark-light.png">
      <source media="(prefers-color-scheme: light)" srcset="https://raw.githubusercontent.com/popnow-ai/popnow-java/main/assets/popnow-wordmark.png">
      <img alt="PopNow" src="https://raw.githubusercontent.com/popnow-ai/popnow-java/main/assets/popnow-wordmark.png" width="260">
    </picture>
  </a>

  <h1>PopNow Java SDK: AI Video, Image and Music Generation API for Java</h1>

  <p><strong>popnow-java</strong> is a Java 17+ client for the PopNow API that turns text, images and audio into AI videos, images, music and speech, using the same models, prices and credits as <a href="https://popnow.ai">popnow.ai</a>.</p>

  <p>
    <a href="LICENSE"><img alt="License: MIT" src="https://img.shields.io/badge/license-MIT-blue.svg"></a>
    <img alt="Java 17+" src="https://img.shields.io/badge/java-17%2B-b07219.svg?logo=openjdk&logoColor=white">
    <a href="https://github.com/popnow-ai/popnow-java/actions/workflows/ci.yml"><img alt="CI" src="https://github.com/popnow-ai/popnow-java/actions/workflows/ci.yml/badge.svg"></a>
    <a href="https://popnow.ai"><img alt="Website: popnow.ai" src="https://img.shields.io/badge/site-popnow.ai-ff5a36.svg"></a>
    <a href="https://popnow.ai/docs"><img alt="Docs" src="https://img.shields.io/badge/docs-popnow.ai%2Fdocs-111111.svg"></a>
  </p>
</div>

PopNow is a browser-based AI studio for videos, images and music: image-to-video, text-to-video, AI images, music and speech, one-tap trending video effects and image styles. This package wraps its developer API (`https://api.popnow.ai/api/v1`) so you can call Kling, Seedance, Wan, Vidu, MiniMax, Nano Banana, GPT Image, Flux, Suno and more from Java, Kotlin or any JVM language. Credit packs, no subscription; failed runs are refunded. *Make it pop.*

- **One dependency.** Jackson databind (2.12 or newer, not shaded, so your version wins) and the JDK's own `java.net.http.HttpClient`.
- **Safe retries.** Back-off with jitter on `429` / `5xx`, honouring `Retry-After`, and only for requests that cannot charge twice: task submissions are retried only under an `Idempotency-Key`.
- **The whole task workflow.** Quote, create (optionally waiting server-side), poll with back-off, retry, purge, task history, usage and balance, all in credits and USD.
- **Files and the asset library.** Streamed three-step uploads, LoRA import from Hugging Face or Civitai, `popnow://f/` and `popnow://a/` URIs, saved assets and characters.
- **Webhook verification** for task callbacks and for Standard Webhooks account endpoints.
- **Contract-tested** against a pinned copy of the [published OpenAPI spec](https://popnow.ai/docs/openapi.yaml).

## Quickstart

**1. Install** from Maven Central (JDK 17+):

```xml
<dependency>
  <groupId>ai.popnow</groupId>
  <artifactId>popnow-java</artifactId>
  <version>0.1.0</version>
</dependency>
```

```kotlin
// build.gradle.kts
dependencies {
    implementation("ai.popnow:popnow-java:0.1.0")
}
```

**2. Create an API key** in the [API console](https://popnow.ai/api/console/keys) and export it:

```bash
export POPNOW_API_KEY="sk-popnow-..."
```

**3. Generate an image:**

```java
import ai.popnow.*;
import java.util.Map;
import java.util.UUID;

public class Quickstart {
    public static void main(String[] args) {
        PopNowClient client = PopNowClient.builder().apiKeyFromEnvironment().build(); // POPNOW_API_KEY

        CreateTaskRequest request = CreateTaskRequest.of("alibaba/z-image-turbo/text-to-image",
                Map.of("prompt", "A small red fox curled up in a teacup, soft morning light"));

        // One key per piece of work, reused on every resend: a repeat returns the original
        // task instead of generating (and charging) twice.
        TaskRecord task = client.run(request, UUID.randomUUID().toString()); // createTask + waitForTerminal

        System.out.println(task.state() + " for " + task.credits() + " credits");
        for (TaskAsset asset : task.assets()) {
            asset.url().ifPresent(System.out::println); // a signed link, valid for about 20 minutes
        }
    }
}
```

## What it covers

| Area | Methods | Endpoint |
| --- | --- | --- |
| Models | `listModels`, `getModel` | `GET /api/v1/models`, `GET /api/v1/models/{model}` |
| Pricing | `quote` | `POST /api/v1/jobs/quote` |
| Tasks | `createTask`, `getTask`, `waitForTerminal`, `run`, `retryTask`, `purgeTask` | `POST /jobs/createTask`, `GET /jobs/recordInfo`, `POST /jobs/retry`, `POST /jobs/purge` |
| History | `listTasks` | `GET /api/v1/jobs` |
| Account | `getBalance`, `getUsage` | `GET /api/v1/chat/credit`, `GET /api/v1/usage` |
| Files | `uploadFile`, `uploadBytes`, `createUploadUrl`, `putUploadBytes`, `commitUploadedFile`, `importLora`, `createDownloadUrl` | `/common/upload-url`, `/files/{id}/commit`, `/files/import`, `/common/download-url` |
| Asset library | `assets().create`, `uploadFile`, `list`, `listAll`, `get`, `update`, `delete`, `usage`, `characters().list` / `get` | `/api/v1/assets*` |
| Webhooks | `CallbackSignatureVerifier`, `EndpointWebhookVerifier` | task callbacks and account endpoints |

Models include Kling, Seedance, Wan, Vidu, MiniMax, Nano Banana, GPT Image, Flux, Qwen Image, Seedream, Suno and more across text-to-image, image editing, text-to-video, image-to-video, reference-to-video, video extend, upscaling, text-to-speech, music and speech-to-text. The live list, with each model's input schema and credit price, is `listModels`:

```java
ModelPage models = client.listModels(ModelFilter.all().withModality("video").withTask("image-to-video"));
for (ApiModel m : models.items()) {
    if (m.callable() && m.credits() != null) {
        System.out.println(m.model() + "  " + m.credits().perUnit() + " credits " + m.credits().unit());
    }
}

// getModel always includes the JSON Schema that validates the input.
ApiModel wan = client.getModel("alibaba/wan-3.0/image-to-video");
System.out.println(wan.inputSchema().toPrettyString());
```

## Show the price first

`quote` runs the same validation as `createTask` but creates nothing and holds nothing. `withQuote` binds the submission to that price (`quoteId` plus `maxCredits`), so a price that moved is refused with code `40901` instead of charged.

```java
CreateTaskRequest request = CreateTaskRequest.of("alibaba/wan-3.0/image-to-video", Map.of(
        "image_url", "https://media.popnow.ai/movie/lighthouse.webp",
        "prompt", "Waves crash against the lighthouse, slow push in",
        "resolution", "480p",
        "duration_seconds", 5));

TaskQuote quote = client.quote(request);
System.out.println("at most " + quote.maxCredits() + " credits ($" + quote.maxCharge() + ")");

String key = UUID.randomUUID().toString();               // store it with your job before sending
AcceptedTask accepted = client.createTask(request.withQuote(quote), key);
System.out.println("accepted " + accepted.taskId() + ", holding " + accepted.estimatedCredits() + " credits");
```

1 credit = $0.01. The hold is the ceiling: unused credits are released when the task settles, and failed or expired tasks release the hold in full.

## Skip the first poll

`CreateTaskOptions.withWait` asks the server to hold the connection open until the task finishes (up to 60 seconds), so a quick model returns its result with the submission.

```java
AcceptedTask accepted = client.createTask(request, key,
        CreateTaskOptions.none().withWait(Duration.ofSeconds(30)));

// completed() is empty whenever the wait ran out. That is the ordinary outcome, not an
// error: the generation is still running, so poll from here.
TaskRecord task = accepted.completed().orElseGet(() -> client.waitForTerminal(accepted.taskId()));
if (!task.succeeded()) {
    System.out.println("failed: " + task.failure().orElse(null) + " " + task.errorMessage());
}
```

**It is an optimisation, never a guarantee**: always check `completed()`. A terminal state is not necessarily a success, either: `failed` and `expired` end the wait too, with `errorCode` set. `waitForTerminal` throws `PopNowWaitTimeoutException` when its local deadline passes; that is not a verdict, the task is still running, and the exception carries its id.

## Start from a local file

Image-to-video, image editing and reference models need your material on PopNow first. `uploadFile` streams the file (ticket, `PUT` to storage, commit) and returns a `popnow://f/` URI for task input. A public HTTPS URL can be passed directly instead.

```java
UploadedFile uploaded = client.uploadFile(Path.of("/path/to/reference.png"));
// uploaded.uri() -> "popnow://f/fil_..."

TaskRecord clip = client.run(CreateTaskRequest.of("alibaba/wan-3.0/image-to-video",
        Map.of("image_url", uploaded.uri(), "prompt", "Slow dolly in", "resolution", "480p")),
        UUID.randomUUID().toString());

// LoRA weights: upload a .safetensors file, or import one from a link.
UploadedFile lora = client.importLora("owner/repo", null);  // optional token for gated files
System.out.println(lora.uri());                              // goes in loras[].path
```

Accepted: JPEG, PNG, WebP and GIF images (10 MiB), MP4/WebM video and MP3/WAV audio (90 MiB), reference documents (PDF, Word, Excel, PowerPoint, Keynote, Pages, Numbers, plain text, Markdown) and `.safetensors` LoRA weights (2 GiB, sent as `application/octet-stream`). The type is inferred from the extension, or pass an `UploadContentType`. Oversized files are refused locally before a ticket is requested. A 2 GiB file needs a longer `uploadTimeout` than the 10-minute default unless the link sustains about 30 Mbps.

## Keep results in the asset library

Output links expire after about 20 minutes. To keep a result, save it to the asset library: it gets a stable `popnow://a/` URI that works in any media field of later requests made with keys of the same workspace.

```java
PopNowAssets assets = client.assets();

// The required Idempotency-Key is generated for you; pass your own as a second argument to
// replay a save after an uncertain response.
Asset saved = assets.create(CreateAssetRequest.fromTaskOutput(clip.taskId(), 0).withName("Lighthouse clip"));
System.out.println(saved.uri());                          // "popnow://a/ast_..."

// Every saved video, newest first, across all pages. Breaking out of the loop stops fetching.
for (Asset asset : assets.listAll(AssetFilter.all().withKind(AssetKind.VIDEO))) {
    System.out.println(asset.name() + " " + asset.uri());
}
```

Storage unlocks with your lifetime credit purchases; `assets().usage()` shows the plan and limits. Without a top-up, saving fails with `PopNowApiException.assetErrorReason()` equal to `ASSET_LIBRARY_LOCKED`. A personal key reaches your personal library, a team key that team's shared library. Characters (named, ordered image sets arranged on the website) are read with `assets().characters()`; put `character.uris()` into a model's reference image field.

## Webhooks instead of polling

Set `withCallBackUrl("https://...")` (public HTTPS) on the request and PopNow posts the finished task to it. Verify every delivery against the **raw** body with your webhook signing secret from the [API console](https://popnow.ai/api/console/webhooks):

```java
CallbackSignatureVerifier verifier =
        new CallbackSignatureVerifier(System.getenv("POPNOW_WEBHOOK_SECRET"));

// In a servlet, Spring controller or any handler that gives you the exact bytes and the headers:
byte[] body = rawRequestBody;                              // never a re-serialized copy
WebhookDelivery delivery = verifier.verify(body, headerName -> headers.get(headerName));
// Acknowledge with 2xx within 15 seconds, then do the work.
// deliveryId() is stable across retries: store it to ignore duplicates.
delivery.task().ifPresent(t -> System.out.println(delivery.deliveryId().orElse("") + " " + t.state()));
```

A failed check throws `PopNowWebhookException` with a `reason()`; answer `401` and drop the delivery. The signature is `base64(HMAC-SHA256(secret, "<taskId>.<timestamp>.<sha256_hex(body)>"))`, compared in constant time, and the timestamp must be within five minutes.

Account endpoints registered in the console use the [Standard Webhooks](https://www.standardwebhooks.com/) format and their own `whsec_` secret; verify those with `new EndpointWebhookVerifier(secret).verify(body, headerLookup)`, which also accepts the two signatures sent during a secret rotation and returns an `EndpointEvent` with `type()` and `task()`.

## Balance, usage and history

```java
Balance balance = client.getBalance();                    // the account's wallet
System.out.println(balance.availableCredits() + " credits available, " + balance.heldCredits() + " held");

UsageReport usage = client.getUsage(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 8)); // this key, [from, to)
System.out.println(usage.totalCalls() + " calls, " + usage.totalCredits() + " credits");

// Pin the dates before walking pages, then move only the cursor.
TaskFilter filter = TaskFilter.all().withRange(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 8));
TaskPage page = client.listTasks(filter);
while (true) {
    page.items().forEach(item -> System.out.println(item.taskId() + " " + item.state() + " " + item.credits()));
    if (!page.hasMore() || page.nextCursor() == null) break;
    page = client.listTasks(filter.withCursor(page.nextCursor()));
}
```

Usage and history are scoped to **this API key**: other keys on the account, and generations started on the website, are not included. Dates are UTC calendar dates in a half-open interval `[from, to)` of at most 92 days. `getUsage` is for reconciliation, not progress: it counts settled charges only and shares an account-wide limit of 30 requests a minute.

## Errors and retries

Every failure is a `PopNowException`, which is sealed, so a `switch` over it is exhaustive:

| Type | Means |
| --- | --- |
| `PopNowApiException` | the API answered, and the answer was no |
| `PopNowProtocolException` | something answered, but not with the API's envelope (a proxy, a captive portal, a wrong base URL) |
| `PopNowTransportException` | no usable answer: DNS, TLS, a dropped connection |
| `PopNowTimeoutException` | a local request budget elapsed; the outcome is unknown, not "did not happen" |
| `PopNowWaitTimeoutException` | polling gave up; the task is still running and carries its id |
| `PopNowUploadException` | object storage refused the presigned `PUT` |
| `PopNowWebhookException` | an inbound delivery was not authentic, fresh and well-formed |

```java
try {
    client.createTask(request, key);
} catch (PopNowApiException failure) {
    System.out.println(failure.describe());               // status, code, request id, reason, advice
    if (failure.businessCode() == 40201) {
        System.out.println("buy credits at https://popnow.ai/pricing");
    }
}
```

Branch on `businessCode()`, never on the message: messages can be translated (`Builder.errorLanguage`), codes are not.

| Code | HTTP | Meaning | What this client does / what to do |
| --- | --- | --- | --- |
| `400` | 400 | invalid request or input; `msg` names the problem | fix the input |
| `40003` | 400 | uploaded bytes do not match their ticket | upload again with a new ticket |
| `40004` | 400 | valid, but this combination of settings cannot be served now | change the named setting |
| `401` | 401 | missing, unknown or revoked key | check `POPNOW_API_KEY` |
| `40201` | 402 | not enough credits for the hold | buy credits, resend |
| `40202` | 402 | a key spend cap was reached | raise the cap in the console |
| `40301` / `40302` / `40303` | 403 | key not allowed this model / IP not allowed / region not offered | adjust the key |
| `40310` | 403 | account email not verified | open the verification link |
| `404` | 404 | not found, or another key's resource | - |
| `409` / `40901` | 409 | idempotency conflict / quote expired or price changed | new key / quote again |
| `429` | 429 | too many requests | retried automatically after `Retry-After` |
| `500` | 500 | server error | retried automatically with back-off |
| `503` / `50301` | 503 | dependency or model briefly unavailable | retried automatically |
| `50302` | 503 | a synchronous generation failed and was refunded | not retried: resend with a **new** idempotency key |

Automatic retries apply only to requests that are safe to repeat: reads, quotes, purges, commits and submissions that carry an idempotency key. Upload tickets and LoRA imports are never resent. A failed generation shows up as a task with `state() == FAILED` and an `errorCode` such as `content_rejected`, `invalid_asset` or `timeout`, not as an exception.

## Configuration

| Builder method | Default |
| --- | --- |
| `apiKey` / `apiKeyFromEnvironment` | `POPNOW_API_KEY` |
| `baseUrl` / `baseUrlFromEnvironment` | `https://api.popnow.ai/api/v1` (`POPNOW_API_BASE_URL`; HTTPS only, HTTP allowed for loopback) |
| `httpClient` | a JDK `HttpClient` that never follows redirects |
| `errorLanguage` | `en` (also `ja`, `ko`, `de`, `fr`, `es`, `pt-BR`, `ru`, `it`, `pl`) |
| `connectTimeout` / `requestTimeout` / `uploadTimeout` / `waitTimeout` | 10 s / 30 s / 10 min / 10 min |
| `pollInterval` / `retryDelays` | 2 s to 15 s / 0.5 s to 8 s |
| `maxRetries` | 3 |

Every request sends `User-Agent: popnow-java/0.1.0 (+https://github.com/popnow-ai/popnow-java)`. Instances are immutable and thread-safe; build one per process. On the module path the module is `ai.popnow`. Keep the API key on your server: never ship it inside an Android app or a desktop binary.

## FAQ

### How do I generate a video from an image with an API in Java?

Upload the image with `client.uploadFile(Path.of("photo.png"))` (or use a public HTTPS URL), then call `client.run(...)` with an image-to-video model such as `alibaba/wan-3.0/image-to-video` and an input containing `image_url`, `prompt`, `resolution` and `duration_seconds`. Read the video link from `task.assets().get(0).url()`.

### How much does a PopNow API call cost?

Each model has a credit price (1 credit = $0.01), shown by `listModels` in `ApiModel.credits()` and on [popnow.ai/pricing](https://popnow.ai/pricing). `client.quote(request)` returns the exact `maxCredits` for one request without spending anything. API calls and the website share one wallet and one price list. Failed tasks are refunded.

### Which AI models can I call from Java?

Every image, video and audio model on PopNow: Kling, Seedance, Wan, Vidu, MiniMax, Nano Banana, GPT Image, Flux, Qwen Image, Seedream, Suno and more. `client.listModels()` returns the live list with input schemas.

### How do I avoid paying twice when a request times out?

Pass the same idempotency key on every resend of the same piece of work. For 24 hours a repeat returns the original task instead of creating a new one. Without a key, `createTask` is never retried automatically, because a lost response does not prove the task was not created.

### Do I have to poll, or can PopNow call my server?

Either. Set a callback URL with `withCallBackUrl` and verify each delivery with `CallbackSignatureVerifier`; or use `CreateTaskOptions.withWait` to have the server hold the request open for quick models; or poll with `waitForTerminal`, which backs off for you.

### How long are generated files kept?

Signed output links last about 20 minutes; read the task again or call `createDownloadUrl` for a fresh one. Outputs follow the retention shown in `TaskRecord.retention()` (shorten it per task with `CreateTaskOptions.withRetention`). To keep a result indefinitely, save it with `assets().create`.

### Is the SDK on Maven Central?

Yes: [`ai.popnow:popnow-java`](https://central.sonatype.com/artifact/ai.popnow/popnow-java) on Maven Central, signed with the PopNow release key.

### Is there an MCP server for ChatGPT or Claude?

Yes: a remote MCP server at `https://api.popnow.ai/mcp` exposes image, video, music and speech tools. See [popnow.ai/mcp](https://popnow.ai/mcp) and [popnow-mcp](https://github.com/popnow-ai/popnow-mcp).

## Development

```bash
mvn -B verify
```

The tests run against a local `com.sun.net.httpserver` stub and need no API key. `ContractDriftTest` compares `contracts/openapi.yaml` with the published spec (set `POPNOW_SKIP_LIVE_CONTRACT=1` offline) and re-checks every enum this package hard-codes against it; `RepositoryGuardTest` keeps every tracked file English-only. `mvn -B -Prelease -Dgpg.skip verify` also builds the Javadoc and sources jars.

## Links

- Website: [popnow.ai](https://popnow.ai)
- Docs: [popnow.ai/docs](https://popnow.ai/docs)
- API reference (OpenAPI): [popnow.ai/docs/openapi.yaml](https://popnow.ai/docs/openapi.yaml)
- Pricing: [popnow.ai/pricing](https://popnow.ai/pricing)
- MCP server: [popnow.ai/mcp](https://popnow.ai/mcp)
- llms.txt: [popnow.ai/llms.txt](https://popnow.ai/llms.txt)
- Other PopNow SDKs: [Python](https://github.com/popnow-ai/popnow-python), [JavaScript / TypeScript](https://github.com/popnow-ai/popnow-js), [Go](https://github.com/popnow-ai/popnow-go), [PHP](https://github.com/popnow-ai/popnow-php)
- More PopNow repos: [popnow-mcp](https://github.com/popnow-ai/popnow-mcp), [popnow-skills](https://github.com/popnow-ai/popnow-skills), [popnow-api](https://github.com/popnow-ai/popnow-api), [popnow-cli](https://github.com/popnow-ai/popnow-cli), [popnow-blender](https://github.com/popnow-ai/popnow-blender), [popnow-comfyui](https://github.com/popnow-ai/popnow-comfyui), [n8n-nodes-popnow](https://github.com/popnow-ai/n8n-nodes-popnow)

## Contributing

Issues and pull requests are welcome; see [CONTRIBUTING.md](CONTRIBUTING.md). To report a vulnerability, see [SECURITY.md](SECURITY.md).

## License

[MIT](LICENSE) © PopNow
