/**
 * Hands a supporter code to whoever just bought one on Ko-fi.
 *
 * Ko-fi posts to this worker on every payment. It checks the payment is really from Ko-fi, takes one code off a list
 * that was minted offline, and emails it. **The signing key never comes near this worker**: codes are minted on
 * McCal's machine with `tools/folio-code.py` and uploaded as a batch, so a worker that's broken into leaks at most the
 * codes still in it - and codes are shareable by design anyway.
 *
 * Cloudflare Workers, free tier, no framework. Deploy with wrangler (see README.md next to this file).
 *
 * Bindings it expects:
 *   CODES        KV namespace - one entry per unused code, key `code:<n>`, plus `used:<transaction id>`
 *   KOFI_TOKEN   secret - the verification token from Ko-fi's webhook page
 *   RESEND_KEY   secret - an email API key (optional; without it the code is only logged for McCal)
 *   FROM_EMAIL   var    - the address the email comes from, on a domain that's been verified
 */

const SUBJECT = "Your Folio early access code";

export default {
  async fetch(request, env) {
    if (request.method !== "POST") return new Response("Folio code service", { status: 200 });

    // Ko-fi posts a form with one field, `data`, holding the JSON.
    let payload;
    try {
      const form = await request.formData();
      payload = JSON.parse(form.get("data"));
    } catch {
      return new Response("bad payload", { status: 400 });
    }

    // Anyone can POST here, so the token is what makes it Ko-fi. Compared in constant time out of habit.
    if (!env.KOFI_TOKEN || !safeEqual(payload.verification_token ?? "", env.KOFI_TOKEN)) {
      return new Response("no", { status: 401 });
    }

    // Ko-fi retries, and a retry must not hand out a second code.
    const seen = `used:${payload.kofi_transaction_id ?? payload.message_id}`;
    if (await env.CODES.get(seen)) return new Response("already handled", { status: 200 });

    const email = (payload.email ?? "").trim();
    const name = (payload.from_name ?? "there").trim();
    const code = await takeCode(env);
    if (!code) {
      // Better to say nothing than to send a code that doesn't exist. McCal tops the list up.
      console.error("OUT OF CODES", { email, type: payload.type });
      return new Response("out of codes", { status: 200 });
    }

    await env.CODES.put(seen, code, { expirationTtl: 60 * 60 * 24 * 30 });
    console.log("issued", { to: email, type: payload.type, tier: payload.tier_name ?? null });

    if (env.RESEND_KEY && email) {
      await send(env, email, name, code);
    }
    return new Response("thanks", { status: 200 });
  },
};

/** Takes the first unused code and removes it, so two payments at once can't get the same one. */
async function takeCode(env) {
  const list = await env.CODES.list({ prefix: "code:", limit: 10 });
  for (const key of list.keys) {
    const code = await env.CODES.get(key.name);
    if (!code) continue;
    await env.CODES.delete(key.name);
    return code;
  }
  return null;
}

async function send(env, to, name, code) {
  const text = [
    `Hi ${name},`,
    "",
    "Thank you for supporting Folio. Here's your early access code:",
    "",
    code,
    "",
    "In Folio: Settings › Market › Early access › paste it › Use code.",
    "",
    "It turns on the Folio Market before it ships. Everything the Market hands out is already in Settings, so this",
    "is a head start rather than a lock - and passing the code to a friend is fine.",
    "",
    "- McCal",
  ].join("\n");

  const response = await fetch("https://api.resend.com/emails", {
    method: "POST",
    headers: { Authorization: `Bearer ${env.RESEND_KEY}`, "Content-Type": "application/json" },
    body: JSON.stringify({ from: env.FROM_EMAIL, to, subject: SUBJECT, text }),
  });
  if (!response.ok) console.error("email failed", response.status, await response.text());
}

function safeEqual(a, b) {
  if (a.length !== b.length) return false;
  let diff = 0;
  for (let i = 0; i < a.length; i++) diff |= a.charCodeAt(i) ^ b.charCodeAt(i);
  return diff === 0;
}
