import { onInAppFeedbackPublished } from "firebase-functions/alerts/appDistribution";
import { defineSecret } from "firebase-functions/params";
import { logger } from "firebase-functions";
import { initializeApp } from "firebase-admin/app";
import { getStorage } from "firebase-admin/storage";

initializeApp();

const githubToken = defineSecret("GITHUB_TOKEN");

const GITHUB_REPO = "MehKoiter/shot-timer-android";
const SCREENSHOT_SIGNED_URL_EXPIRY_MS = 30 * 24 * 60 * 60 * 1000; // 30 days
const TITLE_SNIPPET_LENGTH = 60;

/**
 * Resolves a usable HTTPS URL for the tester's screenshot, for embedding in a GitHub issue.
 *
 * In practice `screenshotUri` (despite what Firebase's docs suggest) is already a temporary
 * HTTPS download URL hosted by Firebase App Distribution itself (same pattern as the release
 * binary download links), not a `gs://bucket/path` Cloud Storage URI - confirmed by real
 * function logs. That URL is used directly with no signing needed. A `gs://` URI is handled
 * as a defensive fallback in case the payload shape ever changes, but note it requires the
 * runtime service account to hold `roles/iam.serviceAccountTokenCreator` (signBlob permission)
 * to generate a signed URL, which is not granted by default.
 */
async function getScreenshotSignedUrl(screenshotUri) {
  if (!screenshotUri || typeof screenshotUri !== "string") {
    return null;
  }

  if (/^https?:\/\//.test(screenshotUri)) {
    return screenshotUri;
  }

  try {
    let bucketName;
    let filePath;

    const gsMatch = screenshotUri.match(/^gs:\/\/([^/]+)\/(.+)$/);
    if (gsMatch) {
      bucketName = gsMatch[1];
      filePath = gsMatch[2];
    } else {
      // Fall back to treating the value as a bare object path in the
      // project's default Storage bucket.
      filePath = screenshotUri.replace(/^\/+/, "");
    }

    if (!filePath) {
      logger.warn("Screenshot URI had no parseable file path, skipping image", {
        screenshotUri,
      });
      return null;
    }

    const bucket = bucketName ? getStorage().bucket(bucketName) : getStorage().bucket();
    const file = bucket.file(filePath);

    const [signedUrl] = await file.getSignedUrl({
      action: "read",
      expires: Date.now() + SCREENSHOT_SIGNED_URL_EXPIRY_MS,
    });

    return signedUrl;
  } catch (err) {
    logger.warn("Failed to generate signed URL for tester screenshot, skipping image", {
      screenshotUri,
      error: err instanceof Error ? err.message : String(err),
    });
    return null;
  }
}

/** Builds a short, non-empty GitHub issue title from the feedback text. */
function buildIssueTitle(text) {
  const trimmed = (text || "").trim();
  if (!trimmed) {
    return "Tester feedback: (no message provided)";
  }
  const snippet =
    trimmed.length > TITLE_SNIPPET_LENGTH
      ? `${trimmed.slice(0, TITLE_SNIPPET_LENGTH).trim()}...`
      : trimmed;
  return `Tester feedback: ${snippet}`;
}

/**
 * Builds the markdown body for the GitHub issue. Deliberately excludes tester name/email:
 * this repo is public, and publishing a tester's real identity in a public issue would expose
 * PII they never consented to having posted publicly. Anyone with Firebase project access can
 * still see who submitted it via the console link below.
 */
function buildIssueBody({ text, appVersion, feedbackConsoleUri, screenshotUrl }) {
  const lines = [];

  lines.push("## Tester feedback");
  lines.push("");
  lines.push(text && text.trim() ? text.trim() : "_(no message provided)_");
  lines.push("");
  lines.push("---");
  lines.push("");
  lines.push(`**App version:** ${appVersion || "(unknown)"}`);

  if (screenshotUrl) {
    lines.push("");
    lines.push("**Screenshot:**");
    lines.push("");
    lines.push(`![screenshot](${screenshotUrl})`);
  }

  if (feedbackConsoleUri) {
    lines.push("");
    lines.push(
      `[View original feedback in the Firebase console](${feedbackConsoleUri}) (tester identity is visible there, to project owners only)`
    );
  }

  return lines.join("\n");
}

export const mirrorTesterFeedbackToGithub = onInAppFeedbackPublished(
  { secrets: [githubToken] },
  async (event) => {
    try {
      const payload = event.data.payload;
      const { text, appVersion, screenshotUri, feedbackConsoleUri } = payload;

      const screenshotUrl = await getScreenshotSignedUrl(screenshotUri);

      const title = buildIssueTitle(text);
      const body = buildIssueBody({
        text,
        appVersion,
        feedbackConsoleUri,
        screenshotUrl,
      });

      const response = await fetch(`https://api.github.com/repos/${GITHUB_REPO}/issues`, {
        method: "POST",
        headers: {
          Authorization: `Bearer ${githubToken.value()}`,
          Accept: "application/vnd.github+json",
          "Content-Type": "application/json",
          "X-GitHub-Api-Version": "2022-11-28",
        },
        body: JSON.stringify({
          title,
          body,
          labels: ["tester-feedback"],
        }),
      });

      logger.info(`GitHub issue creation responded with status ${response.status}`, {
        appId: event.appId,
      });

      if (!response.ok) {
        const errorBody = await response.text();
        logger.error("GitHub API returned a non-2xx response while creating the tester feedback issue", {
          status: response.status,
          errorBody,
        });
      }
    } catch (err) {
      // This is a background alert trigger; letting an error escape just
      // burns retries against Firebase, so log and swallow instead.
      logger.error("Unhandled error while mirroring tester feedback to GitHub", {
        error: err instanceof Error ? err.message : String(err),
        stack: err instanceof Error ? err.stack : undefined,
      });
    }
  }
);
