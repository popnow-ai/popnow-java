# Security policy

## Reporting a vulnerability

Please do **not** open a public issue for security problems.

- Open a [private security advisory](https://github.com/popnow-ai/popnow-java/security/advisories/new) on GitHub, or
- email **support@popnow.ai** with "Security" in the subject.

Include the affected version, a description, and steps to reproduce. Never include a live API key;
if you think a key was exposed, revoke it in the [API console](https://popnow.ai/api/console/keys) first.

## Supported versions

Security fixes are released for the latest minor version of `ai.popnow:popnow-java`.

## Handling secrets

- Keep `POPNOW_API_KEY`, `POPNOW_WEBHOOK_SECRET` and endpoint `whsec_` secrets on your server, in
  environment variables or a secret store. Never ship the API key inside an Android app or a
  desktop binary.
- This client never logs the `Authorization` header, request bodies or signed URLs, never follows
  redirects (which would forward the key), and never sends the key to object storage.
- Output URLs (`TaskAsset.url()`) are short-lived bearer links; do not publish or store them.
