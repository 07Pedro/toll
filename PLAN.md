# Toll — plan

Status (end of 2026-10-04): Phase 0 probe is installed on Petr's Pixel and has passed screen classification. Still pending: the Story/DMs button-test retry on the 22:28 build, then Phase 1. Paused until 2026-10-05.
Toll is an Android app that makes Instagram more expensive to use the more you use it, while messages with friends stay free.

Split: **Product** (features, screens, flows, v1 scope) is written by the session talking with Petr. **Tech** (stack, data model, services, costs, risks) is written by session petr-c6. Each reviews the other's half.

---

## Product

### Problem
Petr spends 3–5 h/day on Instagram on weekdays and up to 8 h/day on weekends. The worst times are weekends and "every free moment" (reflex opens). They tried *one sec*: its fixed few-second wait didn't work, they just wait it out and go in anyway.

### Decisions from Petr
- **Platform:** Android only. Petr's phone is a **Google Pixel 7 on Android 17**; their PC is Windows with WSL. **Android Studio** gets installed on Windows (Petr approved).
- **Target:** Instagram only.
- **Must keep working:** messages (DMs) and reels that friends send in DMs.
- **Friction must escalate:** it should get gradually worse and more painful over time.
- **Never a hard lock:** there is always a way in, but it gets expensive.
- **No other people involved:** no friend codes, no accountability buddy, no alerts to anyone. Only Petr.
- **Single user:** a personal app installed directly on the phone (sideloaded), not published to the Play Store.
- **Stories are free**, like messages.
- **Week-1 limits approved:** 3 h weekdays, 5 h weekends, **20 min less every week** down to 45 min (final).
- **No browser coverage:** Petr doesn't use Instagram in a browser.
- **Quick pass:** Petr hated that in one sec, showing someone something on Instagram meant waiting first. Toll needs a one-time pass that opens Instagram immediately. **3 per day**, 3 minutes each.

### Design principles
1. **The price keeps rising:** with minutes used today, with the number of opens, and week over week.
2. **You pay by acting, not by waiting:** typing, holding a moving dot, walking to a QR code. Passive waits failed with one sec.
3. **The pain continues inside the app,** not only at the entrance.
4. **Start near current usage and tighten gradually.** A limit far below today's habit gets the app uninstalled.

