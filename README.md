# Toll

Android app that makes Instagram more expensive the more you use it; messages stay free. See [PLAN.md](PLAN.md).

Current stage: **Phase 0 probe**. A floating label over Instagram shows which screen Toll thinks is open. Nothing is blocked or counted.

## Build and install (from WSL)

Android Studio on Windows provides the JDK, SDK and `adb`. `build.cmd` points Gradle at them.

```bash
cd /mnt/c/Users/Petr/toll
cmd.exe /c build.cmd assembleDebug          # build
cmd.exe /c build.cmd installDebug           # build + install on the connected phone
cmd.exe /c build.cmd :app:testDebugUnitTest # JVM unit tests (classifier, probe, rules)
```

Only one session runs Gradle at a time.

`adb` lives at `/mnt/c/Users/Petr/AppData/Local/Android/Sdk/platform-tools/adb.exe`:

```bash
alias adb=/mnt/c/Users/Petr/AppData/Local/Android/Sdk/platform-tools/adb.exe
adb devices                      # phone must show as "device", not "unauthorized"
adb logcat -s TollProbe          # live classifications, scroll events, dump and navigation results
```

Always install from the PC (`installDebug` or Studio). An APK opened on the phone gets Android's "restricted settings" block on the accessibility toggle.

## Phone setup (once)

1. Settings → About phone → tap **Build number** 7 times → Developer options → **USB debugging** on. Connect the cable and allow the PC.
2. **Advanced Protection off** (Settings → search "Advanced Protection"). With it on, Android 17 blocks Toll's accessibility service.
3. Play Store → Instagram → ⋮ → untick **Enable auto update**.
4. After installing: Settings → Accessibility → **Toll** → on.

## Phase 0 walkthrough

Open Toll for the checklist. For each screen: open it in Instagram, wait a second, check the label's guess, tap **Dump**. **DMs** and **Story** on the label test the gate's free shortcuts.

Pull the dumps to the PC (debug build only):

```bash
adb exec-out run-as com.petr.toll tar -cf - files/dumps > dumps.tar && mkdir -p dumps && tar -xf dumps.tar -C dumps
```

Dumps contain view IDs and layout only. Message screens, unrecognised screens and screens with a text field keep no text at all; elsewhere only short labels (up to 3 words) survive. `dumps/` is git-ignored.

Once a human has confirmed what a dump shows, copy it to `app/src/test/resources/fixtures/<SCREEN>/` (e.g. `fixtures/DM_THREAD/`). `SignaturesAssetTest` then checks that the classifier gets it right.

## Code map

| Path | What |
|---|---|
| `app/src/main/assets/signatures.json` | Screen rules (seed guesses until Phase 0 confirms them) |
| `classifier/` | Pure Kotlin: screen model, rule matching, DM-origin tracking |
| `probe/` | Phase 0 tooling: tree snapshot, redacted dumps, label overlay, navigation shortcuts |
| `service/TollAccessibilityService.kt` | Ties it together; logs to `TollProbe` |
| `rules/` | Pure Kotlin rules engine (limits, tiers, visits, prices, commitments) |
