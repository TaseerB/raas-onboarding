# Topic 4: RadSec Fundamentals

## Current Task

Configure FreeRADIUS to listen for **RadSec** connections on TCP port 2083 with
mutual TLS, using the certificates from Topic 3. Understand why and when you replace
plain UDP RADIUS with encrypted TCP/TLS transport.

---

## The "Why" — Backend Theory

### The Problem with Plain RADIUS over UDP

```
NAS ──[UDP/1812]──> RADIUS server
     ↑
     Password XOR-encoded with shared secret
     No encryption of any attribute
     No certificate-based peer authentication
     Shared secret = the ONLY security control
```

Risks:
- If the shared secret is weak or leaked, all traffic can be decrypted offline
- No integrity protection of most attributes (only authenticator field)
- Susceptible to attribute stuffing / forged replies if secret is known
- UDP has no transport-layer confidentiality

### RadSec: RADIUS over TLS (RFC 6614) / RADIUS over DTLS (RFC 7360)

RadSec wraps RADIUS packets inside a **TLS session over TCP** (port 2083).

```
NAS ──[TCP SYN]──────────────────────> RADIUS server :2083
    ──[TLS ClientHello]──────────────>
    <─[TLS ServerHello + server.crt]──
    ──[client.crt + Finished]────────>
    <─[TLS Finished]─────────────────
    ──[RADIUS Access-Request (inside TLS tunnel)]──>
    <─[RADIUS Access-Accept (inside TLS tunnel)]───
```

Benefits over UDP:
| Property            | UDP RADIUS          | RadSec (TCP+TLS)               |
|---------------------|---------------------|--------------------------------|
| Encryption          | None (just XOR)     | Full TLS 1.2/1.3               |
| Peer auth           | Shared secret only  | Certificate-based (mTLS)       |
| Integrity           | Partial (MD5 hash)  | TLS MAC/AEAD                   |
| Replay protection   | None                | TLS sequence numbers           |
| Inter-domain safe?  | No                  | Yes                            |

### When to use RadSec

- RADIUS traffic crossing the **public internet** (e.g., eduroam federation)
- Multi-domain roaming federations (each domain has its own CA)
- Any environment where the "shared secret" model is insufficient
- Replacing IPsec tunnels between NAS and RADIUS server

### Port 2083

Port 2083 is the IANA-assigned port for **RADIUS over TLS** (RadSec). It is TCP,
not UDP. Your Docker container must expose it with `-p 2083:2083/tcp`.

---

## The "How" — Step-by-Step

### Prerequisites
- Certificates from Topic 3 must exist under `certs/`
- FreeRADIUS Docker container stopped (we will rebuild the mount)

### Step 1: Copy certs into the docker/ config directory

```bash
cd ~/Desktop/Repos/raas-onboarding

# Create a certs dir inside docker/ for mounting into the container
mkdir -p docker/certs

cp certs/ca/ca.crt      docker/certs/ca.pem
cp certs/ca/dh          docker/certs/dh
cp certs/server/server.crt docker/certs/server.pem
cp certs/server/server.key docker/certs/server.key
cp certs/client/client.crt docker/certs/client.pem
cp certs/client/client.key docker/certs/client.key

# Lock down key permissions
chmod 600 docker/certs/server.key docker/certs/client.key
```

### Step 2: Create the RadSec listen block

Create `docker/radsec-listen.conf` — this will be mounted into the container and
included from `radiusd.conf`.

```bash
cat > docker/radsec-listen.conf << 'EOF'
# RadSec listener — TCP/TLS port 2083
# Included via: radiusd.conf → $INCLUDE /etc/freeradius/3.0/radsec-listen.conf

listen {
    type      = auth
    transport = tls
    port      = 2083

    tls {
        private_key_file   = /etc/freeradius/3.0/certs/server.key
        certificate_file   = /etc/freeradius/3.0/certs/server.pem
        CA_file            = /etc/freeradius/3.0/certs/ca.pem
        dh_file            = /etc/freeradius/3.0/certs/dh
        cipher_list        = "DEFAULT"
        tls_min_version    = "1.2"

        # Require clients to present a valid certificate (mTLS)
        require_client_cert = yes
        verify {
            client              = yes
            check_cert_cn       = no
        }
    }
}
EOF
```

