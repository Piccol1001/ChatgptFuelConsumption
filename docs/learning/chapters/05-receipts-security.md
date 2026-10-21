# 18. Offline receipt capture and OCR

OCR converts an image into text. Parsing interprets that text as fields. Validation decides whether the fields are safe to use. Saving creates a transaction. These are separate stages; combining them into one “scan” function makes errors difficult to isolate.

```text
Camera / Photo Picker
  -> bounded image import and orientation normalization
  -> optional crop / rotate
  -> bundled on-device text recognition
  -> deterministic receipt parser
  -> local merchant/category mapping and validation
  -> review or strictly gated auto-save
  -> atomic database write and optional image retention
```

The app uses `com.google.mlkit:text-recognition:16.0.1`, the bundled Latin text recognizer. The model increases APK size but avoids requiring a recognition-model download on first use. The Play Services downloadable-model alternative would not meet the same first-install offline requirement.

## Capture without broad file access

The camera integration uses an external camera activity and a narrowly granted FileProvider URI. Import uses Android's image selection mechanism. These patterns avoid requesting broad gallery/storage access merely to select one receipt.

A camera activity can be unavailable, cancelled or denied access. A selected URI can fail when opened. Handle those outcomes without creating an empty transaction or pretending recognition succeeded.

The normalizer reads image metadata and handles EXIF orientation. It bounds input bytes and decoded dimensions before allocation. In this snapshot, incoming files are limited to 25 MiB, normalized photos to 8 MiB, and source dimensions to 150 million pixels. Normalized output has additional dimension and pixel limits.

These checks defend against memory exhaustion as well as ordinary oversized photos. A small compressed file can decode into a very large bitmap.

## Quality and image transformations

The app provides rotation and top/bottom cropping. It does not implement a complete document-scanner perspective correction system. Long receipt handling preserves more useful vertical detail within size bounds, but extremely long or faint receipts may still require a better photograph or manual review.

The quality gate checks size, contrast and a sharpness heuristic. A low-quality result remains editable; it blocks automatic acceptance. Such heuristics are not guarantees of optical quality.

`ReceiptScanner` bridges ML Kit's task callback into a suspending function. It keeps the bitmap alive until recognition completes, even if the UI coroutine is cancelled, then closes the recognizer and recycles image resources. Releasing a bitmap immediately on cancellation while the native recognizer still uses it would be unsafe.

## What offline does and does not promise

Recognition and local parsing do not need a network request. That does not mean every language, layout, blur or faded thermal receipt is readable. The bundled recognizer here is the Latin recognizer; it is not a universal promise about all scripts.

The first-install airplane-mode instrumentation evidence is valuable because a model might otherwise have been cached before the test. Real thermal paper, camera focus and external picker behavior still need physical-device testing.

**Practice:** test a synthetic receipt with the total cut off. The correct behavior is an incomplete draft and review, not selecting the largest remaining number automatically.

# 19. Deterministic extraction, categories and safe auto-save

Receipt parsing is an exercise in conservative interpretation. A receipt may contain subtotal, tax, discount, cash tendered, change and final total. “Choose the largest number” can select RM 100 cash tendered for a RM 23.50 purchase.

The parser normalizes whitespace, reads lines and gathers candidates with priorities. Labels such as GRAND TOTAL and JUMLAH KESELURUHAN receive strong priority. TOTAL, NET TOTAL, AMOUNT PAID and JUMLAH BAYARAN are recognized. Subtotal, tax, change, discount, loyalty and unit-price contexts are excluded from final-total candidates.

If a labelled amount appears on the next numeric-only line, that can be considered. Multiple distinct values at the same best priority do not produce a confident winner.

```text
RESTORAN CONTOH
20/10/2026 12:30
SUBTOTAL       RM 20.00
SST            RM  1.20
TOTAL          RM 21.20
CASH           RM 50.00
CHANGE         RM 28.80
```

The intended final amount is RM 21.20. The parser should not add tax again or use cash/change as the total. The merchant in this example is synthetic.

