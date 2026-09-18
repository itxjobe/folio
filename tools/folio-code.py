#!/usr/bin/env python3
"""Makes the supporter codes Folio checks by itself.

A code is `folio-early:<feature>:<expires>.<signature>` - a short line signed with McCal's private key and checked on
the phone against the public key built into the app. There is no account, no server call and nothing stored about who
paid: Folio keeps the code and the date it runs out, and that's all.

    python3 tools/folio-code.py keygen                       # once: makes the key and prints what goes in the app
    python3 tools/folio-code.py keygen --passphrase          # same, with the key encrypted on disk
    python3 tools/folio-code.py protect                      # encrypt the key you already have
    python3 tools/folio-code.py mint market                  # a code for the Market that never runs out
    python3 tools/folio-code.py mint market --days 90        # one that runs out in 90 days
    python3 tools/folio-code.py mint '*' --days 365          # everything, for a year
    python3 tools/folio-code.py verify <code> market         # check one the way the phone does

The private key never leaves the machine it's made on and is never committed. Codes are meant to be shareable - a
supporter passing one to a friend is fine, it's a thank-you rather than a licence - so mint by feature, not by person.

What signing does and doesn't do: nobody can make a code without the private key, so the only way to get one is to be
given one. It doesn't stop a code being passed around, and it can't stop someone building Folio from source with the
check removed - the app is MIT, and a check that runs on the phone is always the phone's to skip. It's an honour
system with a lock on the front door, which is the right shape for a thank-you.

With --passphrase the key is encrypted on disk and every mint needs it. Set FOLIO_KEY_PASSPHRASE to avoid typing it,
or leave it unset and be asked.
"""

import argparse
import base64
import getpass
import hashlib
import os
import pathlib
import subprocess
import sys
import time

KEY = pathlib.Path("folio-supporter.pem")
PREFIX = "folio-early"
DAY = 24 * 60 * 60


def encrypted(key: pathlib.Path) -> bool:
    head = key.read_text(errors="ignore")[:200]
    return "ENCRYPTED" in head or "BEGIN PRIVATE KEY" not in head and "Proc-Type" in head


def passin(key: pathlib.Path) -> list[str]:
    """How openssl should read the passphrase, or nothing when the key isn't encrypted."""
    if not encrypted(key):
        return []
    phrase = os.environ.get("FOLIO_KEY_PASSPHRASE") or getpass.getpass("Passphrase for the supporter key: ")
    os.environ["FOLIO_KEY_PASSPHRASE"] = phrase
    return ["-passin", "env:FOLIO_KEY_PASSPHRASE"]


def public_spki(key: pathlib.Path) -> bytes:
    return subprocess.run(
        ["openssl", "ec", "-in", str(key), "-pubout", "-outform", "DER", *passin(key)],
        check=True, capture_output=True,
    ).stdout


def keygen(protect: bool) -> None:
    if KEY.exists():
        sys.exit(f"{KEY} already exists. Move it aside if you really mean to make a new key - a new key makes every "
                 "code already handed out stop working.")
    subprocess.run(["openssl", "ecparam", "-name", "prime256v1", "-genkey", "-noout", "-out", str(KEY)], check=True)
    if protect:
        phrase = os.environ.get("FOLIO_KEY_PASSPHRASE") or getpass.getpass("Passphrase for the new key: ")
        if not phrase:
            sys.exit("An empty passphrase would leave the key as it was; not doing that.")
        os.environ["FOLIO_KEY_PASSPHRASE"] = phrase
        subprocess.run(
            ["openssl", "pkcs8", "-topk8", "-v2", "aes-256-cbc", "-in", str(KEY), "-out", str(KEY) + ".enc",
             "-passout", "env:FOLIO_KEY_PASSPHRASE"], check=True,
        )
        pathlib.Path(str(KEY) + ".enc").replace(KEY)
        print("The key is encrypted on disk; minting will ask for the passphrase.\n")
    KEY.chmod(0o600)
    spki = public_spki(KEY)
    print(f"Wrote {KEY}. Keep it offline, and don't commit it.\n")
    print("Put this in EarlyAccess.PUBLIC_KEY (market/src/main/java/com/mccal/folio/market/EarlyAccess.kt):\n")
    print(f'        val PUBLIC_KEY: String? = "{base64.b64encode(spki).decode()}"\n')
    print("Fingerprint:", hashlib.sha256(spki).hexdigest().upper()[:32])


