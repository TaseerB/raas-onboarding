# Topic 2: RADIUS Fundamentals & AAA

## Current Task

Start the FreeRADIUS Docker container in debug mode (`-X`), add a test user,
install `radtest` inside the container, and perform a PAP authentication exchange —
then read the debug log to understand every step of the AAA pipeline.

---

## The "Why" — Backend Theory

### AAA: Authentication, Authorization, Accounting

| Pillar           | Question answered                        | RADIUS packet         |
|------------------|------------------------------------------|-----------------------|
| **Authentication**| Who are you? Are you who you claim?     | Access-Request / Accept / Reject |
| **Authorization** | What are you allowed to do?             | Attributes in Access-Accept |
| **Accounting**    | What did you do and for how long?       | Accounting-Request / Response |

### PAP Password Encoding (what actually travels on the wire)

PAP does **not** send the password in plaintext. FreeRADIUS obscures it using:

```
User-Password = MD5(shared_secret + Request-Authenticator) XOR password
```

This means the shared secret is the only thing preventing offline dictionary
attacks against a captured RADIUS exchange. For real deployments, use EAP (Topic 5)
or RadSec (Topic 4) instead.

### The Access-Request / Access-Accept Flow

```
radtest (NAS sim)                        FreeRADIUS
      |                                       |
      |-- Access-Request (UDP/1812) --------->|
      |   Code=1                              |
      |   Identifier=<random byte>            |  ← used to match reply
      |   Request-Authenticator=<16 rand bytes>|
      |   Attributes:                         |
      |     User-Name = "testuser"            |
      |     User-Password = <XOR-encoded>     |
      |     NAS-IP-Address = 127.0.0.1        |
      |     NAS-Port = 0                      |
      |                                       |
      |   [FreeRADIUS decodes password]       |
      |   [checks users file]                 |
      |   [applies policy]                    |
      |                                       |
      |<-- Access-Accept (UDP/1812) ----------|
      |   Code=2                              |
      |   Response-Authenticator=MD5(...)     |
```

### The `users` File

FreeRADIUS's flat-file user database lives at:
`/etc/freeradius/3.0/users`

Format:
```
username    Cleartext-Password := "password"
            Reply-Message = "Hello, %{User-Name}!"
```

The `:=` operator means "set this attribute unconditionally".
The second line (indented) specifies reply attributes sent back in the Access-Accept.

---

## The "How" — Step-by-Step

### Step 1: Start the container in debug mode

```bash
# --name gives it a stable handle for docker exec
# -X tells radiusd to run in foreground with verbose debug output
# Keep this terminal open — the debug log streams here

docker run -it --rm \
  --name freeradius \
  -p 1812:1812/udp \
  -p 1813:1813/udp \
  freeradius/freeradius-server:latest -X
```

You should see output ending with:
```
Ready to process requests
```

### Step 2: Open a second terminal — install radtest

```bash
# Enter the running container
docker exec -it freeradius bash

# Install freeradius-utils (contains radtest)
apt-get update -qq && apt-get install -y freeradius-utils

# Verify
which radtest
# Expected: /usr/bin/radtest
```

### Step 3: Add a test user

```bash
# Still inside the container
# Append testuser to the users file

cat >> /etc/freeradius/3.0/users << 'EOF'

testuser    Cleartext-Password := "testpass"
            Reply-Message = "Hello from FreeRADIUS!"
EOF

# Verify the entry was added
tail -5 /etc/freeradius/3.0/users
```

### Step 4: Reload the server config

```bash
# Send HUP to reload config without restarting
# Find the radiusd PID
kill -HUP $(cat /var/run/freeradius/freeradius.pid 2>/dev/null || pgrep radiusd)
```

> **Alternative:** Stop and restart the container with the user added — see Step 4b below.

#### Step 4b (cleaner): Use a volume-mounted users file (from your Mac)

```bash
# On your Mac — create a local users file with the test entry
mkdir -p ~/Desktop/Repos/raas-onboarding/docker

cat > ~/Desktop/Repos/raas-onboarding/docker/users << 'EOF'
# FreeRADIUS users file — for curriculum testing only
#
testuser    Cleartext-Password := "testpass"
            Reply-Message = "Hello from FreeRADIUS!"
EOF

# Run the container mounting your custom users file
docker run -it --rm \
  --name freeradius \
  -p 1812:1812/udp \
  -p 1813:1813/udp \
  -v ~/Desktop/Repos/raas-onboarding/docker/users:/etc/freeradius/3.0/users:ro \
  freeradius/freeradius-server:latest -X
```

### Step 5: Run radtest

**Option A — from inside the container** (terminal 2):
```bash
# Inside the container
# Syntax: radtest <user> <password> <server> <nas-port> <secret>
radtest testuser testpass 127.0.0.1 0 testing123
```

**Option B — from your Mac host** (requires freeradius-utils on Mac):
```bash
# Install on Mac (Homebrew)
brew install freeradius-server

# Test from Mac host → Docker port-map → container
radtest testuser testpass 127.0.0.1 0 testing123
```

### Expected radtest output

```
Sent Access-Request Id 42 from 0.0.0.0:52345 to 127.0.0.1:1812 length 76
        User-Name = "testuser"
        User-Password = "testpass"
        NAS-IP-Address = 127.0.1.1
        NAS-Port = 0
        Message-Authenticator = 0x00
        Cleartext-Password = "testpass"
Received Access-Accept Id 42 from 127.0.0.1:1812 to 127.0.0.1:52345 length 40
        Reply-Message = "Hello from FreeRADIUS!"
```

---

## Verification — Reading the Debug Log

In the container's terminal (debug mode `-X`), look for this sequence:

```
# 1. Packet received
(0) Received Access-Request Id 42 from 127.0.0.1:52345 to 127.0.0.1:1812

# 2. Decoded attributes
(0)   User-Name = "testuser"
(0)   User-Password = "testpass"          ← decoded from XOR cipher

# 3. Auth module processes request
(0) pap: Login attempt with password "testpass"
(0) pap: Comparing with "Cleartext-Password"
(0) pap: User authenticated successfully

# 4. Policy result
(0) # Executing section authorize from file /etc/freeradius/3.0/sites-enabled/default
(0) Sending Access-Accept Id 42

# 5. Reply attributes
(0)   Reply-Message = "Hello from FreeRADIUS!"
```

**If you see `Access-Reject` instead:**
- Check the shared secret matches (default is `testing123`)
- Confirm the `users` file entry has correct indentation (tab before `Reply-Message`)
- Look for `ERROR` lines in the debug output

---

## Key Concepts Checklist

- [ ] AAA = Authentication + Authorization + Accounting — RADIUS handles all three
- [ ] PAP password is XOR-encoded with MD5(secret + authenticator), NOT plaintext
- [ ] The `users` file is FreeRADIUS's simplest auth backend (flat-file)
- [ ] `-X` flag = foreground debug mode — always use this during development
- [ ] `radtest` simulates a NAS client sending an Access-Request
- [ ] Debug log shows the full AAA pipeline: receive → decode → authorize → reply

---

*Next: Topic 3 — PKI Fundamentals (Mini CA, server cert, client cert with openssl)*
