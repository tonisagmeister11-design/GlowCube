"""Ephemeral self-signed certificates for the pairing server (no custom crypto).

A fresh EC P-256 key/certificate is generated for every pairing session and kept
only in memory. The creator authenticates the receiver by pinning the SHA-256
fingerprint that the receiver shows together with the pairing code.
"""
from __future__ import annotations

import datetime
import hashlib
import ssl
import tempfile
from pathlib import Path

from cryptography import x509
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import ec
from cryptography.x509.oid import NameOID


def generate_self_signed(common_name: str = "MakePrank Receiver") -> tuple[bytes, bytes, str]:
    """Return ``(cert_pem, key_pem, sha256_fingerprint_hex_of_der)``."""
    key = ec.generate_private_key(ec.SECP256R1())
    name = x509.Name([x509.NameAttribute(NameOID.COMMON_NAME, common_name)])
    now = datetime.datetime.now(datetime.timezone.utc)
    cert = (
        x509.CertificateBuilder()
        .subject_name(name).issuer_name(name).public_key(key.public_key())
        .serial_number(x509.random_serial_number())
        .not_valid_before(now - datetime.timedelta(minutes=5))
        .not_valid_after(now + datetime.timedelta(hours=24))
        .sign(key, hashes.SHA256())
    )
    der = cert.public_bytes(serialization.Encoding.DER)
    return (
        cert.public_bytes(serialization.Encoding.PEM),
        key.private_bytes(serialization.Encoding.PEM, serialization.PrivateFormat.PKCS8, serialization.NoEncryption()),
        hashlib.sha256(der).hexdigest(),
    )


def server_context(cert_pem: bytes, key_pem: bytes) -> ssl.SSLContext:
    """Build a TLS server context; the key touches disk only inside a private temp dir, briefly."""
    context = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
    context.minimum_version = ssl.TLSVersion.TLSv1_2
    with tempfile.TemporaryDirectory(prefix="makeprank-tls-") as folder:
        cert_file, key_file = Path(folder, "c.pem"), Path(folder, "k.pem")
        cert_file.write_bytes(cert_pem)
        key_file.write_bytes(key_pem)
        context.load_cert_chain(str(cert_file), str(key_file))
    return context


def client_context() -> ssl.SSLContext:
    """TLS client context. CA validation is replaced by fingerprint pinning done by the caller."""
    context = ssl.SSLContext(ssl.PROTOCOL_TLS_CLIENT)
    context.minimum_version = ssl.TLSVersion.TLSv1_2
    context.check_hostname = False
    context.verify_mode = ssl.CERT_NONE
    return context


def peer_fingerprint(sock: ssl.SSLSocket) -> str:
    der = sock.getpeercert(binary_form=True)
    if not der:
        raise ssl.SSLError("Kein Serverzertifikat erhalten.")
    return hashlib.sha256(der).hexdigest()
