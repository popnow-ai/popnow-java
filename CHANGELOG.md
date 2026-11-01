# Changelog

All notable changes to this package are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and versions follow
[Semantic Versioning](https://semver.org/).

## [Unreleased]

## [0.2.0] - 2026-11-01

The PopNow API bills in exact USD from its own API balance, separate from website credits. This
release reads and confirms those USD amounts. It is backwards compatible: every 0.1 constructor,
accessor and credit field still works.

### Added

- `TaskQuote.maxChargeUsd()` (the exact hold, and the amount to confirm) and
  `TaskQuote.expectedChargeUsd()` (the typical charge, informational).
- `CreateTaskRequest.expectedCostUsd()`, sent as `expectedCostUsd`.
- `AcceptedTask.estimatedCostUsd()`, `TaskRecord.costUsd()`, and `Balance.availableUsd()`,
  `heldUsd()` and `totalUsd()`.
- `ApiModel.usd()` / `usdPrice()` with the new `ModelUsd` rate snapshot: unit, per-unit price,
  billing blocks, chat token prices, long-context tier and price rules, all as exact USD.

### Changed

- `CreateTaskRequest.withQuote(quote)` now sends `quoteId` and `expectedCostUsd` set to the quote's
  `maxChargeUsd`, and no longer sends `expectedCredits`.
- The `40201` remediation points to API billing (`https://popnow.ai/api/console/billing`), and the
  `40901` remediation to `expectedCostUsd`.
- README, llms.txt and Javadoc use USD and the separate API balance. MCP links point to the
  developer MCP server (`https://api.popnow.ai/api/v1/mcp`, `https://popnow.ai/api/mcp`,
  `popnow-ai/popnow-api-mcp`) and the agent skills page `https://popnow.ai/api/skills`.
- The pinned contract is the current published one. The drift check leaves out the few catalogue
  lines the repository guard keeps out of tracked files; none of them is a value this client
  hard-codes.

### Deprecated

- Credit fields, which are display projections and not billing units: `TaskQuote.maxCredits()` and
  `expectedCredits()`, `CreateTaskRequest.expectedCredits()`, `AcceptedTask.estimatedCredits()`,
  `TaskRecord.credits()`, `TaskSummary.credits()`, `Balance.availableCredits()`, `heldCredits()` and
  `totalCredits()`, `UsageReport.totalCredits()`, `UsageByDay.credits()`, `UsageByModel.credits()`,
  `ApiModel.credits()`, `ModelCredits` and `ModelCreditRule`.
- `CreateTaskRequest.expectedCost()`, the legacy name of `expectedCostUsd`.
- The 0.1 constructors of the records that gained a USD component.

## [0.1.0] - 2026-10-19

First release of the PopNow Java SDK (`ai.popnow:popnow-java`, package `ai.popnow`, Java 17+).

### Added

- `PopNowClient` with `POPNOW_API_KEY`, HTTPS-only base URL (loopback HTTP allowed), explicit
  `popnow-java/<version>` User-Agent, `Accept-Language` for error messages, and separate request,
  upload and wait budgets. Redirects are never followed.
- Envelope decoding that branches on the business code. A sealed `PopNowException` taxonomy:
  `PopNowApiException` (status, code, `reason()` from `data.reason`, request id, `Retry-After`,
  `remediation()`), protocol, transport, timeout, wait-timeout, upload and webhook failures.
- Retries with jittered exponential back-off honouring `Retry-After`, only for requests that are
  safe to repeat; `50302` is never resent and task submissions are retried only under an
  `Idempotency-Key`.
- Tasks: `quote`, `createTask` (idempotency key, `X-PopNow-Retention`, server-side `wait` through
  `CreateTaskOptions`), `getTask`, `waitForTerminal`, `run`, `retryTask`, `purgeTask`, `listTasks`.
  Amounts in credits (`maxCredits`, `estimatedCredits`, `credits`) next to their USD equivalents.
- Models: `listModels`, `getModel` with credit prices (`ModelCredits`) and input schemas.
- Account: `getBalance` and `getUsage` in credits and USD.
- Files: streamed three-step `uploadFile` / `uploadBytes`, documents and `.safetensors` LoRA
  weights, `importLora` (`POST /files/import`), `createDownloadUrl`.
- Asset library: `assets()` with `create`, `uploadFile`, `list`, `listAll`, `get`, `update`,
  `delete`, `usage` and read-only `characters()`.
- Webhooks: `CallbackSignatureVerifier` for task callbacks and `EndpointWebhookVerifier` for
  Standard Webhooks account endpoints, both checking the signature before the timestamp.
- Tests: local stub server, contract drift check against a pinned OpenAPI copy, and an
  English-only and naming guard over every tracked file.

[0.2.0]: https://github.com/popnow-ai/popnow-java/releases/tag/v0.2.0
[0.1.0]: https://github.com/popnow-ai/popnow-java/releases/tag/v0.1.0
