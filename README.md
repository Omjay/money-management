# BhaiPaisa / Money Management

Privacy-first personal money manager for Android, with a future iOS companion and optional web viewer.

## Current status

This repository is being used as the project home while the product is being redesigned from the existing Android APK. It now includes an Android/Jetpack Compose foundation for an offline-first private vault; the current interactive phone preview remains the design reference.

The first product milestone is a reliable offline-first Android application. It works without an account or network connection after setup, with manual statement import through the Android system file picker. Automatic Gmail and notification/SMS ingestion are planned integrations and are deliberately not enabled in v1.

## Current Android build

The `app/` module contains the native Android foundation.

- Jetpack Compose screens for Home, accounts, cards, people/loans, and insights.
- Local, encrypted vault using AES-GCM with a non-exportable Android Keystore key.
- Encrypted copies of manually selected PDF statements in internal app storage.
- On-device parsing for text-based ICICI savings and credit-card statements, including separate cards in a combined statement, dated transactions, duplicate suppression, balance-derived bank debits/credits, and local merchant categorisation.
- On-device OCR fallback for image-based HDFC savings statements. The bundled recognition model works offline after installation; its library-provided network permissions are explicitly removed at manifest merge time.
- No `INTERNET`, SMS, contacts, notification-listener, or Google-account permissions.
- Empty first-run state: no personal financial data is embedded in source code or test fixtures.

The current build intentionally does **not** yet parse HDFC or other bank/account PDFs, connect Gmail, read SMS/notifications, or sync Drive. Those integrations require parser fixtures, consent design, policy review, and tests before they can be enabled safely. PDF passwords entered during manual import are used only for that import and are never stored.

### Prerequisites to build the private APK

- Android Studio (current stable) with Android SDK Platform 35.
- JDK 17.
- A physical Android phone with USB debugging enabled, or an emulator.

Open this folder in Android Studio, let it sync dependencies, then use **Build > Build APK(s)**. The output will be `app/build/outputs/apk/debug/app-debug.apk`. Never commit signing material, PDFs, statement passwords, or the on-device vault. The debug APK is ignored by default until a release policy is added.

## Product goals

- Show available money first, across multiple bank accounts.
- Support multiple savings accounts and multiple credit cards, including ICICI and HDFC.
- Import bank statements locally and retain encrypted source PDFs for re-parsing and duplicate detection.
- Parse transactions into useful categories such as Food & Groceries, Investments, Bills, Transport, Peer Transfers, and Miscellaneous.
- Learn local merchant rules: Swiggy, Zomato, chai/tea, and similar merchants should map to Food & Groceries. Unknown merchants remain in Miscellaneous until reviewed.
- Provide category charts and drill-down transaction history by merchant and date.
- Detect peer-to-peer transfers above ₹10,000 as candidates for credit/loan review, while allowing the user to mark a transaction as one-time.
- Show forecasts, pending card payments, account balances, and a review queue without sending personal financial data to a server.

## Privacy and security principles

- Offline-first by default.
- No email, SMS, notification, or bank data leaves the device unless the user explicitly enables a future integration.
- Gmail access, when implemented, must be read-only and limited to the required messages.
- Statement passwords used for future automated sources must be stored only in device-protected encrypted storage. The current manual importer does not retain passwords. Never commit passwords, PDFs, tokens, or personal statement data to Git.
- Optional Google Drive sync is intended for encrypted application state only and must be clearly opt-in.
- All automatic classifications must remain reviewable and reversible.

## Planned architecture

### Android

- Native Android application with a phone-first interface.
- Local encrypted database for normalized accounts, cards, transactions, merchant rules, peer ledgers, and parser results.
- Local encrypted file storage for imported statements.
- Parser adapters per bank/card format, with a common normalized transaction model.
- Deterministic transaction fingerprinting for duplicate detection.
- Work scheduled on-device for refresh and re-processing.

### Data ingestion

1. Manual PDF import works offline.
2. The PDF is decrypted locally using the account-specific password supplied by the user.
3. The parser extracts transactions and records the source fingerprint.
4. Merchant rules and category mappings are applied.
5. Uncertain or unknown rows go to a review queue.
6. The user can correct a category; the correction becomes a local rule.

Future optional sources:

- Gmail read-only statement and transaction-email access.
- Android notification access.
- Android SMS access, subject to Play policy and explicit consent.
- Encrypted, opt-in Google Drive backup/sync.

## Core screens

- **Home:** money available now, attention items, and 30-day outlook.
- **Money:** account balances, cards, forecasts, and pending payments.
- **People:** peer-to-peer ledgers and suggested loan balances.
- **Activity:** normalized recent transactions.
- **Insights:** category pie chart, merchant groupings, Miscellaneous review, and dated drill-downs.

## Classification examples

| Source pattern | Category | Review behavior |
| --- | --- | --- |
| Swiggy, Zomato, chai/tea, grocery merchants | Food & Groceries | Show merchant-level history |
| Groww and other investment references | Investments | Keep separate from spending |
| Large person-to-person transfer above ₹10,000 | Peer Transfer | Suggest credit/loan review |
| Unrecognised merchant | Miscellaneous | Ask user to classify and remember locally |

## Repository rules

Never commit:

- Bank statements or screenshots containing personal data.
- Statement passwords.
- Gmail credentials, OAuth tokens, API keys, or Drive credentials.
- Release keystores or signing credentials.
- Generated APKs unless a release policy is added later.

Use local sample fixtures with masked values for parser tests. Keep provider-specific data isolated so additional banks and cards can be added without changing the core ledger.

## Roadmap

1. Establish the native Android project and local data model.
2. Add manual PDF import and parser test fixtures for ICICI and HDFC.
3. Add encrypted storage and deterministic duplicate detection.
4. Implement account/card management and the five-screen navigation.
5. Implement merchant rules, category insights, and peer-ledger review.
6. Add Gmail, notification, and SMS integrations only after privacy and Play policy review.
7. Add opt-in encrypted Drive sync.
8. Prepare an iOS implementation using the same normalized data model and product behavior.

## Development note

The GitHub destination for this project is the user's `Omjay/money-management` repository. Commits and future pushes must use that account and must not use an Exaqube organization or identity.