## Ambiguity and dates

The parser supports common ISO, Malaysian day-first and named-month forms. It validates dates using calendar types, so 31 February is rejected. Expiry and membership-related lines are excluded from transaction-date candidates.

A date like 03/04 is ambiguous without year/context; do not invent missing information. Multiple distinct valid date candidates set an ambiguity flag. A future date blocks auto-save in the current validation.

The parser defaults to MYR but flags several explicitly recognized foreign currency codes as OTHER. This is a heuristic, not a complete currency detector. A foreign receipt with no recognized currency marker could still need human attention. Never present default MYR as proof of origin.

The `ReceiptSuggestions.station` field serves as merchant/station text in current code. Historical naming is not always the perfect domain name. Renaming persisted structures for elegance alone can introduce compatibility work; document their actual meaning.

## Categories are suggestions, not facts

Built-in local rules recognize examples such as McDonald's for food, Lotus's for groceries, Petronas for fuel, TGV for entertainment and Watsons for shopping. Editable local merchant mappings can override or supply remembered behavior.

Merchant normalization removes case and punctuation differences. This improves matching, but can collapse distinct spellings. A mapping should remain editable. Never treat a merchant-category guess as external financial truth.

This snapshot focuses on transaction-level fields and fuel-specific suggestions. It does not provide a full persisted line-item/tax accounting engine simply because optional tax/items appeared in an earlier requirement.

## Review versus Smart Auto-Save

Review is the default. Smart mode requires a clear merchant, valid strict total, acceptable date, MYR, category, acceptable image quality and no duplicate warning. Petrol receipts require review against existing fuel records.

The repository repeats critical checks at save time, including the current mode and merchant mapping. A UI decision made earlier can become stale if a preference changes before commit.

Duplicate protection combines a SHA-256 fingerprint of normalized OCR text with conservative matching of amount and local date against existing expenses. Text fingerprints do not identify every possible photograph of the same receipt: OCR errors can change text. Equal amount/date can also be two legitimate purchases. Therefore possible duplicates require review rather than automatic deletion or blind acceptance.

An accepted receipt writes the expense and scan identity atomically. Repeating the same fingerprint cannot create a second expense. Undo checks the saved expense has not changed, removes the accepted transaction and its scan identity together, then image cleanup follows the committed result.

Ordinary transaction deletion intentionally has different duplicate-history semantics from immediate scan Undo. Read the repository before assuming every deletion should erase every fingerprint.

**Practice:** two receipts both cost RM 15 on the same day. Explain why possible-duplicate review is appropriate, while silently discarding the second would lose potentially valid data.

# 20. Privacy, encryption and key management

“Offline” is an architectural property that requires several checks. The app removes INTERNET and ACCESS_NETWORK_STATE from the merged manifest, uses bundled recognition, avoids cloud services and disables automatic backup. The project also checks and transforms a specific ML Kit telemetry path through build policy.

That advanced dependency policy is version-specific. Updating a library requires rechecking the expected structure and merged result. Do not copy bytecode transformation into an unrelated project without understanding it.

Offline behavior does not protect an unlocked phone from someone looking at the screen. Encryption does not prevent data shown in an explicit export or screenshot. Security needs a clear threat model.

## Five distinct identities or secrets

| Item | Purpose | Must not be confused with |
|---|---|---|
| Android app signing private key | Proves update identity | Database encryption key |
| Keystore master key | Protects local secrets | User backup password |
| SQLCipher database passphrase | Encrypts database storage | Application ID |
| Manual backup password | Opens a portable encrypted archive | Device biometric credential |
| Device authentication | Controls interactive access | Guaranteed recovery of lost encryption keys |

Never hardcode these secrets into source or commit them to Git. A public certificate fingerprint is an identifier, not the private signing key.

## LocalVault and authenticated encryption

`LocalVault` uses AES-GCM with a Keystore-backed key and purpose binding. GCM provides confidentiality and integrity when used correctly. A fresh nonce is critical; reusing a nonce with the same key can break security.

