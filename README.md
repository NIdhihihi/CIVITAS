# CIVITAS

CIVITAS is a full-stack housing-society election platform: a Spring Boot 3 / Java 17 API with PostgreSQL (or H2 for local development) and an Expo SDK 58 / React Native 0.88 TypeScript app for Android, iOS, and web previews.

It implements the SRS scope in sections 4.1–4.4:

- **Universal voter experience** — public election links, English/Hindi/Marathi UI, email OTP, optional device biometrics, visual ballots with candidate photos, review, dual confirmation, and a downloadable cryptographic receipt.
- **Multi-post and panel elections** — elections with any number of posts, individual-candidate and panel modes, joint panels that span several posts, candidate photos, CSV voter rolls, eligibility per post, and automated invitations / closure notices.
- **Security, anonymity, and audit** — AES-GCM encrypted ballots, a real Pedersen commitment and Schnorr proof of knowledge (`PedersenZkService`), HMAC-based vote nullifiers, SHA-256 + HMAC hash-chained audit ledger, IP/device/identity hashing, fraud alerts for repeated failures and impossible travel, and a verifiable receipt that does not reveal the selected candidate.
- **Automated results and analytics** — tallying immediately after close (and a 15-second scheduler fallback), per-post candidate and panel results, result hash and HMAC signature, public results endpoint, and password-protected, tamper-evident PDF and Excel exports generated with Apache PDFBox and Apache POI.

