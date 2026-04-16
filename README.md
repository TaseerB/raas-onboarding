# raas-onboarding

A hands-on curriculum covering the full RADIUS stack — from raw UDP packets to mutual TLS authentication — using FreeRADIUS in Docker on a Mac M2.

## Quick Start

```bash
# Start FreeRADIUS (Topics 2–5)
./docker/run-radius.sh

# Test UDP auth (Topic 2)
radtest testuser testpass 127.0.0.1 0 testing123

# Test RadSec TLS handshake (Topic 4)
openssl s_client -connect 127.0.0.1:2083 \
  -cert certs/client/client.crt \
  -key  certs/client/client.key \
  -CAfile certs/ca/ca.crt -brief <<< ""
```

---

## Curriculum

### Topic 1 — Networking Fundamentals ✅
**What:** TCP vs UDP, sockets, the 3-way handshake — in the context of RADIUS.

**Key insight:** RADIUS runs over UDP (no handshake, stateless, scalable). RadSec upgrades to TCP+TLS when crossing untrusted networks.

**Docker topology:**
```
Mac terminal → 127.0.0.1:1812 → Docker NAT (192.168.65.1) → container :1812
```

Reference: [topic-01-networking.md](topic-01-networking.md)

---

### Topic 2 — RADIUS Fundamentals & AAA ✅
**What:** Start FreeRADIUS in debug mode, add a test user, fire a PAP `Access-Request` with `radtest`, read the debug log.

**Commands:**
```bash
./docker/run-radius.sh           # starts container with users + clients.conf mounted
radtest testuser testpass 127.0.0.1 0 testing123
# → Received Access-Accept ... Reply-Message = "Hello from FreeRADIUS!"
```

**Files changed:**
- `docker/users` — flat-file user with `testuser / testpass`
- `docker/clients.conf` — allows `127.0.0.1` + `192.168.65.0/24` (Docker Desktop gateway)

**Gotcha fixed:** Docker Desktop routes host→container UDP through `192.168.65.1`, not `127.0.0.1`. Default `clients.conf` only trusts localhost → all packets silently dropped until the subnet was added.

Reference: [topic-02-radius-aaa.md](topic-02-radius-aaa.md)

---

### Topic 3 — PKI Fundamentals ✅
**What:** Build a Mini CA with `openssl`, sign a server cert and a client cert, generate DH params.

**Commands:**
```bash
cd certs
openssl genrsa -out ca/ca.key 4096
openssl req -new -x509 -key ca/ca.key -out ca/ca.crt -days 3650 \
  -subj "/CN=RaaS-Lab-CA"
# ... see topic-03-pki.md for full commands
openssl verify -CAfile ca/ca.crt server/server.crt   # OK
openssl verify -CAfile ca/ca.crt client/client.crt   # OK
```

**Artifacts produced:**
```
certs/ca/ca.crt          ← Root CA (self-signed)
certs/ca/dh              ← DH params for PFS
certs/server/server.crt  ← Server cert (signed by CA, SAN=radius.example.com)
certs/client/client.crt  ← Client cert (signed by CA, CN=testclient)
```

Reference: [topic-03-pki.md](topic-03-pki.md)

---

### Topic 4 — RadSec (RADIUS over TLS) ✅
**What:** Enable FreeRADIUS to listen on TCP port 2083 with mutual TLS using the Topic 3 certs.

**Commands:**
```bash
./docker/run-radius.sh   # now also binds :2083/tcp

# mTLS handshake test
openssl s_client -connect 127.0.0.1:2083 \
  -cert certs/client/client.crt -key certs/client/client.key \
  -CAfile certs/ca/ca.crt -verify_return_error -brief <<< ""
# → CONNECTION ESTABLISHED, Protocol: TLSv1.3, Verification: OK
```

**Files changed:**
- `docker/certs/` — combined key+cert `server.pem`, `client.pem`, `ca.pem`, `dh`
- `docker/radsec-site-tls` — patched built-in `tls` site (dh_file enabled, Docker gateway added to `clients radsec`)
- `docker/run-radius.sh` — added cert and TLS site mounts, switched from `-X` to `-fxx -l stdout` (threading required for TCP/TLS)

**Gotchas fixed:**
- `server.pem` must be key+cert concatenated (not cert-only) for FreeRADIUS default EAP/TLS config
- `-X` disables threading → TCP/TLS listeners refuse to start; use `-fxx -l stdout`

Reference: [topic-04-radsec.md](topic-04-radsec.md)

---

### Topic 5 — EAP-TTLS & EAP-TLS 🔄
**What:** Configure the EAP module, test `EAP-TTLS/PAP` and `EAP-TLS` with `eapol_test`.

Reference: [topic-05-eap.md](topic-05-eap.md)

---

## Repository Structure

```
raas-onboarding/
├── topic-01-networking.md      # Theory: UDP/TCP, sockets, Docker networking
├── topic-02-radius-aaa.md      # RADIUS AAA, PAP, debug log walkthrough
├── topic-03-pki.md             # Mini CA, cert generation, chain verification
├── topic-04-radsec.md          # RadSec config, mTLS, packet inspection
├── topic-05-eap.md             # EAP-TTLS, EAP-TLS, eapol_test
├── docker/
│   ├── run-radius.sh           # One-command container launcher
│   ├── users                   # FreeRADIUS flat-file user DB
│   ├── clients.conf            # Allowed NAS clients (UDP + RadSec)
│   ├── certs/                  # Certs staged for container mount
│   │   ├── server.pem          # Combined server key+cert
│   │   ├── client.pem          # Combined client key+cert
│   │   ├── ca.pem              # CA certificate
│   │   └── dh                  # DH parameters
│   ├── radsec-site-tls         # RadSec listen block (port 2083)
│   └── eap                     # EAP module config override
└── certs/
    ├── ca/                     # CA key, cert, serial, DH params
    ├── server/                 # Server key, CSR, cert
    └── client/                 # Client key, CSR, cert
```

## Environment

| Item | Value |
|------|-------|
| Host | Mac M2 (ARM64) |
| Docker image | `freeradius/freeradius-server:latest` (amd64, runs via Rosetta 2) |
| Config path inside container | `/etc/freeradius/` (not `/etc/freeradius/3.0/`) |
| RADIUS auth port | UDP 1812 |
| RADIUS acct port | UDP 1813 |
| RadSec port | TCP 2083 |
| Shared secret (UDP) | `testing123` |
| Shared secret (RadSec) | `radsec` (RFC 6614 mandated) |
| Docker Desktop gateway | `192.168.65.1` (must be in `clients.conf`) |
