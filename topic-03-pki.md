# Topic 3: PKI Fundamentals

## Current Task

Build a **Mini CA (Certificate Authority)** using `openssl` on your Mac M2, generate
a server certificate and a client certificate — then verify the full chain. These
certs will be reused in Topics 4 (RadSec) and 5 (EAP-TLS).

---

## The "Why" — Backend Theory

### What is PKI?

**Public Key Infrastructure** is the system that makes TLS trustworthy.
It answers: "How do I know this server's public key actually belongs to *that* server?"

```
CA (Certificate Authority)
  └── signs ──> Server Certificate  (server proves identity to clients)
  └── signs ──> Client Certificate  (client proves identity to server — mutual TLS)
```

### Certificate Chain of Trust

```
Root CA (self-signed)
    │  "I vouch for everyone below me"
    │
    ├── Server Cert (signed by CA)
    │       Subject: CN=radius.example.com
    │       Public Key: <server's RSA/EC pub key>
    │       Signature: <CA's digital signature over the cert>
    │
    └── Client Cert (signed by CA)
            Subject: CN=testclient
            Public Key: <client's RSA/EC pub key>
            Signature: <CA's digital signature over the cert>
```

When FreeRADIUS (RadSec/EAP-TLS) receives a TLS connection:
1. It presents its **server cert** — client verifies the CA signature.
2. It requests the client's cert — client presents **client cert**.
3. Both sides verify the other's cert was signed by the **same trusted CA**.
4. This is called **mutual TLS (mTLS)**.

### Key vs. Certificate

| File          | Contains                        | Keep secret? |
|---------------|---------------------------------|--------------|
| `ca.key`      | CA private key                  | YES — never share |
| `ca.crt`      | CA public certificate           | No — distribute widely |
| `server.key`  | Server private key              | YES           |
| `server.csr`  | Certificate Signing Request     | No (intermediate) |
| `server.crt`  | Server certificate (CA-signed)  | No            |
| `client.key`  | Client private key              | YES           |
| `client.crt`  | Client certificate (CA-signed)  | No            |

---

## The "How" — Step-by-Step (on your Mac M2)