Associated authenticated data binds an encrypted value to its intended context, such as settings or a particular file purpose. It is authenticated but not secret. Copying an encrypted blob into the wrong context should fail validation.

Keystore keys are non-exportable through the normal API. Hardware backing depends on the device and configuration; do not claim every device provides identical hardware isolation. Rooted or compromised devices require a broader threat model.

## Encrypted database and settings

The database has a randomly generated passphrase protected by the local vault. Existing plaintext conversion uses an encrypted candidate, validation and controlled promotion. Missing or unreadable keys do not justify deleting the original database and creating an empty one.

Current settings use typed DataStore with an encrypted serializer. They are not simply plain Preferences DataStore values. Corrupt encrypted settings produce an explicit error with existing data preserved. A legacy preferences reader migrates older settings and cleans up only after encrypted commitment.

## App lock and lifecycle

The app lock coordinates authentication with foreground/background transitions and inactivity. The input event that discovers an expired session must not also trigger a financial action under the lock.

App lock, at-rest encryption and screenshot behavior are separate policies. This project intentionally permits screenshots at the current checkpoint. Do not claim that privacy mode blocks all capture merely because the database is encrypted.

## Logging and external boundaries

Log operation status or sanitized error categories, not receipt text, transaction amounts, passwords or decrypted backup contents. Debug logs often outlive the debugging session.

Private receipt storage is encrypted and separate from the device gallery. Removing a receipt image should preserve the transaction. Export and sharing must be explicit. An external document provider may have its own network behavior outside this app's permission boundary.

**Practice:** explain why copying the encrypted database file to a new phone may not make it readable there. The old device's Keystore protection is not automatically portable; the password-protected backup workflow solves a different problem.

# 21. Backups, restoration and crash-safe coordination

A backup is useful only if it can be restored accurately. The project supports an encrypted archive containing validated financial data, settings and optional receipt images. It uses a password-derived key and authenticated streaming encryption.

The current construction uses Tink AES-256-GCM-HKDF-SHA256 streaming segments and PBKDF2-HMAC-SHA256 with 600,000 iterations and a random salt. These are implementation facts, not advice to invent a custom cryptographic format. Password strength still matters; encryption cannot compensate for a trivial password.

## Untrusted input begins at the file picker

A selected backup may be damaged, malicious or from an incompatible version. Validate format, declared sizes, allowed archive paths, record counts, IDs, references, amounts and settings before applying it. Do not trust a ZIP filename to be a safe filesystem path.

The decrypting stream must be consumed through EOF. Reaching a ZIP end marker does not prove that every remaining authenticated segment has been checked. Otherwise trailing corruption might be missed.

Bound parameters and resource limits matter: an archive should not be able to request an arbitrarily expensive key derivation or expand without limit.

## Database, preferences and files are separate systems

A Room transaction cannot atomically commit DataStore and filesystem changes. The repository uses a pending-settings record as a durable bridge:

1. Validate the candidate backup and stage receipts.
2. Commit database restoration together with a pending settings intent.
3. Apply the settings intent to DataStore.
4. Clear/replay the intent according to successful completion.
5. Finalize and clean up staged files according to the restore protocol.

If the process stops between database and settings steps, startup can replay the pending intent. This is a small example of recovery-oriented coordination across storage boundaries.

Do not describe the entire restore as “one transaction” merely because one part uses `withTransaction`.

## Preservation and failure

Wrong password, invalid data or corrupted ciphertext should fail before replacing valid records. Tests need to check preservation, not merely that an exception was thrown. Staged files must not become permanent orphaned sensitive data after cancellation.

Restoring a valid backup is intentionally different from an app update: restore replaces the chosen dataset according to its workflow; an update migrates existing records in place. Do not use restore or uninstall as a casual workaround for a signing mismatch.

**Practice:** simulate failure after the database commit but before settings apply in an isolated fixture. On restart, expect consistent settings recovery from the pending record, not a second application of unrelated financial transactions.