### Step 3: Create a RadSec client entry

Create `docker/radsec-client.conf`:

```bash
cat > docker/radsec-client.conf << 'EOF'
# RadSec client definition
# This allows any client presenting a cert signed by our CA
# "radsec" shortname tells FreeRADIUS to use the special TLS shared secret
client radsec_client {
    ipaddr          = 127.0.0.1
    proto           = tls
    secret          = radsec        # RFC 6614 mandates literal "radsec"
    require_message_authenticator = yes
    nas_type        = other
}
EOF
```

### Step 4: Start the container with RadSec config mounts

Update `docker/run-radius.sh` (or run manually):

```bash
docker rm -f freeradius 2>/dev/null || true

docker run -it --rm \
  --name freeradius \
  -p 1812:1812/udp \
  -p 1813:1813/udp \
  -p 2083:2083/tcp \
  -v ~/Desktop/Repos/raas-onboarding/docker/users:/etc/freeradius/3.0/users:ro \
  -v ~/Desktop/Repos/raas-onboarding/docker/certs:/etc/freeradius/3.0/certs:ro \
  -v ~/Desktop/Repos/raas-onboarding/docker/radsec-listen.conf:/etc/freeradius/3.0/radsec-listen.conf:ro \
  freeradius/freeradius-server:latest -X
```

In `radiusd.conf` (inside the container), FreeRADIUS automatically includes `*.conf`
from the config directory, so the listen block will be picked up. If not, exec into
the container and add the include manually:

```bash
# If the listen block isn't picked up automatically
docker exec -it freeradius bash
echo '$INCLUDE /etc/freeradius/3.0/radsec-listen.conf' \
  >> /etc/freeradius/3.0/radiusd.conf
# Then reload: kill -HUP $(pgrep radiusd)
```

---

## Verification

### Step 5: Test the TLS connection with openssl s_client

```bash
# From your Mac host
# This tests the TLS handshake (not a full RADIUS exchange yet)
openssl s_client \
  -connect 127.0.0.1:2083 \
  -cert ~/Desktop/Repos/raas-onboarding/certs/client/client.crt \
  -key  ~/Desktop/Repos/raas-onboarding/certs/client/client.key \
  -CAfile ~/Desktop/Repos/raas-onboarding/certs/ca/ca.crt \
  -verify_return_error
```

Expected output (key lines):
```
depth=0 CN=radius.example.com
verify return:1
---
Certificate chain
 0 s:CN=radius.example.com
   i:CN=RaaS-Lab-CA
---
SSL handshake has finished
```

If you see `SSL handshake has finished` → TLS mutual authentication succeeded.

### Debug log indicators (container terminal)

```
(TLS) Recv TLS 1.x record type handshake ...
(TLS) <<< TLS 1.x, Certificate
(TLS) >>> TLS 1.x, Certificate
(TLS) Connection Established
```

### Packet-level inspection with tcpdump (on Mac)

```bash
# Capture RadSec TCP traffic on loopback (requires Wireshark or tcpdump)
sudo tcpdump -i lo0 -n 'tcp port 2083' -w /tmp/radsec.pcap

# Then open radsec.pcap in Wireshark — you will see:
# - TCP SYN/SYN-ACK/ACK (3-way handshake)
# - TLS Client Hello / Server Hello
# - Encrypted Application Data (the RADIUS packet is invisible inside TLS)
```

---

## Key Concepts Checklist

- [ ] RadSec = RADIUS over TCP/TLS — RFC 6614, port 2083
- [ ] Shared secret for RadSec is always the literal string `"radsec"` (RFC mandated)
- [ ] mTLS: both server and client present certificates signed by the trusted CA
- [ ] `openssl s_client` is the quickest way to test a TLS listener
- [ ] tcpdump on port 2083 shows encrypted blobs — the RADIUS payload is hidden
- [ ] DH params file (`dh`) enables Perfect Forward Secrecy (PFS) cipher suites
- [ ] UDP RADIUS has no transport-layer encryption; RadSec provides full TLS protection

---

*Next: Topic 5 — EAP Basics (configure eap module for TTLS & TLS, test with eapol_test)*
