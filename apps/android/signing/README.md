# Android signing

No signing private keys are distributed with this project.

`assembleDebug` uses Android's default development signer. A clean CI runner
can generate a different certificate, so debug APKs cannot guarantee coverage
updates over an existing installation, even when `versionCode` increases.
If Android reports a signature conflict, keep the installed app and its data.
Do not uninstall or clear app data without a confirmed encrypted backup and
the account recovery phrase.

For consistent signed releases, keep a private keystore outside the repository
and configure these Gradle properties or environment variables:

- `HEALTH2609_KEYSTORE_PATH`
- `HEALTH2609_KEYSTORE_PASSWORD`
- `HEALTH2609_KEY_ALIAS`
- `HEALTH2609_KEY_PASSWORD`

Use the same private certificate for subsequent `assembleRelease` builds and
increase `versionCode` for device updates. CI may obtain these values from
GitHub Secrets. Without all four values, release signing remains unconfigured;
the project does not silently substitute a public key.
