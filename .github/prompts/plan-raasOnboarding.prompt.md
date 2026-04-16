# Plan: RADIUS/PKI/RadSec/EAP Onboarding Curriculum

## Environment
- Host: Mac M2 (ARM64 / Apple Silicon)
- Docker image: freeradius/freeradius-server:latest
- Workspace: /Users/emumba1/Desktop/Repos/raas-onboarding (empty, files will be created here)

## Curriculum Topics (one at a time, user confirms before advancing)

### Topic 1: Networking Fundamentals ✅ (delivered)
- TCP vs UDP, Sockets, 3-way handshake in RADIUS context
- RADIUS port 1812 (auth) / 1813 (acct) UDP
- Host vs container networking on Docker/Mac M2

### Topic 2: RADIUS Fundamentals & AAA
- Docker run command with -X debug flag
- Add testuser to /etc/freeradius/3.0/users
- radtest PAP authentication test
- Read debug logs to verify Access-Accept

### Topic 3: PKI Fundamentals
- Build Mini CA (openssl on Mac or inside container)
- Generate server certificate + key
- Generate client certificate + key
- Verify cert chain

### Topic 4: RadSec Fundamentals
- Configure FreeRADIUS as RadSec proxy (TCP/TLS port 2083)
- clients.conf + proxy.conf changes
- Explain UDP → TLS transport transition

### Topic 5: EAP Basics (TTLS & TLS)
- Configure eap module (mods-enabled/eap)
- Test with eapol_test
- Verify in debug logs

## Delivery Format (per topic)
Each topic delivered as a Markdown snippet with:
- Current Task
- The "Why" (packet-level theory)
- The "How" (step-by-step commands)
- Verification

## Files to create in workspace
- topic-01-networking.md
- topic-02-radius-aaa.md
- topic-03-pki.md
- topic-04-radsec.md
- topic-05-eap.md
- docker/ (config overrides)
- certs/ (generated certs)
