# CI & releases

Two GitHub Actions workflows live in `.github/workflows/`:

| Workflow | Trigger | What it does |
|---|---|---|
| `ci.yml` | push to `master`, any pull request, manual | Unit tests (Robolectric + boxart matcher), debug APK, uploads APK + UI previews + test reports as artifacts |
| `release.yml` | push a tag `v*`, manual (tag input) | Unit tests, signed release APK, GitHub Release with generated notes and the APK attached |

Both use JDK 17 (Temurin), Gradle 8.9 via the wrapper, and install Android SDK platform 35 / build-tools 35.0.0 on the runner.

## Cut a release

```bash
git tag v0.2.0
git push origin v0.2.0
```

The tag must be `vMAJOR.MINOR.PATCH`. The workflow derives the APK version from it:

- `versionName` = `0.2.0`
- `versionCode` = `MAJOR×10000 + MINOR×100 + PATCH` → `200`

Nothing needs editing in `build.gradle.kts`; `-PversionName` / `-PversionCode` override the defaults
(`0.1.0` / `1`) only for that build.

Retry a release without moving the tag: run the **Release** workflow manually and pass the tag.

## Signing (one-time setup)

Without a key the workflow still publishes, but the APK is **debug-signed** and is named
`kartrid-<version>-debug.apk`. Debug keys differ per machine, so users would have to uninstall
before every update. Set up a real key once:

```bash
keytool -genkeypair -v \
  -keystore kartrid-release.jks \
  -alias kartrid \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -storepass '<STORE_PASSWORD>' -keypass '<KEY_PASSWORD>' \
  -dname "CN=Iqbal Adi Nur, O=Kartrid, C=ID"

base64 -w0 kartrid-release.jks > kartrid-release.jks.b64
```

Add these repository secrets (Settings → Secrets and variables → Actions, or `gh secret set`):

| Secret | Value |
|---|---|
| `KEYSTORE_BASE64` | contents of `kartrid-release.jks.b64` |
| `KEYSTORE_PASSWORD` | store password |
| `KEY_ALIAS` | `kartrid` |
| `KEY_PASSWORD` | key password |

```bash
gh secret set KEYSTORE_BASE64  < kartrid-release.jks.b64
gh secret set KEYSTORE_PASSWORD --body '<STORE_PASSWORD>'
gh secret set KEY_ALIAS          --body 'kartrid'
gh secret set KEY_PASSWORD       --body '<KEY_PASSWORD>'
```

Keep `kartrid-release.jks` and its passwords backed up somewhere safe — **not** in this repo
(`*.jks`, `*.keystore` and `android/keystore.properties` are gitignored). If the key is lost,
existing installs can never be updated in place again.

## Signed build locally

Create `android/keystore.properties` (gitignored) and the normal Gradle tasks will sign release
builds with it:

```properties
KEYSTORE_FILE=/absolute/path/to/kartrid-release.jks
KEYSTORE_PASSWORD=…
KEY_ALIAS=kartrid
KEY_PASSWORD=…
```

```bash
cd android && ./gradlew :app:assembleRelease
# → app/build/outputs/apk/release/app-release.apk
```

Environment variables with the same names take precedence, which is what CI uses.

## Checking a build

```bash
~/devices/android-dev/sdk/build-tools/35.0.0/apksigner verify --print-certs \
  android/app/build/outputs/apk/release/app-release.apk
~/devices/android-dev/sdk/build-tools/34.0.0/aapt dump badging \
  android/app/build/outputs/apk/release/app-release.apk | head -1
```
