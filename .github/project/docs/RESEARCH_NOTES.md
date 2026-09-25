# Research notes and product constraints

## Verified design decisions

- Android internal app-specific storage is a suitable location for sensitive app-only files; Android protects it from other apps and encrypts it on Android 10+.
- Android Keystore keys are non-exportable and can be constrained to specific cryptographic operations. Use AES-GCM with a per-file random IV.
- `androidx.security.crypto` file and preference helpers are now deprecated; use standard file I/O with keys generated in Android Keystore instead.
- Drive `appDataFolder` is hidden from users and other Drive apps. It is a better target than visible My Drive for encrypted application backup metadata.
- Gmail `gmail.readonly` is restricted. It must not be used for a public build without completing the appropriate OAuth compliance work.
- Google Play treats SMS as a high-risk permission. Do not add `READ_SMS` until the app has a policy-reviewed implementation and declaration.

## Sources

- Android Keystore: https://developer.android.com/privacy-and-security/keystore
- Android cryptography: https://developer.android.com/privacy-and-security/cryptography
- Android app-specific storage: https://developer.android.com/training/data-storage/app-specific
- Android Storage Access Framework: https://developer.android.com/guide/topics/providers/document-provider
- Google Drive app data: https://developers.google.com/workspace/drive/api/guides/appdata
- Gmail scopes: https://developers.google.com/workspace/gmail/api/auth/scopes
- Google Play SMS policy: https://support.google.com/googleplay/android-developer/answer/10208820
- Google Play user data policy: https://support.google.com/googleplay/android-developer/answer/10144311

