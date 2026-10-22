# TAM Solar Solutions

The existing public website is static HTML/CSS/JavaScript, served by Spring Boot. The enquiry API uses Spring JDBC and MySQL. There is no separate frontend framework or existing user/account system.

## Local setup

1. Create the `TamSolar` MySQL database and an application user. For a local MySQL server, sign in as a MySQL administrator and run the following, replacing the example password with a strong password you choose:

   ```sql
   CREATE DATABASE IF NOT EXISTS TamSolar
     CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
   CREATE USER IF NOT EXISTS 'tamsolar'@'localhost'
     IDENTIFIED BY 'replace-with-a-strong-password';
   ALTER USER 'tamsolar'@'localhost'
     IDENTIFIED BY 'replace-with-a-strong-password';
   GRANT ALL PRIVILEGES ON TamSolar.* TO 'tamsolar'@'localhost';
   ```

   Use the same password in `DB_PASSWORD` in `backend\.env`. The `ALTER USER` makes this setup repeatable when the account already exists; the grant is limited to this application's database.
2. Copy `backend\.env.example` to `backend\.env`, then set database credentials, SMTP credentials, a strong `ADMIN_API_TOKEN`, and `ADMIN_EMAIL`. The local `.env` file is ignored by Git.
3. From PowerShell, start the backend:

   ```powershell
   cd "D:\Tam Solar\backend"
   $env:JAVA_HOME = "C:\Program Files\Java\jdk-17"
   $env:Path = "$env:JAVA_HOME\bin;$env:Path"
   .\mvnw.cmd spring-boot:run
   ```

   Use the installed Java 17 path for your machine if it differs. Spring initializes the existing `inquiries` table if needed and creates the additive workflow, submission-idempotency, and email-outbox tables. Existing enquiry rows are assigned the `NEW` status.
4. In a second terminal, start the frontend:

   ```powershell
   cd "D:\Tam Solar\frontend"
   pnpm install
   pnpm dev
   ```

   Open the Vite URL shown in the terminal (normally `http://localhost:5174`). Vite proxies `/api` to the backend.

The production bundle can be built with `pnpm build` in `frontend`; the Spring Boot package also serves the source static assets from its classpath.

The homepage hero uses the original text-and-solar-photo layout. The photo is served from its configured image URL.

## Enquiries and emails

The current public form collects name, email, customer type, enquiry topic, and message. The API validates and saves these fields before email delivery is attempted. Each accepted enquiry is assigned a database-generated reference such as `TS-000042`; references remain unique under concurrent inserts. The browser reuses an `Idempotency-Key` after network failures, so retrying the same submission returns the original reference without creating another enquiry or sending duplicate confirmation messages.

Confirmation, admin notification, and admin reply emails are recorded in `email_outbox`. A scheduled worker sends pending messages and retries delivery failures with capped exponential backoff, up to eight attempts. A temporary SMTP failure does not discard a saved enquiry or reply; the admin panel shows each delivery state. Delivery is at-least-once if a process stops after an SMTP server accepts a message but before the database records success, so a rare duplicate email is possible.

For Gmail SMTP, enable 2-Step Verification on the sending Google account, create an App Password, and set it as `MAIL_PASSWORD`; use the account address for `MAIL_USERNAME` and `MAIL_FROM`. Do not use a normal Google account password. If not specified, the application defaults to Gmail SMTP on port 587 with STARTTLS and uses `MAIL_FROM` (default `tamsolarsolution@gmail.com`) as the SMTP username. `MAIL_PASSWORD` must still be set to a valid App Password, and `ADMIN_EMAIL` should be set to the inbox that receives new enquiries. Other SMTP providers can be configured with the same `MAIL_*` variables. Keep credentials out of source control.

Every accepted enquiry is stored first and queues one customer confirmation to the submitted email address and one admin notification to `ADMIN_EMAIL`. The scheduled worker attempts delivery within 15 seconds and records failures for retry; it cannot send mail until a valid SMTP account and password are configured. Check the admin panel's email-delivery status for each enquiry if a message does not arrive.

## Admin panel and APIs

Open `/admin.html` and enter the configured `ADMIN_API_TOKEN`. The token is held in browser-tab session storage and sent in the `X-Admin-Token` header. Admin REST endpoints are under `/api/admin/inquiries`; they provide paginated search/filtering, details and conversation, workflow status, private internal notes, and queued replies. Replies always go to the email address saved with the original enquiry. The panel does not include staff assignment because the project has no staff identity or authorization system.

The public API is `POST /api/inquiries`; `/api/health` remains available. Public submissions are limited to eight per remote IP per ten-minute window per application instance. Configure equivalent rate limiting at the production reverse proxy for multi-instance deployments.

For production, terminate HTTPS at a trusted reverse proxy, ensure all admin traffic and the token are sent only over HTTPS, set the public admin-panel URL in `ADMIN_PANEL_URL`, and configure database and SMTP access for that deployment. The sample binds the app to localhost by default; adjust `SERVER_ADDRESS` only when needed behind a protected proxy. Use a unique, high-entropy admin token and rotate it if exposed.

If startup fails with MySQL `Access denied for user 'tamsolar'@'localhost'`, the server is reachable but rejected the configured database credentials. Re-run the account setup SQL above as a MySQL administrator, make `DB_USER` and `DB_PASSWORD` in `backend\.env` match that account, and restart the application. Do not change the schema or delete database files to resolve an authentication error. Email delivery cannot run until the backend has successfully connected to MySQL and started; SMTP credentials are configured separately with the `MAIL_*` settings.

## Email replies from customers

SMTP is outbound-only; replies customers send from Gmail are not automatically imported into the conversation. This requires a separately provisioned inbound integration, such as Gmail API access with OAuth and a mailbox watch/history sync, or an inbound-email provider webhook with signature verification. Configure the provider's credentials, webhook URL, and secure secret storage before adding that integration. No inbound-email credentials or webhook have been assumed or added here.

## Verification

Run backend tests from `backend` with `.\mvnw.cmd test` and build the frontend from `frontend` with `pnpm build`. The email unit tests use a mocked mail sender and do not contact an SMTP server.
