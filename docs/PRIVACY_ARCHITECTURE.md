# Privacy architecture

## Non-negotiable v1 rules

- The Android app declares no `INTERNET`, SMS, contacts, notification-listener, or account permissions.
- Statements are selected through Android's system file picker and copied into internal app storage.
- The app encrypts its local vault and imported-statement copies using AES-GCM keys generated in Android Keystore.
- The current parser runs on-device only and recognises text-based ICICI credit-card statements. It creates cards from masked card endings, keeps imports separate, and never persists an entered PDF password.
- No actual account names, balances, statement contents, passwords, OAuth tokens, or personal loans are present in source control.
- All categories, account names, card numbers, and peer-ledger entries are entered on-device by the user.

## Future integrations, explicitly deferred

### Google Drive backup

Use encrypted application backups only. Prefer Drive's hidden `appDataFolder` with the narrow `drive.appdata` scope. The encrypted payload remains opaque to Drive; do not upload plaintext statements or database exports.

### Gmail statement import

Use a separate, opt-in module. Gmail read-only access requires `gmail.readonly`, a restricted scope. Public distribution needs OAuth verification; server storage or transmission of restricted-scope data can trigger additional security-assessment requirements. The app must not request Gmail access until manual import, encryption, parser correctness, and deletion flows are complete.

### SMS and notification parsing

Do not add either permission to v1. Google Play restricts SMS access; a money-management exception is possible only with a policy declaration and a true core-function justification. A notification listener also requires the user to enable it in system settings. Both features need a dedicated consent flow, parser allowlist, local-only processing, audit history, and Play-policy review before implementation.

## Threat-model decisions

| Risk | Design response |
| --- | --- |
| Lost/stolen unlocked phone | Encrypted vault; optional future biometric gate before decrypting. |
| App backup or shared storage leak | Store only inside internal app storage; `allowBackup=false`; no external storage. |
| Hard-coded key extraction | Generate non-exportable AES key in Android Keystore. |
| Duplicate imports | Persist a deterministic provider/card/reference key before inserting a normalised transaction. |
| Incorrect automatic categorisation | Preserve original statement evidence, surface review state, and make rules reversible. |
| Credit-card double counting | Record a purchase as spending once; treat a card payment as a transfer/settlement, not new spending. |
| Public source-code disclosure | Use empty first-run state and keep all personal data out of test fixtures and Git. |
