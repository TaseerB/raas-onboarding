# FreeRADIUS — How It Works (Wiki)

## The One-Line Summary

FreeRADIUS is a **bouncer for your network**. Every device that tries to connect hands a ticket to the bouncer (NAS). The bouncer asks FreeRADIUS: *"Is this person on the list?"* FreeRADIUS checks its books and replies: *"Yes, let them in"* or *"No, turn them away."*

---

## The Three Actors

```
Supplicant          NAS                    FreeRADIUS
(your laptop)   (WiFi AP / Switch)         (the bouncer)

   "I'm Alice"  →  "Is Alice allowed?"  →  checks user DB
                ←  "Yes, here's her key" ←  Access-Accept
```

| Actor | Real-world example | Role |
|---|---|---|
| **Supplicant** | Laptop, phone, IoT device | Wants network access |
| **NAS** (Network Access Server) | WiFi access point, VPN gateway, switch port | Enforces access; relays auth to RADIUS |
| **RADIUS server** | FreeRADIUS | Decides allow/deny; holds user database |

The NAS never sees the user's actual password on the wire in EAP scenarios — it's a dumb relay. FreeRADIUS is the brain.

---

## AAA — The Three Jobs

| Job | Question | RADIUS packet |
|---|---|---|
| **Authentication** | Are you who you claim to be? | Access-Request → Accept/Reject |
| **Authorization** | What are you allowed to do? | Attributes in Access-Accept (VLAN, timeout, bandwidth) |
| **Accounting** | What did you actually do? | Accounting-Request (session start, stop, interim) |

**Analogy:** Think of a hotel.
- **Authentication** = showing your ID at check-in
- **Authorization** = being assigned a room on floor 3, not the penthouse
- **Accounting** = the bill tracking your minibar usage

---

## The RADIUS Packet Exchange

### Simple PAP (Topic 2)
```
radtest / NAS                         FreeRADIUS :1812 (UDP)
    │                                       │
    ├── Access-Request ──────────────────→  │
    │   User-Name = "alice"                 │
    │   User-Password = <XOR encoded>       │  ← not plaintext, but weak
    │                                       │  checks users file / LDAP / SQL
    │                                       │
    │ ←── Access-Accept ────────────────── │
    │   Reply-Message = "Welcome, Alice!"   │
    │   Session-Timeout = 3600              │  ← authorization attributes
```

One round-trip. Two UDP datagrams. Done.

### EAP (Topic 5) — Multi-round dialogue
```
Supplicant           NAS              FreeRADIUS
    │                 │                    │
    ├─ EAP-Start ───→ │                    │
    │                 ├─ Access-Request ──→ │  (EAP-Request/Identity)
    │ ← EAP-Request ─ │                    │
    ├─ EAP-Response ─→│                    │
    │     (identity)  ├─ Access-Request ──→ │
    │                 │                    │  [TLS tunnel built here for TTLS]
    │  ... multiple rounds ...             │
    │                 │ ←─ Access-Accept ── │  + MPPE session keys
    │ ← EAP-Success ─ │                    │
```

**Analogy:** PAP is a password written on a post-it note passed to the bouncer. EAP-TTLS is a private phone call inside a soundproof booth — the password is only whispered once the booth is sealed (TLS tunnel established).

---

## The Module Pipeline

FreeRADIUS processes every packet through a pipeline of **modules**, defined in `sites-enabled/default`:

```
authorize   → Who are you? Load user attributes. (files, ldap, sql)
authenticate → Verify credentials. (pap, chap, eap)
post-auth   → Actions after decision. (logging, CoA)
accounting  → Track session. (detail, sql)
```

Each module can return: `ok`, `reject`, `fail`, `notfound`, `updated` — and the server decides what to do next based on precedence rules.

**Analogy:** It's like a series of airport checkpoints. Passport control (authorize) looks you up. Security screening (authenticate) verifies you. The gate agent (post-auth) stamps your boarding pass.

---

## The Shared Secret — And Why It Matters

Plain RADIUS uses a **shared secret** as the only cryptographic protection:

```
User-Password on wire = MD5(secret + Request-Authenticator) XOR password
```

If someone sniffs the wire AND knows the secret → they can recover the password offline.

**This is why RadSec exists.** RadSec wraps RADIUS inside TLS:
- No shared secret needed (certificate trust instead)
- Full encryption of all attributes
- Mutual authentication — both sides prove identity with certs

| | Plain RADIUS/UDP | RadSec (TCP/TLS) |
|---|---|---|
| Password protection | XOR+MD5 (weak) | Inside TLS 1.3 tunnel |
| Peer auth | Shared secret | X.509 certificates |
| Use case | Trusted internal LAN | Inter-domain, internet |

---

## PKI in 30 Seconds

A **Certificate Authority (CA)** is a trusted referee that signs identity cards (certificates).

```
CA private key  ──signs──→  Server Certificate
                                  │
              "I, the CA, vouch that this public key belongs to radius.example.com"
```

When your client connects to the RADIUS server:
1. Server presents its certificate
2. Client checks: *"Was this signed by a CA I trust?"*
3. If yes → proceed. If no → reject.

With **mutual TLS (mTLS)** the server also demands the client's certificate. Both sides verify each other. No impostor can participate.

**Analogy:** A CA is the government issuing passports. Showing a passport proves your identity. mTLS is like two agents from different embassies both showing their government-issued IDs before exchanging classified documents.

---

## EAP Methods at a Glance

| Method | Outer | Inner | Client cert? | Common use |
|---|---|---|---|---|
| **EAP-TLS** | TLS | — (cert is the auth) | Required | High-security enterprise |
| **EAP-TTLS** | TLS | PAP/CHAP/MSCHAPv2 | Optional | Enterprise WiFi (WPA-Enterprise) |
| **PEAP** | TLS | EAP-MSCHAPv2 | Optional | Windows environments |

All three build a TLS tunnel first, then do the actual authentication inside it. The difference is what goes inside the tunnel.

---

## FreeRADIUS Config File Map

```
/etc/freeradius/
├── radiusd.conf          ← Main config, wires everything together
├── clients.conf          ← Who is allowed to send RADIUS packets (NAS list)
├── users                 ← Flat-file user database (simplest backend)
├── proxy.conf            ← Forwarding rules for roaming/federation
├── mods-enabled/
│   ├── eap               ← EAP dispatcher (TLS, TTLS, PEAP sub-modules)
│   ├── pap               ← PAP password check
│   ├── files             ← Reads the users flat-file
│   └── ...
├── sites-enabled/
│   ├── default           ← Main request processing pipeline
│   ├── inner-tunnel      ← Virtual server for EAP inner auth
│   └── tls               ← RadSec listener (port 2083)
└── certs/
    ├── server.pem        ← Server key + cert (combined)
    ├── ca.pem            ← CA certificate
    └── dh                ← DH parameters
```

---

## Ports Reference

| Port | Protocol | Purpose |
|---|---|---|
| **1812** | UDP | RADIUS Authentication |
| **1813** | UDP | RADIUS Accounting |
| **1812** | TCP | RADIUS/TCP (rare, no TLS) |
| **2083** | TCP | RadSec (RADIUS over TLS) |
| **18120** | TCP | FreeRADIUS inner-tunnel (loopback only) |