### Rules (week-1 limits and quick pass count approved by Petr; the other numbers are product defaults Petr can change)
- **Free areas** (don't count toward the limit): the message inbox, chat threads, a single post or reel opened from a chat, and stories. Message notifications open straight into the chat for free.
- **Time on Toll's own screens** (gate, challenges, "Still here?") is never paid.
- **Screens Toll can't recognise** are counted as paid but never gated, so a misread never blocks Petr.
- **Paid areas:** feed, Reels tab, Explore, swiping onward from a friend's reel into more reels, and profiles.
- **Daily limit:** week 1 is 3 h on weekdays and 5 h on weekends, down to a floor of 45 min/day. Weeks count in 7-day blocks from the day Toll is set up, and a day counts as a weekend by its Toll day (so 02:00 on Saturday still uses Friday's limit).
- **Weekly taper: −20 min per week** (Petr's final decision). Weekdays: 3 h, 2 h 40, 2 h 20, 2 h, 1 h 40, 1 h 20, 1 h, then the 45 min floor from week 8. Weekends: 5 h, 4 h 40 … 1 h in week 13, then the 45 min floor from week 14.

  Whichever is chosen, it should be a setting (loosening it follows the 24 h rule).
- **Day boundary:** proposed 04:00 local time, so late-night scrolling counts toward the day it started.
- **Tiers,** by paid minutes used today as a share of the daily limit:

  | Share of limit | What happens |
  |---|---|
  | < 50% | Opens normally. No timer yet. |
  | 50–75% | Before each paid entry, type a sentence exactly (no paste), e.g. "Opening Instagram for the 14th time today." |
  | 75–100% | Before each paid entry, type a longer sentence that also says how long you've spent today ("… I have already spent 2 hours and 20 minutes on it."). While inside, Instagram is greyscale and a full-screen "Still here?" interrupts every 5 min of paid time. |
  | > 100% | Each 10-min pass costs a QR-code scan (code stuck somewhere in another room) **plus** holding a thumb on a slowly moving dot. The hold doubles with each pass that day, **capped at 30 min**: 1, 2, 4, 8, 16, 30, 30… The cap keeps the "never a hard lock" promise, since uncapped doubling reaches 64 and 128 min by pass 7–8. |

- **Visit:** one continuous stay in Instagram. Leaving for less than 30 s (a notification glance, a quick app switch) doesn't end it; leaving for longer or turning the screen off does. Moving between free and paid screens inside one visit doesn't start a new visit.
- **When you pay:** the toll is charged at the first paid screen of a visit. Going feed → DMs → feed in the same visit doesn't charge again.
- **A paid 10-min pass** (over the limit) covers 10 minutes of *paid* time within the same visit. Time in DMs or stories doesn't use it up. Leaving Instagram ends it, so passes can't be saved up.
- **Moving-dot hold:** lifting the thumb or losing the dot pauses the countdown. Being off the dot for more than 10 s resets it to the start. Brief slips are forgiven, but you can't put the phone down.
- **Reflex penalty:** a visit that starts within 10 min of the end of the previous visit with paid time is charged one tier higher. Over the limit, it counts as an extra doubling (still capped at 30 min). Moving between DMs and the feed inside Instagram is never a reflex open.
- **What counts as an "open":** the start of a visit that reaches a paid screen.
- **Crossing a tier mid-visit:** crossing 75% turns on greyscale and "Still here?" immediately. Crossing 100% brings up the gate immediately. Crossing 50% changes nothing until the next visit, because typing is an entry price.
- **From 75% up, the effects stack:** greyscale covers all of Instagram, chats included (it costs nothing, and switching it on and off between screens would flicker). "Still here?" keeps going over the limit too. Paying any toll restarts the 5-minute "Still here?" count.
- **Reflex prices by tier:** under 50% → the short sentence; 50–75% → the longer sentence; 75–100% → QR + a hold at the next pass's length; over the limit → the hold gets one extra doubling.
- **Quick pass** (defaults proposed, Petr can adjust):
  - Started only from a button inside the Toll app, not from the Gate. Having to open Toll on purpose keeps it from becoming the new reflex route.
  - Opens Instagram immediately with no challenge, for **3 minutes of clock time from launch** (leaving and coming back doesn't stretch it), with a countdown in the corner. The countdown turns red for the last 30 s.
  - **3 per day** (reset at 04:00). Available at every tier, including over the limit.
  - During a pass, the gate, challenges, "Still here?" **and greyscale** are all off. The pass is for showing people things, which should look normal.
  - When the pass runs out mid-scroll, the gate appears straight away at the normal price.
  - Minutes used still count toward today's paid minutes, but quick-pass visits are ignored entirely for the reflex penalty: never reflex themselves, and they don't make the next visit reflex.
  - Raising the number or length of quick passes is a loosening change (24 h delay).
- **Commitment:** any change that loosens the rules (higher limits, slower taper, turning tiers off) takes effect after 24 h. Tightening applies immediately.
- **Turning Toll off:** requested inside Toll, takes effect after 48 h. Toll blocks its own pages in Android settings (accessibility toggle, app info, uninstall) until then.
- **Advanced Protection (Android 17)** switches Toll off at once. Toll **never blocks it**, because it's a security feature and Petr must always be able to make the phone safer. When Petr opens that page, Toll shows a one-screen notice ("Turning this on switches Toll off. To pause Toll instead, request a turn-off.") with a plain **Continue** button and no challenge, and logs the event in Toll's history.
- **Escape routes covered:** Instagram Lite gets the same toll. Browsers are not covered (Petr's decision).

### Screens
1. **Onboarding:** what Toll does → grant permissions → set week-1 limits (defaults 3 h / 5 h) and the floor → print or show the QR code to stick up somewhere.
2. **Gate** (shown over Instagram when it lands on a paid screen): today's paid minutes and open count, what this entry costs, what the next one will cost. Buttons: **Messages (free)**, **Stories (free)**, **Pay toll**, **Leave**. Stories needs its own button because the stories tray sits on top of the home feed, which is paid. Toll opens the first story in the tray for you; leaving the story viewer lands back on the gate.
3. **Toll challenges:** typing a sentence; thumb hold on a moving dot with a countdown; QR scan.
4. **Timer** (Petr's decision, 2026-10-05): a small, circular, translucent dial in the **top-left** of the screen, on paid and unrecognised screens, **only from 50% of the daily limit**. It shows today's paid time against the limit. It never takes touches. During a quick pass, the quick-pass countdown takes its place.
5. **"Still here?" interrupt.**
6. **Toll home:** a big **Quick pass** button showing how many are left today ("3 left today"), then today (paid minutes, opens, current tier), this week's limit, a week-by-week trend.
7. **Settings:** limits, floor, taper rate; pending loosening changes with their countdown; the "turn Toll off" request and its countdown.

### v1 scope (proposed)
- **Phase 0, feasibility probe:** a tiny app Petr installs that shows whether Toll can reliably tell DM inbox/thread, a reel from a DM, feed, Reels tab, Explore and stories apart on Petr's Instagram version. Everything below depends on this.
- **Phase 1, core:** detect Instagram, classify the screen, count paid minutes and opens, Gate, the tier ladder (typing, "Still here?", moving-dot hold with doubling), reflex penalty, weekly taper, corner timer, Toll home, quick pass, 24 h delay on loosening. Toll stays easy to switch off during Phase 1 so testing isn't painful.
- **Phase 2, hardening:** greyscale, QR-code challenge, self-protection (blocking its own settings pages + 48 h turn-off), coverage of Instagram Lite.

### Non-goals
Other apps, iOS, the Play Store, multiple users, friends or accountability features, cloud sync, analytics, **Instagram in a browser** on the phone, and **Instagram on other devices** (PC browser, tablet).

### Success looks like
Paid Instagram minutes **on the phone** go down week over week roughly in line with the limit, and Petr still has Toll installed after 2 weeks. (Toll can't see Instagram on other devices.)

### Open questions
None right now. Answered 2026-10-05: the timer shows only from 50% (top-left, small, circular, translucent); git approved and set up (local repo, first commit `5fdc3df`).

### Product UI so far (Phase 0)
- Theme in `ui/TollTheme.kt`: concrete greys, barrier red (paid, brand), go green (free), signal amber (attention), Overpass font (motorway-sign lettering), barrier-stripe motif. The app icon is a T whose crossbar is a striped barrier arm.
- Petr found the first probe UI "really bad". Every user-facing screen gets a real design pass, test screens included.
- Lesson from the walkthrough: anything slower than ~300 ms must show instant feedback, or Petr presses repeatedly.

---

## Tech

### Summary
Toll is a native Kotlin app built around one **AccessibilityService**. The service sees which Instagram screen is in front, classifies it as free or paid, meters paid time, and puts Toll's own windows (gate, timer, challenges) over Instagram. Everything runs and is stored on the phone. The app declares **no INTERNET permission**, so although it can read Instagram's screens (including DMs), it has no way to send anything anywhere. Running cost: **$0** (no server, no Play Store fee).

### Stack
| Piece | Choice | Why |
|---|---|---|
| Language, UI | Kotlin, Jetpack Compose | Standard Android today; Compose also draws the overlay windows. |
| SDK levels | `targetSdk` = latest stable; `minSdk 33` | One device: Petr's Pixel 7, now on Android 17. `minSdk 33` (the version it shipped with) also allows testing on older emulator images. |
| Storage | Room (history), DataStore (settings, pending changes) | On-device, no account. |
| QR scanning | CameraX + ZXing | Works offline, no Google Play Services dependency. |
| Tests | JUnit on the JVM | Rules engine and classifier are tested without a phone. |
| Package | `com.petr.toll` | Sideloaded only. |

No DI framework, no networking, no analytics libraries.

### Build and install
- **Android Studio on Windows owns the toolchain:** SDK, its bundled JDK (JBR) and `adb.exe`. The project lives at `C:\Users\Petr\toll`. Claude sessions in WSL drive it through the Windows tools: `cmd.exe /c gradlew.bat installDebug` (with `JAVA_HOME` pointing at Studio's `jbr` folder) and `adb.exe logcat`. This builds on the fast Windows disk and uses USB without WSL passthrough. Petr never has to open the IDE, but can for the debugger or Layout Inspector. (Checked 2026-10-04: no Android tools installed yet on either side; WSL can call Windows programs.)
- **Only one session runs Gradle at a time.** Two builds in the same folder fight over Gradle's locks.
- **Phone connection:** USB with *USB debugging* on (Settings → About phone → tap Build number 7 times → Developer options). If Windows doesn't recognise the Pixel, install the *Google USB Driver* from Studio's SDK Manager → SDK Tools. Wireless debugging also works as a fallback.
- **Restricted settings, verified:** on Android 13+, an APK opened from a file manager or browser can't have its accessibility service switched on until Petr allows it. Apps installed with `adb install` (which is what Studio and `gradlew installDebug` use) are **not** restricted ([source](https://www.esper.io/blog/android-13-sideloading-restriction-harder-malware-abuse-accessibility-apis)). Always install via adb. If Toll ever does show "Restricted setting": Settings → Apps → Toll → ⋮ → *Allow restricted settings* → confirm with PIN/fingerprint, then enable the service again.
- **One signing key, backed up.** An update signed with a different key forces an uninstall, which wipes Toll's history and the grant below. Keep the debug keystore (`C:\Users\Petr\.android\debug.keystore`) in a backup, or create a dedicated key in Phase 1.
- **One-time grant for Phase 2** (greyscale, self-protection): `adb.exe shell pm grant com.petr.toll android.permission.WRITE_SECURE_SETTINGS`. It survives updates, not uninstalls.
- **Advanced Protection must stay off (verified; it's an Android 17 change, not 16):** with Android's Advanced Protection mode on, apps that aren't accessibility tools can't use accessibility services. Turning the mode on revokes Toll's access at once, and it can't be re-granted until the mode is off again ([source](https://www.androidauthority.com/android-17-beta-2-advanced-protection-mode-accessibility-apps-3648860/), [source](https://thehackernews.com/2026/10/android-17-advanced-protection-locks.html)). Toll is not an accessibility tool and won't claim to be: `isAccessibilityTool` stays unset.

### Phone setup checklist
1. Developer options → **USB debugging** on.
2. **Advanced Protection off** (Settings → search "Advanced Protection").
3. Play Store → Instagram → ⋮ → untick **Enable auto update**.
4. Toll installed **from the PC** (Studio or `gradlew installDebug`), never by opening an APK on the phone.
5. Settings → Accessibility → Toll → **on**.
6. Phase 2 only: the `WRITE_SECURE_SETTINGS` grant above. No battery-saver exceptions are needed on a Pixel.

### Architecture
```
Instagram / Instagram Lite / Android Settings
          │ accessibility events (throttled, about 5 per second)
          ▼
TollAccessibilityService
  ├─ ScreenClassifier    which screen is in front (feed, Reels tab, DM thread, story…)
  ├─ SessionTracker      visits, free or paid, where it was opened from, quick pass
  ├─ Rules               today's limit, tier, price of the next entry (pure Kotlin)
  ├─ OverlayController   gate, corner timer, "Still here?", moving-dot hold
  ├─ Greyscale           Phase 2
  └─ Guard               Phase 2: blocks Toll's own Settings pages
          │
          ▼
Room + DataStore  ◄──  Toll app (home, quick pass, settings, onboarding, typing + QR challenges)
```
**Service setup:** listens to window-state, window-content and scroll events; reports view IDs; sees all interactive windows. No package filter, because it must notice when Instagram leaves the screen; events from unrelated apps are dropped immediately. A 200 ms event throttle and direct view-ID lookups (instead of walking the whole tree) keep battery use low. Screen on/off comes from system broadcasts.

### Screen classifier (the main risk)
- **Signals, cheapest first:** app package; window class name; known view IDs (looked up directly); the bottom tab bar's labels and which tab is selected; presence of the DM message input.
- **Naming (confirmed in Phase 0):** inside Instagram's code, Stories are "reel" (`reel_viewer_*`) and Reels are "clips" (`clips_*`).
- **Signatures are data, not code:** a `signatures.json` asset maps signals to screens. When Instagram changes, we fix the JSON, not the logic.
- **"Reel opened from a DM" is context, not a screen.** The classifier only reports "Reels viewer"; it can't tell a DM reel from the Reels tab by looks. SessionTracker decides:
  - Reels viewer entered directly from a DM thread → **free** (remember the reel's author + caption).
  - The author/caption changes, or a vertical scroll event fires in the viewer → the user swiped onward → **paid** from that moment.
  - Reels viewer reached any other way (Reels tab, feed, Explore, profile) → **paid**.
  - Back to the thread resets the context.
- **Stories are free** in every context, so the story viewer needs no context tracking.
- **Failure policy:** the DM input field is the most stable signal and wins over everything, so an Instagram update can make Toll leaky but shouldn't make DMs paid. Screens Toll can't recognise are **metered as paid but never gated**, so a misread never locks Petr out. If unrecognised screens exceed ~10% of a day's Instagram time, Toll notifies Petr and saves a dump automatically.
- **Pin Instagram's version:** Play Store → Instagram → ⋮ → untick *Enable auto update*, so it only changes when Petr chooses. After each update, re-run the Phase 0 walkthrough.
- **Regression tests:** every Phase 0 dump becomes a test fixture; JVM tests check the classifier against all of them.

### Time counting
- Paid time runs only while the screen is on, Instagram (or Instagram Lite) is visible, the screen is paid, and no Toll window covers it.
- Durations use the phone's monotonic clock. The wall clock only decides which day a minute belongs to: `day = local date of (now − 4 h)`. Toll compares both clocks, so manually changing the phone's time doesn't start a new day.
- Every screen change plus a 30-second heartbeat is written to the database. Android may kill the service at any moment; at most 30 s is lost.
- **Visits** are the unit of charging. A visit starts when Instagram comes to the front. When Instagram leaves the front, a 30 s grace timer starts: coming back within it continues the same visit; otherwise, or when the screen turns off, the visit ends at the moment Instagram left.
- **Charging:** the toll is charged once, at the first paid screen of a visit. Free ↔ paid moves inside the visit never charge again. An **open** is a visit that reaches a paid screen.
- **Reflex:** a visit is reflex if it starts within 10 min of the end of the previous visit that had paid time. Quick-pass visits are ignored for this, both as the reflex visit and as the "previous visit".
- **Over the limit,** a paid pass gives the visit a budget of 10 paid minutes; time on free screens doesn't use it. The budget dies with the visit. When it runs out mid-visit, the gate returns with the next pass's price.
- **Crossing a tier mid-visit** (confirmed by Product): crossing 75% turns on greyscale and "Still here?" immediately; crossing 100% brings up the gate immediately. Crossing 50% changes nothing until the next visit, since typing is an entry price.
- Split screen: Instagram counts whenever its window is visible.

### Rules engine
Pure Kotlin with no Android code: `limitFor(day)`, `tier(paidMinutes, limit)`, `priceOfVisit(state)`, `holdMinutes(pass)` (1, 2, 4, 8, 16, then capped at 30; a reflex visit adds one doubling, still capped), `effectiveSettings(now)` (applies loosening changes only 24 h after they were requested). The taper is a setting with a kind and an amount; Petr chose `Subtract(20 min)` per week (`Multiply(x)` also exists), so changing it later is a value, not code. Unit tests cover tier boundaries, the 04:00 rollover, weekends, the floor, doubling and its 30-min cap, visit boundaries (the 30 s grace, screen-off), the reflex bump, the per-visit pass budget, quick-pass counting and the 24 h / 48 h delays. Most logic bugs would live here, so it's kept out of the service.

### Enforcement
- **Overlay windows** are accessibility overlays owned by the service. They need no "display over other apps" permission, and Android lets taps pass through them (since Android 12 it blocks that for ordinary opaque overlays), which the corner timer relies on.
- **Gate:** full-screen overlay over Instagram. *Leave* → Android Home. *Messages (free)* → Toll clicks Instagram's DM icon through accessibility (fallback: Instagram's direct-inbox link). *Stories (free)* → Toll clicks the first story in the tray at the top of the feed. Accessibility clicks reach Instagram underneath the gate, so the gate stays up until the classifier confirms the DM or story screen; the paid feed never flashes. Leaving the story viewer lands on the feed, which brings the gate back by itself. Both clicks are tested in Phase 0.
- **Tapping like a finger (`canPerformGestures`):** Instagram 449 refuses accessibility clicks on story bubbles. The free shortcuts therefore try a click first, then tap the target's centre with an injected gesture. Toll's own panel or gate is removed for that moment, so the tap reaches Instagram. This is a more powerful permission, since Toll can tap anywhere on screen; it's covered by the same "view and control screen" consent, and Toll still has no internet access. Verified 2026-10-04: adding the capability in an update kept the service enabled (capabilities 1 → 33), with no re-confirmation.
- **Corner timer:** small overlay that ignores touches.
- **"Still here?":** full-screen overlay every 5 min of paid time in tier 3.
- **Moving-dot hold:** full-screen overlay that keeps the screen on. The countdown runs only while the thumb is on the dot. Lifting it or slipping off pauses; more than 10 s off the dot resets it to the start.
- **Typing and QR challenges** run in a Toll activity opened over Instagram. The keyboard and camera work reliably in an activity, and Android lets apps with an active accessibility service open activities from the background.
- **No pasting:** suggestions off, no copy/paste menu, and any single edit that inserts more than one word is rejected. That blocks paste and autofill but still allows swipe typing.
- **QR code:** onboarding generates a random secret, shows it as a QR code and saves a PNG to print. Only that code is accepted. Known, accepted bypass: a photo of the code on another screen.
- **Quick pass:** Toll home's button stores `quickPassUntil = now + 3 min`, uses up one of today's passes, and opens Instagram with its launch intent (needs a `<queries>` entry for `com.instagram.android` in the manifest). While a pass is active, the gate, challenges, "Still here?" and greyscale are all off, and a countdown replaces the corner timer (red for the last 30 s). The pass runs on wall-clock time from launch. When it expires, the gate appears straight away at the normal price. Paid minutes keep counting, and the visit isn't recorded as an open. Number and length of passes (default 3 × 3 min) are settings.
- **Greyscale (Phase 2):** Toll switches Android's built-in colour correction to monochrome (`accessibility_display_daltonizer_enabled=1`, `accessibility_display_daltonizer=0`) while Instagram is in front in tier 3+. It restores Petr's previous values when Instagram leaves and again whenever the service starts, so a crash can't leave the phone grey.

### Self-protection (Phase 2)
- **Guard** watches Settings (`com.android.settings`) and the package installer for Toll's App info page, Toll's accessibility page and the uninstall dialog. It matches on visible page titles plus the word "Toll", which survives Android updates better than view IDs. On a match it presses Back and shows when the turn-off request completes.
- **Advanced Protection is never blocked.** It's a security feature, and Petr must always be able to make the phone safer. When its settings page opens, Guard shows a one-screen notice ("Turning this on switches Toll off. To pause Toll instead, request a turn-off.") with a plain **Continue** button: no challenge, no wait. The notice shows once each time the page is opened, and the event is logged in Toll's history.
- **Backstop:** a background job every 15 min checks that Toll's service is on. If it's off without an approved turn-off, Toll switches it back on by writing Android's enabled-services setting (allowed by the adb grant). It never tries while Advanced Protection is on.
- **Accepted escapes** (effortful, in line with "never a hard lock"): Safe Mode, `adb uninstall` from the PC, factory reset, and Advanced Protection (deliberately left open, see above).
- The Pixel runs stock Android, which doesn't kill accessibility services the way some brands' battery managers do, so no brand-specific workarounds are needed.

### Instagram Lite (Phase 2)
- **Instagram Lite** probably exposes little to accessibility, so all Lite time counts as paid; DMs stay free in the main app. To verify in Phase 2.
- **No browser coverage:** Petr doesn't use Instagram in a browser (Product decision).
### Data model
| Store | Contents |
|---|---|
| `visit` table | start/end time, day, reached paid?, tier charged, reflex?, quick pass?, pass budget used |
| `segment` table | visit, start/end time, app, screen, paid?, opened from DM? |
| `toll` table | time, day, kind (typing / hold / QR / still-here), pass number, required seconds, completed? |
| `classifier_miss` table | time, Instagram version, dump file |
| `guard_event` table | time, page (App info / accessibility / uninstall / Advanced Protection), outcome (sent back / continued) |
| DataStore | week-1 limits, taper kind + amount, floor, day start hour, start date, quick pass count + length (3 × 3 min), QR secret, pending loosening changes with their effective time, turn-off request time, Petr's original colour settings |

Today's paid minutes, tier, limit and passes left are always computed from these, never stored. Size: well under 1 MB a year.

### Phase 0 probe: concrete spec
- **Day-one shortcut, before any app exists:** once the phone is connected, `adb.exe shell uiautomator dump` captures the current screen's full view tree with resource IDs. Petr opens each screen and a Claude session pulls a dump, stripping all text first so no message content is read. It may fail on constantly animating screens such as Reels ("could not get idle state"), which is why the probe app below is still needed.
- **The probe app** is the first build of Toll itself with enforcement off, so the code carries forward. A floating label shows the live guess ("DM thread", "Reels tab"…) and the signals behind it: package, window class, matched view IDs, selected tab.
- **Tapping the label** saves the current screen's structure to JSON: view IDs, class names, content descriptions, positions, selected/scrollable state, plus Instagram's version. **Message text is redacted** (any text or description longer than a few words becomes its length), so friends' messages never leave the phone.
- **Every classification change** goes to logcat with a timestamp, so the DM-reel → swipe → paid transition can be checked live with `adb.exe logcat`.
- **Petr's walkthrough (~15 min):** feed (with the stories tray at the top), Reels tab, Explore grid and a post from it, search, a story from the tray, own profile, a friend's profile, DM inbox, a DM thread, a reel from a DM and then one swipe onward, a post and a story shared in a DM, a comments sheet, a DM notification tap, a "liked your post" notification tap.
- **Navigation test:** two debug buttons on the label, *Open DMs* and *Open first story*, perform the same accessibility clicks the gate will use.
- Dumps come to the PC with `adb pull`; Claude turns them into signatures and test fixtures.
- **Pass criteria:** DM inbox, threads and stories are never labelled paid; every paid screen is labelled correctly ≥95% of the time over a normal 10-min session; the swipe onward from a DM reel is detected within one reel; the stories tray's first story and the DM icon are found and opened by the debug buttons every time.

### Phase 0 results (2026-10-04, Instagram 449.0.0.52.84, Pixel 7 on Android 17)
- **Screens recognised:** feed (including the stories tray), Reels tab, Explore grid, search, a reel from Explore, a story from the tray, own and friend's profiles, DM inbox, DM thread, comments sheet, a reel from a DM and the reel after it, and a post shared in a DM. All 15 scrubbed dumps are test fixtures (`app/src/test/resources/fixtures/<SCREEN>/`) and classify correctly.
- **DM reel → swipe → paid works:** live and as a replay test (`DmReelReplayTest`). The item key (author + caption) changes on the swipe.
- **What Instagram 449 looks like to accessibility:**
  - Bottom tabs `feed_tab`, `clips_tab`, `direct_tab` (DMs are a tab), `search_tab`, `profile_tab`. Often only the tab's inner `tab_icon` is marked selected, so tabs are matched by ID with "selected anywhere inside".
  - Reels are `clips_*` and stories are `reel_viewer_*`, as predicted.
  - Reels and posts opened from a DM run in `com.instagram.modal.TransparentModalActivity`; the Reels tab runs in `InstagramMainActivity`. That's a second origin signal for Phase 1.
  - A post shared in a DM opens in the reels viewer, not a separate post screen.
  - A reel from Explore has an inline comment field, so the comments rule requires the bottom sheet.
  - The stories tray is `reels_tray_container` > unnamed list > one item per bubble.
- **Buttons:** *DMs* works (clicks `direct_tab`). *Story* was fixed after the re-check and still needs a test on the phone.
- **Untested:** a story shared in a DM, notification taps, a post opened from a profile grid (`post_detail` is still a guess), swiping inside a carousel, Instagram Lite.
- **Performance, which changes Phase 1:** reading the whole tree costs about 3–5 ms per node in IPC. Typical screens take 150–600 ms, the DM inbox 1.1–1.6 s. On the main thread this froze the probe for about 5.7 s once. The probe now reads on a worker thread. **Phase 1 must not walk the tree:** convert the confirmed fragments into exact view IDs, look them up with `findAccessibilityNodeInfosByViewId` (plus Android 13's prefetch flags), and stop at the first decisive match. The gate should appear within about 300 ms.

### Risks
| Risk | Likelihood | Mitigation |
|---|---|---|
| An Instagram update breaks screen detection | High (weekly releases) | Signatures as data, unknown-screen alerts, auto-update off, fixture tests |
| Can't tell a DM reel from swiping onward | Medium | Origin tracking + author/caption change detection; Phase 0 tests it first |
| Play Protect warns about a sideloaded app that uses accessibility | Medium | Install via adb; choose "install anyway" if prompted |
| Instagram on another device (PC browser, tablet) | Certain if Petr wants it | Out of scope (listed in Non-goals) |
| Advanced Protection switched on | Low | Revokes Toll instantly and blocks re-enabling. Deliberately allowed: keep it off (setup checklist); Guard shows a notice, never a block, and logs it (Phase 2) |
| Phone stuck in greyscale after a crash | Low | Colours restored on service start |
| Battery drain | Low | Throttled events, direct ID lookups; check battery stats in week 1 |
| Changing the phone's clock to reset the day | Low | Both clocks compared |

### Where Tech left off (2026-10-04, 22:30)
**On the phone:** Toll `0.0.1-probe`, built 22:28:33. The accessibility service is on (gesture capability included, capabilities=33). one sec's accessibility service is also still on. The phone was unplugged afterwards.

**Done in Phase 0:** see "Phase 0 results" above. Short version: the classifier works on Instagram 449, DM reel → swipe → paid works, and 70/70 JVM tests pass (classifier 28, replay 4, rules 36, other 2).

**Installed but not yet verified on the phone:**
1. *Open a story* taps the bubble like a finger (accessibility clicks are refused there). *Open messages* already worked. Retest: feed at the top → Open a story → back → Open messages. The log should show `open first story: Opened first story`.
2. Back-and-retry when a shortcut is pressed on the wrong screen.
3. Android 13 prefetching for whole-tree reads. Compare the `slow tick` lines against the baseline: typically 150–600 ms, DM inbox 1.1–1.6 s.

**Phase 1 next steps (Tech):**
1. **Fast classifier:** turn the confirmed fragments in `signatures.json` into exact view IDs and look them up with `findAccessibilityNodeInfosByViewId`, in rule order, stopping at the first match, with prefetching. No tree walks outside the debug probe. Target: gate within ~300 ms of landing on a paid screen. Measure it.
2. **Session tracker:** turn classifier + `OriginTracker` output into the rules engine's `TollEvent.Screen(at, PAID|FREE|UNKNOWN|OUTSIDE)`. Send OUTSIDE when Instagram leaves the front, plus ScreenOff/ScreenOn from broadcasts. Add `TransparentModalActivity` as a second "opened from a DM" signal. Schedule a Tick at `decision.nextChangeAt` instead of polling.
3. **Persistence:** store the engine's events (Room) and settings and pending changes (DataStore); rebuild state with `engine.replay` on service start.
4. **Enforcement UI:** gate, corner timer, "Still here?", moving-dot hold, typing activity, quick-pass launch (`<queries>` is already in the manifest). Ownership as in Phase 0: petr-6e writes the UI, petr-c6 wires it to the service. The gate's free shortcuts reuse `Navigator` (click, then gesture-tap fallback; hide the gate during the tap).
5. Keep the probe (label, dumps, walkthrough) as a debug-only mode for re-checking after Instagram updates.

**Working notes:**
- Build and install from WSL with `cmd.exe /c build.cmd <task>` (see README). Only petr-c6 runs Gradle and adb.
- Raw dumps are in `toll/dumps/` (git-ignored; they contain short usernames). Delete them once no longer needed. Scrubbed fixtures live in `app/src/test/resources/fixtures/`.
- There's no version control yet. Suggest asking Petr whether to `git init` and make a first commit before Phase 1.
- File ownership: petr-6e has `rules/`, `MainActivity.kt`, `probe/ProbeOverlay.kt`, `ui/`, `res/font/`, `res/drawable/` and `res/mipmap-anydpi/` (icon). petr-c6 has everything else, plus Gradle and adb.

### What Tech still needs
From Product: the corner-timer answer (always visible on paid screens, or only from 50%). It changes only `Decision.showTimer`, not the architecture.
