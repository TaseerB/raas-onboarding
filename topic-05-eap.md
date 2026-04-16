# Topic 5: EAP Basics (TTLS & TLS)

## Current Task

Configure FreeRADIUS's `eap` module to support **EAP-TTLS/PAP** and **EAP-TLS**,
then verify both methods using `eapol_test` inside the Docker container.

---

## The "Why" — Backend Theory

### What is EAP?

**Extensible Authentication Protocol (EAP)** is a framework, not a specific auth
method. It runs *inside* a RADIUS Access-Request/Access-Challenge exchange and allows
the NAS to relay arbitrary authentication dialogs between the supplicant (client) and
the RADIUS server.

```
Supplicant (WiFi client / 802.1X port)
    ↕  EAP over LAN (EAPOL) frames
NAS (Access Point / Switch)
    ↕  RADIUS Access-Request/Challenge carrying EAP-Message attribute
FreeRADIUS
    ↕  internal EAP module processes the dialog
Auth backend (users file / LDAP / SQL)
```

The NAS is a **pass-through** — it doesn't interpret EAP. It just wraps EAP frames
into RADIUS `EAP-Message` attributes and forwards them.

### The RADIUS/EAP Exchange (multi-round)

```
Client                   NAS                    FreeRADIUS
  |--EAP-Start----------->|                          |
  |                        |--Access-Request(EAP)-->  |
  |                        |<-Access-Challenge(EAP)-- |  ← EAP-Request/Identity
  |<-EAP-Request/Identity--|                          |
  |--EAP-Response/Identity>|                          |
  |                        |--Access-Request(EAP)-->  |
  |                        |<-Access-Challenge(EAP)-- |  ← TLS/TTLS tunnel setup
  |         ... multiple TLS handshake exchanges ...  |
  |                        |<-Access-Accept(MPPE)---- |  ← session keys
  |<-EAP-Success-----------|                          |
```

### EAP-TLS vs EAP-TTLS

| Method    | Outer tunnel | Inner auth     | Client cert required? | Complexity |
|-----------|-------------|----------------|----------------------|------------|
| EAP-TLS   | TLS          | None (cert = auth) | YES              | High       |
| EAP-TTLS  | TLS          | PAP/CHAP/MS-CHAPv2 inside tunnel | No (optional) | Medium |

**EAP-TLS:** Both server and client present X.509 certs. The TLS handshake itself
is the authentication. Most secure but requires cert provisioning on every device.

**EAP-TTLS:** Only the server presents a cert (outer TLS tunnel). Inside the
encrypted tunnel, the client sends a username/password via a legacy auth method
(e.g., PAP). This is common in enterprise WiFi where managing per-device certs
is impractical.

### The `eap` Module in FreeRADIUS

FreeRADIUS's EAP logic lives in a loadable module: `mods-enabled/eap`.
It is a dispatcher that hands off to method-specific sub-modules (`tls`, `ttls`,
`peap`, `md5`, etc.) based on the EAP-Type attribute negotiated with the client.

---

## The "How" — Step-by-Step

### Prerequisites
- Certificates from Topic 3 in `certs/`
- FreeRADIUS container running (or ready to restart with mounts)

### Step 1: Create the EAP module configuration override

```bash
cat > ~/Desktop/Repos/raas-onboarding/docker/eap << 'EOF'
# FreeRADIUS EAP module configuration
# Mount at: /etc/freeradius/3.0/mods-enabled/eap
# Overrides the default to point at our Topic 3 certs

eap {
    default_eap_type = ttls        # Default method offered to clients
    timer_expire     = 60
    ignore_unknown_eap_types = no
    cisco_accounting_username_bug = no

    # EAP-TLS (certificate-based mutual auth)
    tls-config tls-common {
        private_key_file   = /etc/freeradius/3.0/certs/server.key
        certificate_file   = /etc/freeradius/3.0/certs/server.pem
        CA_file            = /etc/freeradius/3.0/certs/ca.pem
        dh_file            = /etc/freeradius/3.0/certs/dh
        cipher_list        = "DEFAULT"
        tls_min_version    = "1.2"
        ecdh_curve         = "prime256v1"

        cache {
            enable = no
        }

        verify {
            client              = yes
            check_cert_cn       = no
            check_cert_issuer   = no
        }
    }

    tls {
        tls = tls-common
    }

    # EAP-TTLS (TLS outer tunnel + PAP inner auth)
    ttls {
        tls     = tls-common
        default_eap_type = md5      # inner EAP type (or use PAP)
        copy_request_to_tunnel = yes
        use_tunneled_reply     = yes
        virtual_server         = "inner-tunnel"
    }
}
EOF
```

### Step 2: Mount the EAP config and certs into the container

```bash
docker rm -f freeradius 2>/dev/null || true

docker run -it --rm \
  --name freeradius \
  -p 1812:1812/udp \
  -p 1813:1813/udp \
  -p 2083:2083/tcp \
  -v ~/Desktop/Repos/raas-onboarding/docker/users:/etc/freeradius/3.0/users:ro \
  -v ~/Desktop/Repos/raas-onboarding/docker/certs:/etc/freeradius/3.0/certs:ro \
  -v ~/Desktop/Repos/raas-onboarding/docker/eap:/etc/freeradius/3.0/mods-enabled/eap:ro \
  freeradius/freeradius-server:latest -X
```

