import { createSign } from "node:crypto";

export interface FcmConfig {
  projectId: string;
  clientEmail: string;
  privateKey: string;
}

export interface FcmResult {
  status: number;
  // FCM's errorCode (UNREGISTERED, INVALID_ARGUMENT, …), else the error status.
  reason?: string;
  message?: string;
}

export type FcmPushType = "alert" | "background";

const TOKEN_URL = "https://oauth2.googleapis.com/token";
const SCOPE = "https://www.googleapis.com/auth/firebase.messaging";
const ASSERTION_TTL_SECONDS = 60 * 60;
const REFRESH_MARGIN_MS = 5 * 60 * 1000;
const REQUEST_TIMEOUT_MS = 10_000;
const TIME_TO_LIVE = "21600s";

export function loadFcmConfig(): FcmConfig | null {
  const projectId = process.env.FCM_PROJECT_ID;
  const clientEmail = process.env.FCM_CLIENT_EMAIL;
  const privateKey = process.env.FCM_PRIVATE_KEY;
  if (!projectId || !clientEmail || !privateKey) return null;
  return {
    projectId,
    clientEmail,
    privateKey: privateKey.replace(/\\n/g, "\n"),
  };
}

function base64url(input: Buffer | string): string {
  return Buffer.from(input).toString("base64url");
}

let cachedToken: { value: string; expiresAt: number; owner: string } | null =
  null;

type AccessToken = { value: string } | { failure: FcmResult };

// An OAuth2 access token for the service account, from a signed JWT assertion.
// Reused until five minutes before it expires.
async function accessToken(config: FcmConfig): Promise<AccessToken> {
  const now = Date.now();
  if (
    cachedToken &&
    cachedToken.owner === config.clientEmail &&
    now < cachedToken.expiresAt - REFRESH_MARGIN_MS
  ) {
    return { value: cachedToken.value };
  }

  const iat = Math.floor(now / 1000);
  const header = base64url(JSON.stringify({ alg: "RS256", typ: "JWT" }));
  const claims = base64url(
    JSON.stringify({
      iss: config.clientEmail,
      scope: SCOPE,
      aud: TOKEN_URL,
      iat,
      exp: iat + ASSERTION_TTL_SECONDS,
    }),
  );
  const signingInput = `${header}.${claims}`;
  const signature = createSign("RSA-SHA256")
    .update(signingInput)
    .sign(config.privateKey);

  const response = await fetch(TOKEN_URL, {
    method: "POST",
    headers: { "content-type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer",
      assertion: `${signingInput}.${base64url(signature)}`,
    }),
    signal: AbortSignal.timeout(REQUEST_TIMEOUT_MS),
  });
  const body = (await response.json().catch(() => ({}))) as {
    access_token?: string;
    expires_in?: number;
    error?: string;
  };
  if (!response.ok || !body.access_token) {
    return {
      failure: {
        status: response.status,
        reason: `oauth: ${body.error ?? "no access token"}`,
      },
    };
  }
  cachedToken = {
    value: body.access_token,
    expiresAt: now + (body.expires_in ?? ASSERTION_TTL_SECONDS) * 1000,
    owner: config.clientEmail,
  };
  return { value: body.access_token };
}

interface FcmErrorBody {
  error?: {
    status?: string;
    message?: string;
    details?: { "@type"?: string; errorCode?: string }[];
  };
}

// A Firebase Installation ID, which FCM hands Android apps since
// firebase-messaging 26: 22 URL-safe base64 characters, so it can't be mistaken
// for a (much longer) legacy registration token, which goes in `token`.
const FID_RE = /^[cdef][A-Za-z0-9_-]{21}$/;

// Sends a data-only message: the app decrypts `blob` itself and posts the
// notification, so FCM never shows one on its own. Alerts go out at high
// priority so a dozing phone wakes for them; background (withdrawal) messages
// at normal priority. Resolves null when FCM or Google's token endpoint can't
// be reached.
export async function sendFcmPush(
  deviceToken: string,
  blob: string,
  config: FcmConfig,
  pushType: FcmPushType = "alert",
  collapseId?: string,
): Promise<FcmResult | null> {
  try {
    const bearer = await accessToken(config);
    if ("failure" in bearer) return bearer.failure;

    const data: Record<string, string> = { blob, type: pushType };
    if (collapseId) data.collapseId = collapseId;
    const android: Record<string, string> = {
      priority: pushType === "background" ? "NORMAL" : "HIGH",
      ttl: TIME_TO_LIVE,
    };
    if (pushType === "alert" && collapseId) android.collapse_key = collapseId;

    const response = await fetch(
      `https://fcm.googleapis.com/v1/projects/${encodeURIComponent(config.projectId)}/messages:send`,
      {
        method: "POST",
        headers: {
          authorization: `Bearer ${bearer.value}`,
          "content-type": "application/json",
        },
        body: JSON.stringify({
          message: {
            ...(FID_RE.test(deviceToken) ? { fid: deviceToken } : { token: deviceToken }),
            data,
            android,
          },
        }),
        signal: AbortSignal.timeout(REQUEST_TIMEOUT_MS),
      },
    );
    if (response.ok) return { status: 200 };

    // A token Google revoked early must not be reused for the next push.
    if (response.status === 401) cachedToken = null;
    const body = (await response.json().catch(() => ({}))) as FcmErrorBody;
    const errorCode = body.error?.details?.find((d) => d.errorCode)?.errorCode;
    return {
      status: response.status,
      reason: errorCode ?? body.error?.status,
      message: body.error?.message,
    };
  } catch {
    return null;
  }
}
