# 一餐一动 iOS / iPadOS

SwiftUI device application, iOS 17+. Open `Health2609.xcodeproj`, select the shared **Health2609** scheme and an iPhone/iPad or a simulator. `Package.swift` remains a source development convenience; a Swift executable is not an installable iOS application.

## Device release packaging

On macOS with Xcode 15+:

```sh
apps/ios/build-ipa.sh
ENABLE_HEALTHKIT=1 apps/ios/build-ipa.sh
```

Output:

- `dist/ios/yicanyidong-ios-resign-unsigned.ipa`: arm64 device bundle, suitable input for a signing/re-signing tool. Does not request the HealthKit entitlement. Manual exercise, lunch, real camera/photo recognition, editing and summary remain available.
- `dist/ios/yicanyidong-ios-healthkit-unsigned.ipa`: same app with HealthKit enabled; the eventual signing profile must authorize `com.apple.developer.healthkit`. The source entitlements are at `Resources/Health2609.entitlements`.

These are **unsigned** IPA files. Enterprise signing/Aisi installation still requires an actual valid certificate, matching provisioning profile, bundle ID and device/distribution authorization. No certificate or profile is included in this repository, and an unsigned IPA cannot be installed directly. Re-sign the chosen IPA using the intended tool and its supplied profile. A generic signing profile without HealthKit should use the `resign` variant. Both variants share a bundle ID and replace each other when installed.

`IOS_VERSION`, `IOS_BUILD_NUMBER`, `IOS_OUTPUT_DIR` and `IOS_DERIVED_DIR` are optional build environment settings. The script performs a device build and packaging only, and runs no tests.

## Implemented flows

- School menu, persistent saved portions, lunch updates and daily summary.
- Manual exercise, energy reference, photo library and real camera capture.
- Photos are decoded, resized to 1600 px and encoded as JPEG before multipart upload, including HEIC originals. Students edit and confirm AI results before saving.
- HealthKit variant requests explicit system permission; errors and no-readable-data preserve previous records. No fabricated demo activity is uploaded. Samples spanning a school boundary are excluded conservatively rather than attributed wholly to outside-school activity.
- School day boundaries use Asia/Shanghai, consistent with the current school deployment. In-school windows must load successfully before health synchronization.
- Settings allow administrator-assigned school/student identifiers and explain the demo identity.

## Current scope

The current school API uses demo identity headers, matching the backend competition-demo shell. These are not secure account authentication. Private account sync is separately authenticated by a Bearer device token and encrypts the journal on device. Campus records are not treated as private account sync records.

Production endpoint: `https://h2609.lunarlab.uk`. The app uploads selected meal images transiently for AI recognition and stores the confirmed nutritional record. HealthKit raw samples and routes stay on device.

## Private journal and encrypted sync

Settings → 私密账号、加密同步与设备管理 now provides registration, password login, recovery-phrase device enrollment, manual sync, device listing/revocation, future-key rotation, password change and recovery reset. 历史 shows local and pulled private records, pending status and deletion tombstones. Anonymous records can explicitly be imported into an account; colliding anonymous entries remain preserved. Logging out retains records under their original account. 切换到此服务的匿名本机记录 lets the user inspect remaining anonymous records. Different `serverURL + username` owners retain separate journals/cursors and never implicitly migrate another account's records.

Today's manual exercise, confirmed home meals and energy reference changes now save to the encrypted local journal **before any network sync**, including when offline. These records are not posted as plaintext to the school's demonstration endpoints. Campus lunch/menu and PE remain separate. Summary uses saved campus lunch portions and PE plus the current owner's private journal; the backend's demo household meals/manual activity/preferences are not imported into a private account. The camera's original photo is transient input to the separately requested AI recognition API and is not stored in the sync payload.

Wire crypto matches the Android implementation: purpose-separated SHA256 salts, PBKDF2-HMAC-SHA256 210000/32-byte keys, AES-256-GCM 12-byte nonce and Base64(ciphertext + 16-byte tag), exact key AAD. New writes use envelope v2 with deletion/source-device metadata bound to AAD; historical v1 remains readable. Pulled timestamp strings remain unchanged for AAD, with nanosecond precision used in conflict ordering. Unknown envelope versions fail without advancing the page cursor. Each pull page validates/decrypts fully, then commits journal and cursor atomically. Push batches are bounded by both 200 records and encoded size; records become clean only after a valid `ok: true` acknowledgement.

The local vault (journal, session token, epoch keys, cursors and account archives) is encrypted with a random key stored as `AfterFirstUnlockThisDeviceOnly` Keychain material. The vault file uses iOS file protection and is excluded from backups. No account password is stored. Read/Keychain/decryption failures preserve the existing encrypted file and show an error. Changing signing identity or deleting the app can make local material unavailable; the user's separately saved recovery phrase is needed to enroll again and retrieve previously uploaded records.

Apple Health summaries use an additional `outside_activity` private entity on iOS. All clients preserve unknown entity types, but Android/Harmony may not yet include this iOS-only health entity in their dashboard totals. `home_meal`, `manual_activity`, and `preference/energy_reference` IDs and payloads match Android's current journal. This source implementation still requires the macOS device build in release CI; no runtime interoperability test is claimed here.
