# Topic 1: Networking Fundamentals

## Current Task

Understand TCP vs. UDP, sockets, and the 3-way handshake — specifically how they
apply to a FreeRADIUS server running inside a Docker container on a Mac M2.

---

## The "Why" — Backend Theory

### UDP vs. TCP at the Packet Level

| Property        | UDP                          | TCP                            |
|-----------------|------------------------------|--------------------------------|
| Connection      | Connectionless               | Connection-oriented            |
| Reliability     | No retransmit, no ACK        | ACKed, retransmitted on loss   |
| Header overhead | 8 bytes                      | 20–60 bytes                    |
| Latency         | Lower (no handshake)         | Higher (handshake required)    |
| RADIUS uses it? | YES (ports 1812/1813)        | Only for RadSec (port 2083)    |

**Why RADIUS chose UDP:**
RADIUS was designed in the early 1990s for NAS (Network Access Server) environments
where thousands of auth requests fire concurrently. UDP's lack of per-packet
connection state keeps the server stateless and scalable. Lost packets are simply
retried at the application layer by the NAS client (configurable retry/timeout).

---

### Sockets

A **socket** is the OS abstraction that glues an IP address + port + protocol into
a handle your process can read/write.

```
Socket = (Protocol, Source IP, Source Port, Dest IP, Dest Port)
Example:  UDP / 0.0.0.0 / 1812 / <any> / <any>
```

When FreeRADIUS starts, it calls `bind(AF_INET, SOCK_DGRAM, port=1812)` — this
creates a UDP socket that "owns" port 1812, ready to receive datagrams from any
NAS client whose shared secret matches.

---

### The TCP 3-Way Handshake (and why RADIUS doesn't use it for auth)

```
Client                        Server
  |--- SYN  ------------------>|   (I want to connect)
  |<-- SYN-ACK ----------------|   (OK, I'm listening)
  |--- ACK  ------------------>|   (Connection established)
  |====== DATA EXCHANGE =======|
  |--- FIN  ------------------>|   (Tearing down)
```

This handshake adds ~1.5 × RTT of latency before the first byte of data. For a
single user login this is imperceptible, but a RADIUS server handling 50,000
concurrent device auth events (e.g., 802.1X port-auth in a large enterprise) would
waste enormous CPU/memory maintaining per-connection TCP state.

> **RADIUS/UDP:** No handshake. The NAS drops an Access-Request datagram directly
> onto the wire. FreeRADIUS processes it and fires back an Access-Accept or
> Access-Reject. Done. One round-trip.

> **RadSec (Topic 4):** When we need encryption + integrity, we pay the TCP/TLS
> handshake cost — but only across untrusted inter-domain links, not inside the
> same network.

---

### RADIUS Packet Flow (UDP)

```
NAS / radtest                          FreeRADIUS (Docker)
      |                                       |
      |--[UDP Access-Request]---------------->|  port 1812
      |   Attributes: User-Name, User-Password|
      |   (password obfuscated with MD5+secret)|
      |                                       |  ← policy evaluation
      |<--[UDP Access-Accept / Reject]--------|
      |   Attributes: Session-Timeout, etc.   |
```

Key points:
- The entire exchange is **two UDP datagrams** (request + response).
- The **shared secret** is never sent on the wire; it is used as an HMAC key to
  sign the packet authenticator field.
- There is no session, no keep-alive, no persistent connection for plain RADIUS.

---

### Docker Networking — Mac M2 Specifics

```
┌─────────────────────── Mac M2 Host ──────────────────────────┐
│  Your terminal                                                │
│  radtest → 127.0.0.1:1812  ───────────────────────────────┐  │
│                              Docker port-map (host→ctr)    │  │
│                              0.0.0.0:1812/udp              │  │
│                              ↓                             │  │
│  ┌───────────────── Docker Container ──────────────────┐   │  │
│  │  freeradius process                                 │   │  │
│  │  bind → 0.0.0.0:1812 (container-internal)          │   │  │
│  │                                                     │   │  │
│  │  127.0.0.1 inside here = THE CONTAINER ITSELF      │   │  │
│  └─────────────────────────────────────────────────────┘   │  │
└───────────────────────────────────────────────────────────────┘
```

Key rules to remember:
1. `127.0.0.1` inside the container ≠ your Mac's `127.0.0.1`.
2. To reach the container from your Mac, `radtest` points to `127.0.0.1` on the
   **host**, which Docker's NAT forwards to the container's port 1812.
3. Docker on Apple Silicon runs a lightweight Linux VM under the hood
   (via Apple Virtualization Framework). The port-map goes:
   `Mac terminal → VM NAT → container`.
4. Always publish both auth and accounting ports:
   `-p 1812:1812/udp -p 1813:1813/udp`

---

## The "How" — Environment Verification

Topic 1 is conceptual. Validate your Docker install is ARM64-ready before Topic 2:

```bash
# 1. Confirm Docker is running and architecture
docker version --format '{{.Server.Arch}}'
# Expected output: arm64  (or aarch64)

# 2. Pull the FreeRADIUS image (ARM64 manifest is selected automatically)
docker pull freeradius/freeradius-server:latest

# 3. Inspect the image architecture
docker inspect freeradius/freeradius-server:latest \
  --format '{{.Architecture}}'
# Expected: arm64
```

---

## Verification

```bash
# Confirm no process is already holding port 1812 (clean slate check)
sudo lsof -iUDP:1812
# Expected: no output (nothing listening yet)

# After Topic 2 you will run this again and see freeradius holding the socket:
# freeradiu  <PID>  root  UDP *:1812
```

### Key Concepts Checklist

- [ ] RADIUS uses UDP (ports 1812 auth / 1813 accounting) — no TCP handshake overhead
- [ ] A socket binds `(protocol, ip, port)` — FreeRADIUS binds `UDP/0.0.0.0/1812`
- [ ] The 3-way handshake (SYN→SYN-ACK→ACK) is TCP-only; used in RadSec (Topic 4)
- [ ] On Mac M2 + Docker: `127.0.0.1` on your host maps to the container via NAT
- [ ] Always publish `-p 1812:1812/udp -p 1813:1813/udp` when running the container

---

*Next: Topic 2 — RADIUS Fundamentals & AAA (Docker setup, testuser, PAP with radtest)*
