#!/usr/bin/env python3
"""Makes the supporter codes Folio checks by itself.

A code is `folio-early:<feature>:<expires>.<signature>` - a short line signed with McCal's private key and checked on
the phone against the public key built into the app. There is no account, no server call and nothing stored about who
paid: Folio keeps the code and the date it runs out, and that's all.

    python3 tools/folio-code.py keygen                       # once: makes the key and prints what goes in the app
    python3 tools/folio-code.py mint market                  # a code for the Market that never runs out
    python3 tools/folio-code.py mint market --days 90        # one that runs out in 90 days
    python3 tools/folio-code.py mint '*' --days 365          # everything, for a year
    python3 tools/folio-code.py verify <code> market         # check one the way the phone does

The private key never leaves the machine it's made on and is never committed. Codes are meant to be shareable - a
supporter passing one to a friend is fine, it's a thank-you rather than a licence - so mint by feature, not by person.
"""

import argparse
import base64
import hashlib
import pathlib
import subprocess
import sys
import time

KEY = pathlib.Path("folio-supporter.pem")
PREFIX = "folio-early"
DAY = 24 * 60 * 60


def public_spki(key: pathlib.Path) -> bytes:
    return subprocess.run(
        ["openssl", "ec", "-in", str(key), "-pubout", "-outform", "DER"],
        check=True, capture_output=True,
    ).stdout


def keygen() -> None:
    if KEY.exists():
        sys.exit(f"{KEY} already exists. Move it aside if you really mean to make a new key - a new key makes every "
                 "code already handed out stop working.")
    subprocess.run(["openssl", "ecparam", "-name", "prime256v1", "-genkey", "-noout", "-out", str(KEY)], check=True)
    KEY.chmod(0o600)
    spki = public_spki(KEY)
    print(f"Wrote {KEY}. Keep it offline, and don't commit it.\n")
    print("Put this in EarlyAccess.PUBLIC_KEY (market/src/main/java/com/mccal/folio/market/EarlyAccess.kt):\n")
    print(f'        val PUBLIC_KEY: String? = "{base64.b64encode(spki).decode()}"\n')
    print("Fingerprint:", hashlib.sha256(spki).hexdigest().upper()[:32])


def mint(feature: str, days: int) -> None:
    if not KEY.exists():
        sys.exit(f"No {KEY}. Run: python3 tools/folio-code.py keygen")
    expires = 0 if days <= 0 else int(time.time()) + days * DAY
    payload = f"{PREFIX}:{feature}:{expires}"
    signature = subprocess.run(
        ["openssl", "dgst", "-sha256", "-sign", str(KEY)],
        input=payload.encode(), check=True, capture_output=True,
    ).stdout
    code = f"{payload}.{base64.b64encode(signature).decode()}"
    print(code)
    print(f"\n  feature : {feature}", file=sys.stderr)
    print(f"  runs out: {'never' if expires == 0 else time.strftime('%Y-%m-%d', time.localtime(expires))}",
          file=sys.stderr)
    print(f"  length  : {len(code)} characters (it's meant to be pasted, not typed)", file=sys.stderr)


def verify(code: str, feature: str) -> None:
    if not KEY.exists():
        sys.exit(f"No {KEY} to check against.")
    payload, _, signature = code.strip().rpartition(".")
    parts = payload.split(":")
    if len(parts) != 3 or parts[0] != PREFIX:
        sys.exit("That isn't the shape of a Folio code.")
    pub = pathlib.Path("folio-supporter.pub.pem")
    pub.write_bytes(subprocess.run(["openssl", "ec", "-in", str(KEY), "-pubout"], check=True, capture_output=True).stdout)
    sig = pathlib.Path("folio-code.sig")
    sig.write_bytes(base64.b64decode(signature))
    result = subprocess.run(
        ["openssl", "dgst", "-sha256", "-verify", str(pub), "-signature", str(sig)],
        input=payload.encode(), capture_output=True,
    )
    pub.unlink()
    sig.unlink()
    ok = result.returncode == 0
    expires = int(parts[2])
    print("signature:", "good" if ok else "BAD")
    print("feature  :", parts[1], "(asked for:", feature + ")",
          "- match" if parts[1] in (feature, "*") else "- WRONG FEATURE")
    print("runs out :", "never" if expires == 0 else time.strftime("%Y-%m-%d", time.localtime(expires)),
          "- EXPIRED" if 0 < expires < time.time() else "")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    commands = parser.add_subparsers(dest="command", required=True)
    commands.add_parser("keygen", help="make the supporter key (once)")
    minter = commands.add_parser("mint", help="make a code")
    minter.add_argument("feature", help="market, or * for everything")
    minter.add_argument("--days", type=int, default=0, help="days until it runs out; 0 means never")
    checker = commands.add_parser("verify", help="check a code the way the phone does")
    checker.add_argument("code")
    checker.add_argument("feature", nargs="?", default="market")

    args = parser.parse_args()
    if args.command == "keygen":
        keygen()
    elif args.command == "mint":
        mint(args.feature, args.days)
    else:
        verify(args.code, args.feature)


if __name__ == "__main__":
    main()
