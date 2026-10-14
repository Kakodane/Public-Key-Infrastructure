# PKI System

A web-based **Public Key Infrastructure (PKI)** application for issuing, managing, downloading and revoking X.509 certificates. Built as a university project for the *Information Security* course (Team 40).

## Features

**Roles:** Administrator, CA user (organization), Regular user.

| Area | What it does |
|------|--------------|
| Registration | Regular users register with email, password and a public key. The server encrypts a random challenge with that key; the user decrypts it **in the browser (Web Crypto API)** to activate the account. The private key never leaves the client. |
| CA users | Admin creates organizations and CA users; the CA user receives an activation link by email to set a password. |
| Authentication | Email + password login, short-lived JWT access token and longer-lived refresh token. |
| Two-factor auth | Optional TOTP (authenticator app) for regular users, with QR-code setup. |
| Issuance | Root (self-signed), Intermediate and End-Entity certificates with configurable subject (X.500), validity and extensions. Issuer validity (period, signature, revocation status) is checked before issuing. |
| CSR | Upload a CSR (PEM) or let the system auto-generate a key pair and certificate. |
| Download | Certificate + private key as a PKCS#12 keystore, or certificate only (`.cer` / `.pem`). |
| Viewing | Admin sees all certificates, CA user sees their organization's chain, regular user sees their own. |
| Revocation | Revoke with an X.509 reason code; revoked certificates are published through CRL endpoints. |
| Key storage | Private keys are stored encrypted (AES-GCM) with a per-organization key, which is protected by a master key. |
| Master key rotation | Periodic (scheduled) rotation of the master key with re-encryption of dependent data. |
| HTTPS | Frontend and backend communicate over HTTPS; the TLS certificate can be issued by this PKI itself. |

The full (Serbian) assignment is in [`specification.md`](specification.md).

## Tech stack

- **Backend:** Java 21, Spring Boot 4.0.5, Spring Security, Spring Data JPA, PostgreSQL, JJWT, Bouncy Castle, SendGrid, TOTP library
- **Frontend:** Angular 21 (standalone components), RxJS, `angularx-qrcode`, Vitest

## Repository structure

```
backend/ib/     Spring Boot application (REST API under /api)
frontend/ib/    Angular single-page application
docs/HTTPS.md   Step-by-step HTTPS / own-PKI certificate setup
specification.md  Project assignment
```

## Prerequisites

- JDK 21
- Node.js and npm (npm 11 is used by the project)
- PostgreSQL running on `localhost:5432` with an empty database named `ib`
- A [SendGrid](https://sendgrid.com/) API key (for activation / password-reset emails)
- OpenSSL (only for the HTTPS setup)

## Configuration

Secrets are **not** stored in the repository. The backend reads them from environment variables (see `backend/ib/src/main/resources/application.properties`):

| Variable | Required | Description |
|----------|----------|-------------|
| `DB_PASSWORD` | yes | Password of the PostgreSQL user `postgres` |
| `SENDGRID_API_KEY` | yes | SendGrid API key used to send emails |
| `KEYSTORE_PASSWORD` | yes (HTTPS) | Password of the server TLS keystore |
| `KEYSTORE_ALIAS` | no | Keystore alias (default `localhost`) |
| `MASTER_KEY_BASE64` | recommended | Base64 master key for encrypting stored private keys. A **development-only** default is used if unset; always set your own outside local development. |

Also review `jwt.secret` and `app.mail.from` in `application.properties` and replace them with your own values for any real deployment.

## Running locally

The backend is configured for HTTPS and needs a TLS keystore at
`backend/ib/src/main/resources/keystore/keystore_server.p12` (git-ignored). Generating it is a one-time bootstrap described in **[docs/HTTPS.md](docs/HTTPS.md)**. Once it is in place:

**Backend** (`https://localhost:8443`)

```powershell
cd backend/ib
$env:DB_PASSWORD="..."
$env:SENDGRID_API_KEY="..."
$env:KEYSTORE_PASSWORD="..."
./mvnw spring-boot:run
```

**Frontend** (`https://localhost:4200`)

```powershell
cd frontend/ib
npm install
npm start
```

The dev server proxies `/api` to the backend (`frontend/ib/proxy.conf.json`) and expects the PEM key/certificate in `frontend/ib/ssl/` (also git-ignored; see `docs/HTTPS.md`).

### Demo accounts

On first start with an empty database, `AuthDevDataLoader` seeds an organization and demo users for development:

| Email | Role |
|-------|------|
| `admin@ib.local` | Admin |
| `ca@ib.local` | CA user |
| `user@ib.local`, `user2@ib.local`, `user3@ib.local` | Regular user |

The shared demo password is defined in `AuthDevDataLoader.java`. These accounts are for local development only and should be removed or changed before any real deployment.

## API overview

All endpoints are under `/api`:

| Prefix | Purpose |
|--------|---------|
| `/api/auth` | Login, refresh, logout, account activation |
| `/api/registration` | Regular-user registration and key-possession challenge |
| `/api/password-reset` | Account recovery via encrypted challenge |
| `/api/2fa` | TOTP setup, verification, disable |
| `/api/admin` | Organizations, CA users, all certificates, master key status / rotation |
| `/api/ca`, `/api/regular` | Role-specific certificate views and actions |
| `/api/certificates` | Issue root / intermediate / end-entity / CSR / autogenerate, download, revoke |
| `/api/crl` | Certificate Revocation Lists (`/api/crl/{caId}.crl`) |

## Tests

```powershell
cd backend/ib && ./mvnw test
cd frontend/ib && npm test
```

## Security notes

- Never commit keystores, PEM files, `.env` files or API keys (they are covered by `.gitignore`).
- Override all development defaults (`jwt.secret`, master key, demo accounts) before deploying anywhere public.