All commands run on your Mac using the system `openssl` (LibreSSL on macOS is fine
for generating certs; for production use brew's `openssl@3`).

```bash
# Verify openssl is available
openssl version
# LibreSSL 3.x or OpenSSL 3.x — both work

# Optional: use Homebrew OpenSSL for full feature set
brew install openssl@3
export PATH="$(brew --prefix openssl@3)/bin:$PATH"
```

### Step 1: Create the workspace directory structure

```bash
cd ~/Desktop/Repos/raas-onboarding
mkdir -p certs/{ca,server,client}
cd certs
```

### Step 2: Generate the Root CA

```bash
# 2a. Generate CA private key (4096-bit RSA)
openssl genrsa -out ca/ca.key 4096

# 2b. Generate self-signed CA certificate (10-year validity for lab use)
openssl req -new -x509 \
  -key ca/ca.key \
  -out ca/ca.crt \
  -days 3650 \
  -subj "/C=US/ST=Lab/L=Local/O=RaaS-Onboarding/CN=RaaS-Lab-CA"

# Verify the CA cert
openssl x509 -in ca/ca.crt -text -noout | grep -E "Subject:|Issuer:|Not (Before|After)"
```

Expected output:
```
Subject: C=US, ST=Lab, L=Local, O=RaaS-Onboarding, CN=RaaS-Lab-CA
Issuer:  C=US, ST=Lab, L=Local, O=RaaS-Onboarding, CN=RaaS-Lab-CA
Not Before: ...
Not After:  ... (10 years out)
```

Note that Issuer == Subject — this is the definition of a self-signed certificate.

### Step 3: Generate the Server Certificate

```bash
# 3a. Generate server private key
openssl genrsa -out server/server.key 2048

# 3b. Generate a CSR (Certificate Signing Request)
openssl req -new \
  -key server/server.key \
  -out server/server.csr \
  -subj "/C=US/ST=Lab/L=Local/O=RaaS-Onboarding/CN=radius.example.com"

# 3c. CA signs the CSR → server certificate
openssl x509 -req \
  -in server/server.csr \
  -CA ca/ca.crt \
  -CAkey ca/ca.key \
  -CAcreateserial \
  -out server/server.crt \
  -days 825 \
  -extfile <(printf "subjectAltName=DNS:radius.example.com,IP:127.0.0.1\nkeyUsage=digitalSignature,keyEncipherment\nextendedKeyUsage=serverAuth")

# Verify the chain
openssl verify -CAfile ca/ca.crt server/server.crt
# Expected: server/server.crt: OK
```

### Step 4: Generate the Client Certificate

```bash
# 4a. Generate client private key
openssl genrsa -out client/client.key 2048

# 4b. Generate client CSR
openssl req -new \
  -key client/client.key \
  -out client/client.csr \
  -subj "/C=US/ST=Lab/L=Local/O=RaaS-Onboarding/CN=testclient"

# 4c. CA signs the client CSR → client certificate
openssl x509 -req \
  -in client/client.csr \
  -CA ca/ca.crt \
  -CAkey ca/ca.key \
  -CAcreateserial \
  -out client/client.crt \
  -days 825 \
  -extfile <(printf "keyUsage=digitalSignature\nextendedKeyUsage=clientAuth")

# Verify the chain
openssl verify -CAfile ca/ca.crt client/client.crt
# Expected: client/client.crt: OK
```

### Step 5: Bundle for FreeRADIUS (DH params)

FreeRADIUS requires a Diffie-Hellman parameters file for Perfect Forward Secrecy:

```bash
# Generate DH params (2048-bit — takes ~10-30 seconds on M2)
openssl dhparam -out ca/dh 2048

# Final directory layout should be:
# certs/
#   ca/
#     ca.key       ← CA private key (keep secret)
#     ca.crt       ← CA certificate (distribute)
#     ca.srl       ← serial counter (auto-created)
#     dh           ← DH parameters
#   server/
#     server.key   ← server private key
#     server.csr   ← (intermediate, can delete)
#     server.crt   ← server certificate
#   client/
#     client.key   ← client private key
#     client.csr   ← (intermediate, can delete)
#     client.crt   ← client certificate
```

---

## Verification

### Verify each cert was signed by the CA

```bash
cd ~/Desktop/Repos/raas-onboarding/certs

openssl verify -CAfile ca/ca.crt server/server.crt
# server/server.crt: OK

openssl verify -CAfile ca/ca.crt client/client.crt
# client/client.crt: OK
```

### Confirm key-cert pairs match (public key fingerprints must be identical)

```bash
# Server key and cert must match
openssl rsa  -in server/server.key -pubout | openssl md5
openssl x509 -in server/server.crt -pubkey -noout | openssl md5
# Both lines must print the same hash

# Client key and cert must match
openssl rsa  -in client/client.key -pubout | openssl md5
openssl x509 -in client/client.crt -pubkey -noout | openssl md5
# Both lines must print the same hash
```

### Inspect Subject Alternative Names (SAN)

```bash
openssl x509 -in server/server.crt -text -noout | grep -A2 "Subject Alternative"
# Expected:
#   X509v3 Subject Alternative Name:
#     DNS:radius.example.com, IP Address:127.0.0.1
```

---

## Key Concepts Checklist

- [ ] A CA is a trusted third party whose job is to sign other certs
- [ ] Self-signed CA = the CA signs its own cert (Issuer == Subject)
- [ ] CSR (Certificate Signing Request) carries the public key + identity; CA signs it
- [ ] `openssl verify -CAfile ca/ca.crt <cert>` validates the chain
- [ ] Key/cert pairs must match — same public key embedded in both
- [ ] DH params file (`dh`) is required by FreeRADIUS for PFS cipher suites
- [ ] SAN extension (`subjectAltName`) is required by modern TLS clients

---

*Next: Topic 4 — RadSec Fundamentals (configure TCP/TLS port 2083, mount these certs into FreeRADIUS)*
