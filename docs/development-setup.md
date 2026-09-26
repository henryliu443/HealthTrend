# Development Setup

Reproducible steps for building and testing HealthTrend on a fresh macOS (Apple Silicon) machine.

## 1. Toolchain

| Component | Version | Notes |
|---|---|---|
| JDK | **17** | `brew install openjdk@17` |
| Gradle | **8.11.1** | via wrapper (`./gradlew`), no system install needed |
| Kotlin | 2.1.20 | pinned in `gradle/libs.versions.toml` |
| AGP | 8.9.0 | pinned in `gradle/libs.versions.toml` |
| Android SDK | at `~/Library/Android/sdk` | |
| compileSdk / targetSdk / minSdk | 36 / 36 / 35 | |

### Shell environment (`~/.zshrc`)

```bash
export ANDROID_HOME="$HOME/Library/Android/sdk"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export JAVA_HOME="/opt/homebrew/opt/openjdk@17"
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$PATH"
```

### Gradle JDK (`~/.gradle/gradle.properties`)

Kept **outside** the repository so the checkout stays portable across machines:

```properties
org.gradle.java.home=/opt/homebrew/opt/openjdk@17
```

## 2. Android SDK packages

```bash
sdkmanager --licenses
sdkmanager "platform-tools" "platforms;android-35" "platforms;android-36" \
           "build-tools;36.0.0" "emulator" \
           "system-images;android-35;google_apis;arm64-v8a"

avdmanager create avd -n Pixel_8_API_35 \
  -k "system-images;android-35;google_apis;arm64-v8a" -d pixel_8
```

## 3. Robolectric offline runtime (required once per machine)

`:core:data` unit tests run Room against real SQLite via Robolectric.
**Robolectric downloads its Android runtime from Maven Central itself** (not through Gradle),
which is unreliable on this network. Pre-seed the jar into the local Maven repository so the
test task runs fully offline:

```bash
REL=org/robolectric/android-all-instrumented/15-robolectric-12650502-i7
JAR=android-all-instrumented-15-robolectric-12650502-i7.jar
DEST="$HOME/.m2/repository/$REL"
mkdir -p "$DEST"
curl -L --retry 3 -o "$DEST/$JAR" "https://maven.aliyun.com/repository/central/$REL/$JAR"
shasum -a 1 "$DEST/$JAR" | awk '{print $1}' > "$DEST/$JAR.sha1"
```

The artifact version is dictated by Robolectric 4.14.1 for `@Config(sdk = [35])`. If the
Robolectric version in `libs.versions.toml` changes, read the new coordinates from a failing
`testDebugUnitTest` run (message mentions `android-all-instrumented:<version>`).

## 4. Build & test

```bash
./gradlew build                       # everything
./gradlew :core:common:test :core:domain:test   # pure JVM unit tests
./gradlew :core:data:testDebugUnitTest          # Room/Robolectric integration tests
./gradlew :app:assembleDebug                    # APK -> app/build/outputs/apk/debug/
```

## 5. Verified deviations from AGENTS.md

| # | AGENTS.md | Actual | Reason |
|---|---|---|---|
| 1 | §2.1 register JDK 17 via `sudo ln -sfn ...` | `JAVA_HOME` + `org.gradle.java.home` point at Homebrew's `openjdk@17` | `sudo` needs a password; non-interactive automation cannot run it. Equivalent effect, no system directories touched. |
| 2 | §4.3 `meal_logs` indexed on `timestamp` only | also `food_id` | Room requires FK child columns to be indexed; the warning explicitly advises it. Performance-only, no semantic change. |
| 3 | §3.2 pins AGP 8.9.0, §2.2 requires compileSdk 36 | `android.suppressUnsupportedCompileSdk=36` in `gradle.properties` | AGP 8.9.0 was only tested up to compileSdk 35. Suppressing keeps the pinned AGP while honouring the required compileSdk. Revisit when AGP is upgraded. |

## 6. Network note

Direct HTTPS to `repo1.maven.org` (Maven Central) is frequently reset on this network. Gradle
builds still succeed because most artifacts resolve from `dl.google.com` and the plugin portal.
If CI or a fresh machine struggles, add a mirror repository to `settings.gradle.kts`.
Robolectric never uses Gradle repositories — see section 3.
