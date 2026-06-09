# HTTPS setup (client ↔ server)

All communication runs over HTTPS:

- **Frontend (Angular dev server):** `https://localhost:4200`
- **Backend (Spring Boot):** `https://localhost:8443`
- The frontend reaches the backend through the dev-server proxy (`proxy.conf.json`), so the
  browser only ever talks to `https://localhost:4200`.

The TLS certificate is issued by **our own PKI** (bonus requirement). Keystores and PEM files
are **git-ignored** — every developer generates their own once, following the steps below.

> Bootstrapping note: the PKI app must be running to issue the cert, but the backend is
> configured for TLS and needs the keystore to start. So issue the certificate **first** with
> the backend temporarily on plain HTTP (see step 0), then place the keystore and restart.

## Prerequisites
- OpenSSL on PATH (`openssl version`)
- PostgreSQL running (per `application.properties`)
- An admin account in the system

### Step 1. Run the app in plain HTTP (temporary)

**Backend** — run in folder `backend/ib`:
```powershell
# from backend/ib
$env:DB_PASSWORD="..."; $env:SENDGRID_API_KEY="..."
./mvnw spring-boot:run "-Dspring-boot.run.arguments=--server.ssl.enabled=false --server.port=8080"
```

**Frontend** — make two *temporary* edits so it can reach the HTTP backend, then start it:
1. In `frontend/ib/proxy.conf.json`, change both `"target"` values from
   `https://localhost:8443` to `http://localhost:8080`.
2. Run in folder `frontend/ib`:
   ```powershell
   npm start -- --ssl=false
   ```

Open `http://localhost:4200` and log in as admin. (You'll undo both edits in Step 4.)

### Step 2. Issue the server certificate (in the app UI)

As admin → **Certificates → Issue**:
1. Create a **Root CA** (any organization) if you don't have one.
2. Issue an **End-Entity** certificate signed by that Root, using **autogenerate**:
   - **Common name (CN):** `localhost`
   - **Subject Alternative Names:** `dns:localhost, ip:127.0.0.1`  ← **required** (Chrome rejects
     certs without a matching SAN)
   - **Key Usage:** `digitalSignature`, `keyEncipherment`
   - **Extended Key Usage:** `serverAuth`
3. **Download the PKCS#12 keystore** right after issuing. Remember the password you set —
   you'll need it as `KEYSTORE_PASSWORD`. The downloaded file is typically `keystore_server.p12`.

Also **download the Root CA** certificate (`.pem` or `.cer`) — needed in Step 5.

### Step 3. Put the certificate files in place

You need the keystore for the backend and a PEM cert+key pair for the frontend.

**Backend** — copy the downloaded keystore to (create the `keystore` folder if missing):
```
backend/ib/src/main/resources/keystore/keystore_server.p12
```

**Frontend** — extract PEM from the keystore. Run these where the `.p12` is (e.g. Downloads),
replacing `PASSWORD` with the keystore password from Step 2, and `REPO` with the path to this
repository:
```powershell
openssl pkcs12 -in keystore_server.p12 -nocerts -nodes -passin "pass:PASSWORD" -out REPO/frontend/ib/ssl/server.key
openssl pkcs12 -in keystore_server.p12 -clcerts -nokeys -passin "pass:PASSWORD" -out REPO/frontend/ib/ssl/server.crt
```
(Create the `frontend/ib/ssl` folder first if it doesn't exist. Omit `-passin "pass:PASSWORD"`
to be prompted for the password instead.)

### Step 4. Undo the temporary HTTP edits

In `frontend/ib/proxy.conf.json`, change both `"target"` values back to
`https://localhost:8443`. Stop the backend and frontend from Step 1.

### Step 5. Trust the Root CA in your OS

So the browser shows a valid lock (no warning):
1. Open the Root CA file you downloaded in Step 2.
2. **Windows:** right-click → **Install Certificate** → **Local Machine** → *Place all
   certificates in the following store* → **Trusted Root Certification Authorities**.
   - *(macOS: add to Keychain, mark "Always Trust". Linux: add to the system trust store.)*

---

## Part B — Normal run (HTTPS)

Once Part A is done, this is all you do day to day.

**Backend** — run in folder `backend/ib`. The only HTTPS-specific extra is the keystore
password (set it alongside your usual env vars):
```powershell
$env:KEYSTORE_PASSWORD="<password from Part A, Step 2>"
./mvnw spring-boot:run
```

**Frontend** — run in folder `frontend/ib`:
```powershell
npm start
```

Open **https://localhost:4200**.

> If the keystore alias isn't `localhost`, also set `$env:KEYSTORE_ALIAS="<your alias>"`.
> (The PKI uses the certificate's CN as the alias, so with CN=`localhost` you can ignore this.)

---

## Verify it worked

- The cert has the right SAN:
  ```powershell
  openssl x509 -in frontend/ib/ssl/server.crt -text -noout
  ```
  should list `Subject Alternative Name: DNS:localhost, IP Address:127.0.0.1`.
- `https://localhost:4200` loads with a valid padlock (after Step 5), and login + API calls work.
- The backend is reachable over TLS: `curl -k https://localhost:8443/api/crl/<rootCertId>.crl`.