> **Important:** this is a demonstration system, not a certified election platform. Aadhaar OTP and SMS/WhatsApp delivery are not integrated; the API reports those methods as unavailable. It uses a server-held master secret. Read [Security model and limitations](#security-model-and-limitations) before using it with real voter data.

## Project layout

```text
backend/                 Spring Boot API, security, election, voting, audit, results
  src/main/java/...      Domain modules: auth, elections, voting, results, audit, notifications
  src/main/resources/    application.yml, application-dev.yml
frontend/                Expo React Native app
  App.tsx                App shell, language provider, tab navigation
  src/VotingFlow.tsx     Public voter journey
  src/BallotView.tsx     Shared visual ballot (voter + admin preview)
  src/AdminElections.tsx Election list, creation, detail shell
  src/admin/             Setup, voters, communications, preview, results, audit tabs
  src/Account.tsx        Member/admin sign-in, eligible elections, receipts
  src/ui.tsx             Design system and shared components
  src/i18n.tsx           English / Hindi / Marathi strings
docker-compose.yml       Local PostgreSQL
```

## Prerequisites

- Java 17+
- Maven 3.9+
- Node.js `^22.13.0` or `^24.3.0` and npm (required by Expo SDK 58)
- Docker Desktop (only if you want PostgreSQL; the `dev` profile uses in-memory H2)
- Expo Go compatible with SDK 58 on a phone, or an Android emulator

## Start the backend

### Option A — PostgreSQL (production-like)

```powershell
docker compose up -d postgres
cd backend
mvn spring-boot:run
```

The default API port is `9000`. PostgreSQL defaults match `docker-compose.yml`. Override `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, and `DB_DRIVER` for another database.

### Option B — H2 in-memory (fastest local demo)

```powershell
cd backend
$env:SPRING_PROFILES_ACTIVE = 'dev'
$env:PORT = '9000'
mvn spring-boot:run
```

The `dev` profile uses `jdbc:h2:mem:civitas`, enables the H2 console at `/h2-console`, and provisions a development administrator:

- **Email:** `admin@civitas.local`
- **Password:** `civitas@123`

All data is lost when the process stops. The H2 console is only available in the `dev` profile.

For a stable local setup, create `backend/.env` (ignored by git) with Java-properties syntax:

```properties
PORT=9000
CIVITAS_ADMIN_EMAIL=admin@civitas.local
CIVITAS_ADMIN_PASSWORD=change-this-password
JWT_SECRET=a-private-secret-of-at-least-32-bytes-change-me
CIVITAS_MASTER_SECRET=another-private-secret-of-at-least-32-bytes
CIVITAS_DEMO_SEED_ENABLED=true
CIVITAS_DEMO_PASSWORD=civitas-demo-123
```

The master secret protects vote nullifiers, OTP hashes, audit signatures, receipt signatures, Aadhaar hashes, and result signatures. Set it explicitly before keeping any data.

## Start the app

```powershell
cd frontend
npm install
npx expo start
```

Press `a` for an Android emulator, `w` for a web preview, or scan the QR code with Expo Go.

The frontend detects the Expo development host and targets backend port `9000` automatically. If you need to override it, set `EXPO_PUBLIC_API_URL` in `frontend/.env` (ignored by git):

```properties
EXPO_PUBLIC_API_URL=http://192.168.0.105:9000
```

- Android emulator: `http://10.0.2.2:9000`
- Physical phone: the computer's LAN IP, detected from Expo Go; the computer and phone must use the same network.
- Web preview: `http://localhost:9000`

The app allows cleartext HTTP for local development. Use HTTPS in production. `npx expo install --check` verifies native dependency compatibility.

### Ready-made demo workflow

The local `backend/.env` enables an idempotent demo seed. The `dev` profile enables it by default. Restart the backend after enabling the seed and wait for the `CIVITAS demo data is ready` log line. It adds the `CIVITAS Demo Society`, four elections across active, scheduled, and draft states, individual and panel ballot choices, voter rolls, and two pending candidate applications. It never deletes existing records or resets election status after the first seed.

| Purpose | Sign-in | Password |
| --- | --- | --- |
| Platform admin | `admin@civitas.local` | `civitas@123` (or `CIVITAS_ADMIN_PASSWORD`) |
| Candidate 1 | `demo.candidate@civitas.local` | `civitas-demo-123` |
| Candidate 2 | `demo.candidate2@civitas.local` | `civitas-demo-123` |
| Demo voter account | `demo.voter@civitas.local` | `civitas-demo-123` |

To walk through the app without setting up records:

1. Sign in as the platform admin from **Account → Administrator sign in**. Open **Admin → Elections** to see the live multi-post election, the active panel ballot showcase, the scheduled community poll, and the candidate setup practice election. **Admin → Societies** shows the seeded society; **Admin → Applications** has one application to approve and one to reject.
2. Open **Candidate** and sign in as either demo candidate. Review the seeded application, or submit another position application. Return to the admin console to accept or reject it.
3. Open **Vote**, select **CIVITAS Demo - Live Society Election** to try the individual and panel options across multiple posts, or select **CIVITAS Demo - Panel Ballot Showcase** to try a panel focused ballot. Authenticate as `demo.voter@civitas.local`. Development OTPs are shown in the app. Review the ballot, confirm the vote, and save the receipt. The scheduled poll is for previewing the upcoming-election state; the setup-practice election is for admin ballot configuration.
4. Return to the admin console, close voting from the election overview, then press **Tally & Publish**. Open **Results** to view and export the results and **Audit** to inspect the event ledger.

For PostgreSQL, start the database with `docker compose up -d postgres` before starting Spring Boot. For a disposable H2 walkthrough, use the `dev` profile; its data resets when that process stops. Demo credentials and OTP exposure are for local development only.

## Walkthrough

### 1. Administrator setup

1. Open the **Account** tab and choose **Administrator sign in**.
2. Sign in with the bootstrapped administrator.
3. Open **Admin → Societies** to create a society. Society creation is restricted to platform administrators.
4. Create an election with a title, description, start time, and end time.
5. In **Ballot setup**, add posts. Each post can be:
   - **Individual candidates** — voters choose one candidate.
   - **Panels only** — voters choose a panel.
   - **Candidates or panels** — both options appear on the ballot.
6. Add panels (a panel can contain one candidate per post) and candidates. Candidate and panel photos can be chosen from the device photo library and are stored under `backend/uploads/`.
7. In **Voter roll**, import a CSV:

   ```csv
   name,email,phone,aadhaar,posts
   Asha Rao,asha@example.com,9876500001,111122223333,Chairperson;Secretary;Treasurer
   Ravi Shah,ravi@example.com,9876500002,444455556666,Chairperson
   ```

   - `posts` is optional; leave it blank to make the voter eligible for every post.
   - Use `;` or `|` between post titles.
   - A header row is optional.
   - Provide at least one of email, phone, or Aadhaar.
   - Aadhaar is stored as an HMAC hash plus the last four digits, never in plaintext.
   - Duplicate email, phone, or Aadhaar rows in the same election are skipped with a row-level error.
8. Optionally schedule email **Communications** with `{{name}}`, `{{election}}`, `{{society}}`, `{{startsAt}}`, and `{{endsAt}}` placeholders. SMS and WhatsApp require a delivery provider.
9. Use **Preview** to see exactly what voters will see.
10. Open voting with **Open voting**, or let the scheduler open a scheduled election within 15 seconds of its start time.

### 2. Voter journey

1. The administrator shares the election code (a UUID) from **Overview → Public voting link**, or a deep link such as `civitas://vote?election=<uuid>`.
2. The voter opens the **Vote** tab, pastes the code, and taps **Open ballot**.
3. The voter confirms identity with:
   - **Email OTP** — a six-digit code sent by SMTP or printed to the backend console when SMTP is not configured.
   - Aadhaar OTP is not available until an authorized provider integration is configured.
   - **Device biometrics** — after signing in with a member account whose email is on the voter roll, the app uses the device's local biometric prompt and then calls the authenticated backend endpoint.
4. The visual ballot shows posts, candidates, statements, photos, and panel options. A panel can be applied to every post it covers with **Vote for the full panel**.
5. The voter reviews the selections, requests a confirmation code, and enters the second code to cast the ballot.
6. The ballot is sealed with AES-GCM. Each post receives a deterministic nullifier and a Pedersen commitment with a Schnorr proof. A receipt is returned with a SHA-256 hash, HMAC signature, and verification status.
7. The receipt can be downloaded or checked later from the Vote tab. It proves that a ballot was accepted without revealing the choice.

### 3. Results and audit

1. When voting closes, the election is marked `CLOSED`; an after-commit listener immediately runs the tally and marks it `COUNTED`. A 15-second scheduler also closes and tallies elections whose end time has passed.
2. **Results** shows per-post candidate and panel vote counts, percentages, total ballots, eligible voters, the result hash, the result signature, and whether the audit chain is valid.
3. Enter an export password of at least eight characters and download the **PDF** or **Excel** file. Files are encrypted with the password and embed the result hash and signature.
4. The public results endpoint `/api/public/results/{electionId}` is available after counting.
5. **Audit** verifies the hash chain, lists recent entries, exports the ledger as CSV, and shows fraud alerts for platform administrators and auditors.

## API overview

### Public and authentication

| Method | Endpoint | Purpose |
| --- | --- | --- |
| `POST` | `/api/auth/register` | Create a member account and send an email OTP. |
| `POST` | `/api/auth/verify-email` | Verify the email OTP and receive a JWT. |
| `POST` | `/api/auth/resend-otp` | Resend the email OTP. |
| `POST` | `/api/auth/login` | Member sign-in. |
| `POST` | `/api/auth/admin/login` | Administrator sign-in. |
| `GET` | `/api/auth/me` | Current member profile. |
| `GET` | `/api/health` | Health check. |
| `GET` | `/api/public/elections` | Active elections available from public links. |
| `GET` | `/api/public/elections/{id}` | Public ballot metadata, posts, and panels. |
| `GET` | `/api/public/results/{id}` | Counted results for the public results page. |

### Societies

| Method | Endpoint | Purpose |
| --- | --- | --- |
| `POST` | `/api/tenants` | Create a society. The signed-in creator is promoted to `TENANT_ADMIN` for that society. |
| `GET` | `/api/tenants/mine` | Societies the signed-in member belongs to. |
| `GET` | `/api/tenants` | All societies. |

### Voting

| Method | Endpoint | Purpose |
| --- | --- | --- |
| `GET` | `/api/voting/elections` | Signed-in member's eligible elections. |
| `POST` | `/api/voting/auth/request-otp` | Request an email OTP. Body: `electionId`, `method`, `identifier`. Aadhaar OTP is unavailable. |
| `POST` | `/api/voting/auth/verify-otp` | Verify the OTP and receive a voting session token. |
| `POST` | `/api/voting/auth/biometric` | Create a voting session after device biometrics (requires a bearer token). |
| `GET` | `/api/voting/ballot` | Ballot for the `X-Voting-Session` header. |
| `POST` | `/api/voting/confirm` | Validate selections and send the second confirmation code. |
| `POST` | `/api/voting/cast` | Cast the ballot and return the receipt. |
| `GET` | `/api/voting/receipt/{code}` | Verify a receipt. |
| `GET` | `/api/voting/receipt/{code}/download` | Download the receipt as text. |
| `GET` | `/api/voting/receipts/mine` | Receipt history for a signed-in member. |

### Election administration

| Method | Endpoint | Purpose |
| --- | --- | --- |
| `GET` | `/api/elections/mine` | Elections visible to the administrator. |
| `POST` | `/api/elections` | Create an election. |
| `GET` / `PATCH` | `/api/elections/{id}` | Read or edit an election. |
| `PATCH` | `/api/elections/{id}/status` | `SCHEDULED`, `ACTIVE`, `CLOSED`, or `CANCELLED`. |
| `POST` / `GET` | `/api/elections/{id}/posts` | Add or list posts. |
| `POST` / `GET` | `/api/elections/{id}/panels` | Add or list panels. |
| `POST` / `GET` | `/api/elections/{id}/candidates` | Add or list candidates. |
| `PATCH` | `/api/elections/{id}/candidates/{candidateId}` | Edit a candidate or change approval status. |
| `POST` / `GET` | `/api/elections/{id}/voter-roll` | Import or list the voter roll. |
| `DELETE` | `/api/elections/{id}/voter-roll/{voterId}` | Remove a voter who has not voted. |
| `GET` | `/api/elections/{id}/ballot-preview` | Preview the assembled ballot. |
| `POST` / `GET` | `/api/elections/{id}/communications` | Schedule or list campaigns. |
| `GET` | `/api/elections/{id}/results` | Results (auto-tallies a closed election). |
| `POST` | `/api/elections/{id}/count` | Force a tally after close. |
| `POST` | `/api/elections/{id}/results/export` | Password-protected export. Send `{ "format": "pdf", "password": "..." }` as JSON so the password is not exposed in the URL. |

### Audit

| Method | Endpoint | Purpose |
| --- | --- | --- |
| `GET` | `/api/audit?electionId=...&limit=200` | Recent ledger entries. |
| `GET` | `/api/audit/verify?electionId=...` | Verify the hash chain and signatures. |
| `GET` | `/api/audit/export?electionId=...` | Export the ledger as CSV with a checksum. |
| `GET` | `/api/audit/alerts` | Fraud alerts (platform administrators and auditors). |
| `PATCH` | `/api/audit/alerts/{id}/resolve` | Resolve an alert. |

## Local / free alternatives

The SRS integrations that normally require paid or regulated providers are wired to local substitutes:

| Capability | Local implementation |
| --- | --- |
| Aadhaar OTP | Not implemented. The API rejects this method until an authorized AUA/KUA adapter is configured. |
| SMS | Not implemented. Scheduling SMS returns `501 Not Implemented` instead of claiming a delivery. |
| WhatsApp | Not implemented. Scheduling WhatsApp returns `501 Not Implemented` instead of claiming a delivery. |
| Email | Uses SMTP when `MAIL_HOST`/`MAIL_PASSWORD` are configured; otherwise `EmailService` logs the message and exposes the development OTP. |
| Biometrics | Uses the device's own secure authentication via `expo-local-authentication`; no paid biometric API is called. The backend creates a short-lived voting session only after the device prompt succeeds. |
| PDF / Excel exports | Apache PDFBox and Apache POI run locally; no external document service. |

## Security model and limitations

- **Ballot secrecy:** ballots are encrypted with AES-GCM. Each per-post ballot also stores a Pedersen commitment and a Schnorr proof of knowledge. The tally opens the commitment and checks the proof before counting.
- **Anonymity:** the vote row is linked to an election and post, not to the voter. A deterministic HMAC nullifier prevents more than one ballot per post while avoiding a direct voter-to-vote foreign key. Participation records store a receipt hash/signature, not the selection.
- **Receipts:** the receipt hash covers the election, receipt ID, cast time, number of posts, and a nullifier root. The HMAC signature makes it tamper-evident, but the receipt deliberately does not reveal the vote.
- **Audit ledger:** every important action appends a row whose `entryHash` is SHA-256 over the canonical fields and the previous hash; the row is also HMAC-signed. Verification recomputes the chain. There is no update or delete API for the ledger.
- **Fraud signals:** failed OTP attempts from the same hashed IP trigger a burst alert after five failures in 15 minutes; successful sign-ins from a different hashed IP within ten minutes of a previous sign-in trigger an impossible-travel alert.
- **Uploaded photos:** candidate and panel photos are limited to 5 MB, restricted to image MIME types, and stored under `backend/uploads/` with random names.
- **Not production-grade:** this demo does not provide end-to-end verifiable election proofs, a distributed trust quorum, hardware security modules, ballot reconciliation, legal compliance, or protection against a malicious server administrator. The master secret is held by the backend. Aadhaar OTP is not integrated. Do not use this repository to conduct a real election or to process real Aadhaar numbers without the required legal authorization.

## Environment variables

| Variable | Default | Purpose |
| --- | --- | --- |
| `PORT` | `9000` | API port. |
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `DB_DRIVER` | PostgreSQL `localhost:5432/civitas` | Database connection. |
| `JWT_SECRET` | development fallback | JWT signing key; use a private 32+ byte value. |
| `CIVITAS_MASTER_SECRET` | falls back to `JWT_SECRET` | HMAC/AES master key for ballots, receipts, audit, and Aadhaar hashes. |
| `OTP_EXPIRATION_MINUTES` | `5` | Voting OTP lifetime. |
| `OTP_EXPOSE_IN_DEVELOPMENT` | `true` | Return OTPs in API responses. Set `false` in any shared environment. |
| `VOTING_SESSION_MINUTES` | `10` | Voting session lifetime. |
| `VOTING_CONFIRMATION_MINUTES` | `5` | Second-factor confirmation lifetime. |
| `CIVITAS_UPLOADS_DIR` | `uploads` | Candidate/panel photo directory. |
| `CORS_ALLOWED_ORIGINS` | local development origins | Comma-separated browser origins. |
| `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD`, `MAIL_SMTP_AUTH`, `MAIL_SMTP_STARTTLS` | local log fallback | SMTP configuration. |
| `CIVITAS_ADMIN_EMAIL`, `CIVITAS_ADMIN_PASSWORD`, `CIVITAS_ADMIN_FULL_NAME` | empty in production, dev defaults in `dev` | Bootstrapped platform administrator. |
| `EXPO_PUBLIC_API_URL` | Expo host on port `9000` | App API address. |

## Build and verification

```powershell
# Backend compile
cd backend
mvn -q -DskipTests compile

# Frontend type check and bundle
cd ../frontend
npm run typecheck
npx expo install --check
npx expo export --platform android --output-dir .expo-export-check
```

The backend was previously exercised with a multi-post election workflow covering society and election creation, tenant-admin sign-in, posts, candidates, a panel, CSV voter import, email-OTP ballot casting, duplicate-vote blocking, automatic tally, PDF and Excel exports, and audit-chain verification (`auditChainValid=true`). Aadhaar OTP is not implemented.

## License / status

This repository is a demonstration project. It has no production support commitments. Review and harden it — database migrations, HTTPS, secret management, rate limiting, monitoring, backups, independent security review, and the legal requirements for any election you run — before deploying it.
