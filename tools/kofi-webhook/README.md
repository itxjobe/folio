# Ko-fi webhook

Optional. It hands a supporter code to whoever just paid, without you being awake.

**You don't need it to start.** A Ko-fi shop item with a digital file delivers one code to every buyer automatically,
which is the simplest thing that works and is what `docs/releases/kofi.md` recommends first. Set this up only when you
want a *different* code per person.

## What it does

Ko-fi posts to the worker on every payment. The worker checks the payment is really from Ko-fi, takes one code off a
list you minted earlier, and emails it to the address Ko-fi gives it.

**The signing key never goes online.** You mint a batch on your Mac and upload the codes; the worker only hands them
out. If it were ever broken into, what leaks is a handful of codes — which are shareable by design anyway.

## Setting it up

1. **Mint a batch** (they don't expire unless you say so):

   ```bash
   for i in $(seq 1 50); do python3 tools/folio-code.py mint market 2>/dev/null; done > codes.txt
   ```

2. **Make the worker**, once:

   ```bash
   npm install -g wrangler
   wrangler login
   wrangler kv namespace create CODES
   ```

   Put the namespace id in `wrangler.toml`, then:

   ```bash
   wrangler deploy
   ```

3. **Upload the codes**:

   ```bash
   n=0; while read -r code; do n=$((n+1)); wrangler kv key put --binding CODES "code:$n" "$code" --remote; done < codes.txt
   ```

4. **Secrets**:

   ```bash
   wrangler secret put KOFI_TOKEN     # from Ko-fi → Settings → API/Webhooks → Verification token
   wrangler secret put RESEND_KEY     # optional; without it the code is logged, not sent
   ```

   `FROM_EMAIL` goes in `wrangler.toml` and has to be on a domain the email provider has verified.

5. **Point Ko-fi at it.** Ko-fi → Settings → **API / Webhooks** → Webhook URL: your worker's address. The page has a
   **Send test** button and shows the sample payload — worth reading it once, because Ko-fi posts a form with a single
   `data` field holding the JSON rather than posting JSON directly.

6. **Test it**, in this order: the Send test button (expect `thanks` and a line in `wrangler tail`), then a real 1-unit
   purchase to yourself, then paste the code that arrives into Settings › Market › Early access.

## Keeping it fed

`wrangler kv key list --binding CODES --remote --prefix code:` says how many are left. When it runs low, mint more and
upload them; the worker answers "out of codes" and logs loudly rather than sending something that won't work.

## If you'd rather not run a server

- **Shop item with a digital file** — one code for everyone, no infrastructure.
- **Supporters-only post** — the code in a post only supporters can read.

Both are in `docs/releases/kofi.md`.