Look for in the debug output:
```
modules {
  ...
  eap: Loaded EAP type tls
  eap: Loaded EAP type ttls
```

### Step 3: Install eapol_test inside the container

```bash
# In a second terminal — enter the container
docker exec -it freeradius bash

apt-get update -qq && apt-get install -y eapoltest 2>/dev/null || \
  apt-get install -y wpasupplicant 2>/dev/null
# eapol_test is bundled with wpa_supplicant

which eapol_test
# Expected: /usr/sbin/eapol_test
```

### Step 4: Test EAP-TTLS/PAP from inside the container

Create an `eapol_test` config file inside the container:

```bash
# Inside the container
cat > /tmp/eap-ttls-pap.conf << 'EOF'
network={
    key_mgmt=WPA-EAP
    eap=TTLS
    identity="testuser"
    anonymous_identity="anonymous"
    password="testpass"
    phase2="auth=PAP"
    ca_cert="/etc/freeradius/3.0/certs/ca.pem"
}
EOF

# Run eapol_test
eapol_test \
  -c /tmp/eap-ttls-pap.conf \
  -a 127.0.0.1 \
  -p 1812 \
  -s testing123 \
  -r 1
```

Expected output:
```
EAPOL: Successfully authenticated
CTRL-EVENT-EAP-SUCCESS EAP authentication completed successfully
```

### Step 5: Test EAP-TLS (mutual cert auth)

```bash
# Inside the container
cat > /tmp/eap-tls.conf << 'EOF'
network={
    key_mgmt=WPA-EAP
    eap=TLS
    identity="testclient"
    ca_cert="/etc/freeradius/3.0/certs/ca.pem"
    client_cert="/etc/freeradius/3.0/certs/client.pem"
    private_key="/etc/freeradius/3.0/certs/client.key"
}
EOF

eapol_test \
  -c /tmp/eap-tls.conf \
  -a 127.0.0.1 \
  -p 1812 \
  -s testing123 \
  -r 1
```

---

## Verification — Reading the Debug Log

### EAP-TTLS/PAP success (container debug terminal)

```
# 1. EAP identity exchange
(0) eap: Peer sent EAP Response (code 2) ID 1 length 14
(0) eap: Peer sent Identity "anonymous"

# 2. TTLS tunnel established
(0) eap_ttls: TTLS established — resuming inner auth
(0) eap_ttls: Got tunneled request

# 3. Inner PAP auth (inside TLS)
(0) pap: Login attempt with password "testpass"
(0) pap: User authenticated successfully

# 4. Access-Accept with MPPE session keys
(0) eap: Sending EAP Success
(0) Sending Access-Accept
(0)   MS-MPPE-Recv-Key = ...
(0)   MS-MPPE-Send-Key = ...
```

### EAP-TLS success

```
(0) eap_tls: TLS established
(0) eap_tls: Peer certificate: CN=testclient
(0) eap: Sending EAP Success
(0) Sending Access-Accept
```

### Key debug log patterns to watch for

| Log line                                | Meaning                                      |
|-----------------------------------------|----------------------------------------------|
| `eap: Peer sent Identity`               | Client identified itself                     |
| `TTLS established`                      | Outer TLS tunnel up, inner auth starting     |
| `pap: User authenticated successfully`  | Inner PAP credential check passed            |
| `TLS established` + cert CN            | EAP-TLS mTLS handshake succeeded             |
| `eap: Sending EAP Success`              | RADIUS will return Access-Accept             |
| `eap: ERROR: Failed in EAP sub-module`  | Check certs, inner auth config, users file   |

---

## Key Concepts Checklist

- [ ] EAP is a framework — it runs inside RADIUS `EAP-Message` attributes
- [ ] NAS is a transparent relay; FreeRADIUS drives the EAP state machine
- [ ] EAP-TLS: mTLS — both sides present certs; no password needed
- [ ] EAP-TTLS: TLS outer tunnel + `inner-tunnel` virtual server for credential auth
- [ ] `eapol_test` simulates a WPA-Enterprise supplicant against RADIUS
- [ ] MPPE keys in the Access-Accept → NAS uses these for 802.11 session encryption
- [ ] `default_eap_type = ttls` sets what EAP type FreeRADIUS offers first
- [ ] `ca_cert` in the supplicant config is critical — clients MUST verify the server cert

---

## Curriculum Complete

You have now covered:
1. **Networking Fundamentals** — UDP/TCP, sockets, 3-way handshake, Docker networking
2. **RADIUS Fundamentals & AAA** — PAP auth, users file, radtest, debug log
3. **PKI Fundamentals** — Mini CA, server/client certs, openssl chain verification
4. **RadSec** — RADIUS over TCP/TLS, port 2083, mTLS, shared secret = "radsec"
5. **EAP-TTLS & EAP-TLS** — the eap module, inner-tunnel, eapol_test

Workspace files created:
- `topic-01-networking.md`
- `topic-02-radius-aaa.md`
- `topic-03-pki.md`
- `topic-04-radsec.md`
- `topic-05-eap.md`
- `docker/users` — FreeRADIUS users file with testuser
- `docker/run-radius.sh` — convenience start script
- `docker/eap` — EAP module config override
- `docker/radsec-listen.conf` — RadSec listen block
- `docker/radsec-client.conf` — RadSec client definition
