import {
  loadApnsConfig,
  sendPush,
  type ApnsEnv,
  type ApnsPushType,
} from "@/lib/apns";
import { loadFcmConfig, sendFcmPush, type FcmResult } from "@/lib/fcm";

export const runtime = "nodejs";

const APNS_TOKEN_RE = /^[0-9a-fA-F]{1,200}$/;
const FCM_TOKEN_RE = /^[A-Za-z0-9_:-]{1,4096}$/;
const BASE64_RE = /^[A-Za-z0-9+/]+={0,2}$/;
const MAX_BLOB_CHARS = 8 * 1024;

const RATE_LIMIT_MAX = 30;
const RATE_LIMIT_WINDOW_MS = 60_000;

const rateLog = new Map<string, number[]>();

function rateLimited(token: string): boolean {
  const now = Date.now();
  const cutoff = now - RATE_LIMIT_WINDOW_MS;

  for (const [key, hits] of rateLog) {
    const kept = hits.filter((t) => t > cutoff);
    if (kept.length === 0) rateLog.delete(key);
    else rateLog.set(key, kept);
  }

  const hits = rateLog.get(token) ?? [];
  if (hits.length >= RATE_LIMIT_MAX) return true;
  hits.push(now);
  rateLog.set(token, hits);
  return false;
}

function isValidCollapseId(id: unknown): id is string {
  return typeof id === "string" && /^[\x21-\x7e]{1,64}$/.test(id);
}

function isValidBlob(blob: unknown): blob is string {
  return (
    typeof blob === "string" &&
    blob.length > 0 &&
    blob.length <= MAX_BLOB_CHARS &&
    BASE64_RE.test(blob)
  );
}

function badRequest(): Response {
  return Response.json({ ok: false, reason: "bad request" }, { status: 400 });
}

function notConfigured(): Response {
  return Response.json(
    { ok: false, reason: "relay not configured" },
    { status: 503 },
  );
}

function tooMany(): Response {
  return Response.json({ ok: false, reason: "rate limited" }, { status: 429 });
}

// The desktop clears a device's token on a 410, so a dead install stops
// generating traffic.
function unregistered(): Response {
  return Response.json(
    { ok: false, status: 410, reason: "Unregistered" },
    { status: 410 },
  );
}

export async function POST(request: Request): Promise<Response> {
  let body: unknown;
  try {
    body = await request.json();
  } catch {
    return badRequest();
  }

  if (!body || typeof body !== "object") return badRequest();
  const { platform, token, env, blob, type, collapseId } = body as Record<
    string,
    unknown
  >;

  if (platform !== undefined && platform !== "apns" && platform !== "fcm") {
    return badRequest();
  }
  const fcm = platform === "fcm";
  const tokenRe = fcm ? FCM_TOKEN_RE : APNS_TOKEN_RE;
  if (typeof token !== "string" || !tokenRe.test(token)) return badRequest();
  if (!fcm && env !== "production" && env !== "sandbox") return badRequest();
  if (!isValidBlob(blob)) return badRequest();
  if (type !== undefined && type !== "alert" && type !== "background") {
    return badRequest();
  }
  const pushType: ApnsPushType = type === "background" ? "background" : "alert";
  if (collapseId !== undefined && !isValidCollapseId(collapseId)) {
    return badRequest();
  }
  const collapse = typeof collapseId === "string" ? collapseId : undefined;

  return fcm
    ? relayToFcm(token, blob, pushType, collapse)
    : relayToApns(env as ApnsEnv, token, blob, pushType, collapse);
}

async function relayToApns(
  env: ApnsEnv,
  token: string,
  blob: string,
  pushType: ApnsPushType,
  collapseId: string | undefined,
): Promise<Response> {
  const config = loadApnsConfig();
  if (!config) return notConfigured();
  if (rateLimited(token)) return tooMany();

  const payload = JSON.stringify(
    pushType === "background"
      ? { aps: { "content-available": 1 }, blob }
      : {
          aps: {
            alert: { title: "lpm", body: "Activity in lpm" },
            sound: "default",
            "mutable-content": 1,
          },
          blob,
        },
  );

  const result = await sendPush(
    env,
    token,
    payload,
    config,
    pushType,
    collapseId,
  );
  if (!result) {
    return Response.json(
      { ok: false, reason: "apns unreachable" },
      { status: 502 },
    );
  }

  if (result.status === 200) {
    return Response.json({ ok: true });
  }

  if (
    result.status === 410 ||
    (result.status === 400 && result.reason === "BadDeviceToken")
  ) {
    return unregistered();
  }

  return Response.json(
    { ok: false, status: result.status, reason: result.reason },
    { status: 502 },
  );
}

// FCM reports an uninstalled app or a rotated-out token as UNREGISTERED (404),
// and a token that was never valid as INVALID_ARGUMENT naming the token.
function fcmTokenDead(result: FcmResult): boolean {
  if (result.status === 404 || result.reason === "UNREGISTERED") return true;
  return (
    result.status === 400 &&
    result.reason === "INVALID_ARGUMENT" &&
    /registration token/i.test(result.message ?? "")
  );
}

async function relayToFcm(
  token: string,
  blob: string,
  pushType: ApnsPushType,
  collapseId: string | undefined,
): Promise<Response> {
  const config = loadFcmConfig();
  if (!config) return notConfigured();
  if (rateLimited(token)) return tooMany();

  const result = await sendFcmPush(token, blob, config, pushType, collapseId);
  if (!result) {
    return Response.json(
      { ok: false, reason: "fcm unreachable" },
      { status: 502 },
    );
  }

  if (result.status === 200) {
    return Response.json({ ok: true });
  }

  if (fcmTokenDead(result)) return unregistered();

  return Response.json(
    { ok: false, status: result.status, reason: result.reason },
    { status: 502 },
  );
}
