"""One-time Hisaab release key creation. Never place outputs inside a Git repository."""

import argparse
import base64
import hashlib
import os
from pathlib import Path
import secrets
import subprocess
from datetime import datetime, timedelta, timezone

from cryptography import x509
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import rsa
from cryptography.hazmat.primitives.serialization import pkcs12
from cryptography.x509.oid import NameOID


def exclusive_write(path: Path, content: bytes) -> None:
    with path.open("xb") as output:
        output.write(content)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--backup-dir", type=Path, required=True)
    parser.add_argument("--password-file", type=Path, required=True)
    parser.add_argument("--repo", required=True)
    args = parser.parse_args()

    backup = args.backup_dir.resolve()
    password_file = args.password_file.resolve()
    key_file = backup / "hisaab-release.p12"
    if password_file.parent == backup:
        raise SystemExit("Keep the password file outside the key backup folder.")
    if key_file.exists() or password_file.exists():
        raise SystemExit("A release key or password file already exists; refusing to replace it.")

    backup.mkdir(parents=True, exist_ok=True)
    password_file.parent.mkdir(parents=True, exist_ok=True)
    password = secrets.token_urlsafe(36)
    alias = "hisaab-release"
    key = rsa.generate_private_key(public_exponent=65537, key_size=4096)
    subject = x509.Name([x509.NameAttribute(NameOID.COMMON_NAME, "Hisaab Android Release")])
    now = datetime.now(timezone.utc)
    cert = (
        x509.CertificateBuilder()
        .subject_name(subject)
        .issuer_name(subject)
        .public_key(key.public_key())
        .serial_number(x509.random_serial_number())
        .not_valid_before(now - timedelta(days=1))
        .not_valid_after(now + timedelta(days=365 * 30))
        .sign(key, hashes.SHA256())
    )
    payload = pkcs12.serialize_key_and_certificates(
        alias.encode("ascii"), key, cert, None,
        serialization.BestAvailableEncryption(password.encode("ascii")),
    )
    exclusive_write(key_file, payload)
    exclusive_write(password_file, (password + "\n").encode("ascii"))

    values = {
        "HISAAB_KEYSTORE_BASE64": base64.b64encode(payload).decode("ascii"),
        "HISAAB_SIGNING_STORE_PASSWORD": password,
        "HISAAB_SIGNING_KEY_ALIAS": alias,
        "HISAAB_SIGNING_KEY_PASSWORD": password,
    }
    for name, value in values.items():
        subprocess.run(
            ["gh", "secret", "set", name, "--repo", args.repo],
            input=value, text=True, check=True, capture_output=True,
        )
        print(f"Uploaded GitHub secret: {name}")

    print(f"Private key backup: {key_file}")
    print(f"Separate recovery password file: {password_file}")
    print(f"Public certificate SHA-256: {hashlib.sha256(cert.public_bytes(serialization.Encoding.DER)).hexdigest()}")


if __name__ == "__main__":
    main()