def protect() -> None:
    """Encrypts the key that already exists, in place.

    The new file is written beside the old one and checked before anything is replaced, so a wrong passphrase or a
    full disk leaves the key as it was. The passphrase is asked for here and never stored: losing it is the same as
    losing the key, so it belongs in a password manager next to the backup.
    """
    if not KEY.exists():
        sys.exit(f"No {KEY} here. Run this from the folder holding the key (~/.folio).")
    if encrypted(KEY):
        sys.exit(f"{KEY} is already encrypted.")

    phrase = getpass.getpass("New passphrase: ")
    if len(phrase) < 8:
        sys.exit("That's short enough to guess. Nothing was changed.")
    if phrase != getpass.getpass("Again: "):
        sys.exit("Those don't match. Nothing was changed.")

    os.environ["FOLIO_KEY_PASSPHRASE"] = phrase
    encrypted_file = pathlib.Path(str(KEY) + ".enc")
    result = subprocess.run(
        ["openssl", "pkcs8", "-topk8", "-v2", "aes-256-cbc", "-in", str(KEY), "-out", str(encrypted_file),
         "-passout", "env:FOLIO_KEY_PASSPHRASE"], capture_output=True,
    )
    if result.returncode != 0 or not encrypted_file.exists():
        encrypted_file.unlink(missing_ok=True)
        sys.exit("openssl couldn't encrypt the key. Nothing was changed.\n" + result.stderr.decode())

    # It has to still be the same key, and still usable, before the old file goes.
    before = base64.b64encode(public_spki(KEY)).decode()
    check = subprocess.run(
        ["openssl", "ec", "-in", str(encrypted_file), "-pubout", "-outform", "DER",
         "-passin", "env:FOLIO_KEY_PASSPHRASE"], capture_output=True,
    )
    if check.returncode != 0 or base64.b64encode(check.stdout).decode() != before:
        encrypted_file.unlink(missing_ok=True)
        sys.exit("The encrypted copy didn't read back as the same key. Nothing was changed.")

    encrypted_file.replace(KEY)
    KEY.chmod(0o600)
    print(f"{KEY} is encrypted now. Minting will ask for the passphrase.")
    print("\nTwo things to do, in this order:")
    print("  1. Put the passphrase in your password manager. Without it the key is gone, and so is every code.")
    print("  2. Replace your offline backup with this file - the old backup is still unencrypted.")


def mint(feature: str, days: int) -> None:
    if not KEY.exists():
        sys.exit(f"No {KEY}. Run: python3 tools/folio-code.py keygen")
    expires = 0 if days <= 0 else int(time.time()) + days * DAY
    payload = f"{PREFIX}:{feature}:{expires}"
    signing = subprocess.run(
        ["openssl", "dgst", "-sha256", "-sign", str(KEY), *passin(KEY)],
        input=payload.encode(), capture_output=True,
    )
    if signing.returncode != 0:
        sys.exit("Couldn't sign with that key. If it's encrypted, the passphrase was wrong.")
    signature = signing.stdout
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
    pub.write_bytes(subprocess.run(["openssl", "ec", "-in", str(KEY), "-pubout", *passin(KEY)],
                                   check=True, capture_output=True).stdout)
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
    maker = commands.add_parser("keygen", help="make the supporter key (once)")
    maker.add_argument("--passphrase", action="store_true", help="encrypt the key on disk; every mint then needs it")
    commands.add_parser("protect", help="encrypt the key that already exists")
    minter = commands.add_parser("mint", help="make a code")
    minter.add_argument("feature", help="market, or * for everything")
    minter.add_argument("--days", type=int, default=0, help="days until it runs out; 0 means never")
    checker = commands.add_parser("verify", help="check a code the way the phone does")
    checker.add_argument("code")
    checker.add_argument("feature", nargs="?", default="market")

    args = parser.parse_args()
    if args.command == "keygen":
        keygen(args.passphrase)
    elif args.command == "protect":
        protect()
    elif args.command == "mint":
        mint(args.feature, args.days)
    else:
        verify(args.code, args.feature)


if __name__ == "__main__":
    main()
