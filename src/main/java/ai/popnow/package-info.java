/**
 * Java client for the PopNow API: AI video, image, music and speech generation.
 *
 * <p>Start at {@link ai.popnow.PopNowClient}. The asynchronous media task is the main subject:
 * catalogue lookup, price quote in credits, submission (optionally waiting server-side), polling,
 * retry and content destruction, plus media upload, LoRA import and download URLs. Files used
 * again and again live in the asset library, {@link ai.popnow.PopNowClient#assets()}, which also
 * reads the library's characters.
 *
 * <p>Webhooks: {@link ai.popnow.CallbackSignatureVerifier} for {@code callBackUrl} deliveries and
 * {@link ai.popnow.EndpointWebhookVerifier} for account endpoints in the Standard Webhooks format.
 */
package ai.popnow;
