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

The current school API uses demo identity headers, matching the backend competition-demo shell. These are not secure account authentication. Android's E2EE private journal/recovery is **not implemented in this iOS client**; its absence is visible in Settings. Campus records can be shared by the same assigned identifiers, but this does not promise cross-platform private journal sync.

Production endpoint: `https://h2609.lunarlab.uk`. The app uploads selected meal images transiently for AI recognition and stores the confirmed nutritional record. HealthKit raw samples and routes stay on device.
