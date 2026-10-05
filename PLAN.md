# Toll — plan

Status (end of 2026-10-05): Phase 1, earn time, and the 24 h turn-off are built, tested (124 tests) and installed on Petr's Pixel. Toll is NOT started. Next (2026-10-06): Tech writes the guard rules from Petr's capture of the 4 Android pages, then Petr taps Start and does a 1-minute guard check (Settings → Accessibility → Toll should show "Toll is protected"). Phase 2 after that: greyscale, barcode pass, Instagram Lite.
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
2. **You pay by acting, not by waiting:** typing, holding a moving dot, walking to a barcode in another room. Passive waits failed with one sec.
3. **The pain continues inside the app,** not only at the entrance.
4. **Start near current usage and tighten gradually.** A limit far below today's habit gets the app uninstalled.

### Rules (week-1 limits and quick pass count approved by Petr; the other numbers are product defaults Petr can change)
- **The rule (Petr, 2026-10-05): places you go on purpose are free; anything that feeds you an endless stream costs.**
- **Free areas** (don't count toward the limit): the message inbox, chat threads, a single post or reel opened from a chat, stories, **Petr's own profile**, **Saved** (the grid and any single saved item), **other people's profiles** (the profile page and a single post opened from it), and **search results for a typed name**. Message notifications open straight into the chat for free.
- **Time on Toll's own screens** (gate, challenges, "Still here?") is never paid.
- **Screens Toll can't recognise** are counted as paid but never gated, so a misread never blocks Petr.
- **Paid areas:** the home feed, the Reels tab, the Explore grid of suggestions, and **swiping onward from any single item** (a reel or post from a chat, a saved item, a post from a profile) into the next one.
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
  | > 100% | Each 10-min pass costs scanning Petr's chosen barcode (an item kept in another room; see "Barcode" below) **plus** holding a thumb on a slowly moving dot. The hold doubles with each pass that day, **capped at 30 min**: 1, 2, 4, 8, 16, 30, 30… The cap keeps the "never a hard lock" promise, since uncapped doubling reaches 64 and 128 min by pass 7–8. |

- **Visit:** one continuous stay in Instagram. Leaving for less than 30 s (a notification glance, a quick app switch) doesn't end it; leaving for longer or turning the screen off does. Moving between free and paid screens inside one visit doesn't start a new visit.
- **When you pay:** the toll is charged at the first paid screen of a visit. Going feed → DMs → feed in the same visit doesn't charge again.
- **A paid 10-min pass** (over the limit) covers 10 minutes of *paid* time within the same visit. Time in DMs or stories doesn't use it up. Leaving Instagram ends it, so passes can't be saved up.
- **Moving-dot hold:** lifting the thumb or losing the dot pauses the countdown. Being off the dot for more than 10 s resets it to the start. Brief slips are forgiven, but you can't put the phone down.
- **Reflex penalty:** a visit that starts within 10 min of the end of the previous visit with paid time is charged one tier higher. Over the limit, it counts as an extra doubling (still capped at 30 min). Moving between DMs and the feed inside Instagram is never a reflex open.
- **What counts as an "open":** the start of a visit that reaches a paid screen.
- **Crossing a tier mid-visit:** crossing 75% turns on greyscale and "Still here?" immediately. Crossing 100% brings up the gate immediately. Crossing 50% changes nothing until the next visit, because typing is an entry price.
- **From 75% up, the effects stack:** greyscale covers all of Instagram, chats included (it costs nothing, and switching it on and off between screens would flicker). "Still here?" keeps going over the limit too. Paying any toll restarts the 5-minute "Still here?" count.
- **Reflex prices by tier:** under 50% → the short sentence; 50–75% → the longer sentence; 75–100% → barcode + a hold at the next pass's length; over the limit → the hold gets one extra doubling.
- **Quick pass** (defaults proposed, Petr can adjust):
  - Started only from a button inside the Toll app, not from the Gate. Having to open Toll on purpose keeps it from becoming the new reflex route.
  - Opens Instagram immediately with no challenge, for **3 minutes of clock time from launch** (leaving and coming back doesn't stretch it), with a countdown in the corner. The countdown turns red for the last 30 s.
  - **3 per day** (reset at 04:00). Available at every tier, including over the limit.
  - During a pass, the gate, challenges, "Still here?" **and greyscale** are all off. The pass is for showing people things, which should look normal.
  - When the pass runs out mid-scroll, the gate appears straight away at the normal price.
  - Minutes used still count toward today's paid minutes, but quick-pass visits are ignored entirely for the reflex penalty: never reflex themselves, and they don't make the next visit reflex.
  - Raising the number or length of quick passes is a loosening change (24 h delay).
- **Commitment:** any change that loosens the rules (higher limits, slower taper, turning tiers off) takes effect after 24 h. Tightening applies immediately.
- **Turning Toll off** (Petr's decision 2026-10-05: **24 h**, not 48 h; built now, ahead of the rest of Phase 2):
  - Petr taps **Turn off Toll** in Settings. For the next 24 h everything keeps working, and the home screen shows when it will switch off, with **Cancel**.
  - After 24 h, Toll stops charging and counting and **stays off** until Petr taps **Turn Toll back on**. That works immediately and continues the same plan: same start date, the weekly cut keeps following the calendar.
  - While Toll is on (including during the 24 h), it protects itself: opening Toll's page in Accessibility settings, its App info page or the uninstall dialog shows a notice ("Toll is protected…") and goes back. The notice offers **Open Toll** to make the request there.
  - Advanced Protection's page shows its own notice with a plain **Continue** (never blocked).
  - Before the first Start and after a turn-off, nothing is protected.
- **Advanced Protection (Android 17)** switches Toll off at once. Toll **never blocks it**, because it's a security feature and Petr must always be able to make the phone safer. When Petr opens that page, Toll shows a one-screen notice ("Turning this on switches Toll off. To pause Toll instead, request a turn-off.") with a plain **Continue** button and no challenge, and logs the event in Toll's history.
- **Escape routes covered:** Instagram Lite gets the same toll. Browsers are not covered (Petr's decision).

### Barcode instead of a QR code (Petr's decision, 2026-10-05)
Petr has no printer, so there's no QR code to stick up. Instead:
- **Enrol once:** Petr picks an everyday item that lives in another room (shampoo in the bathroom, a cereal box in the kitchen) and scans its barcode in Toll. Toll stores that barcode's value.
- **Over the limit,** each pass means walking to that item and scanning the same barcode, then the hold.
- Works offline, needs nothing printed or bought. Changing the enrolled item counts as loosening (24 h).
- Known, accepted bypass: moving the item next to the bed, or a photo of the barcode. Like the QR plan, it's friction, not a lock.
- Alternatives considered: walking N steps (no setup, easier to fake), an NFC sticker (has to be bought).

### Earn time (Petr's idea and decision, 2026-10-05)
Optional tasks that buy extra minutes, as a healthy alternative to paying tolls.
- **Each task adds +10 min to today's limit**, which moves the whole ladder up (typing, grey and holds all start later). It works at every tier, including over the limit.
- **Cap: +30 min a day.** Raising the cap or the reward is a loosening change (24 h rule).
- **Tasks:**
  - **Push-ups:** 20, counted with the proximity sensor while the phone lies on the floor under Petr's face. A camera-based count can replace it later if the sensor proves too easy to fake.
  - **Duolingo:** about 5 minutes with Duolingo in front and the screen on, measured by the service. Detecting a finished lesson can come later.
  - **Walking:** 1,000 steps on the step counter (needs the activity-recognition permission).
- Earned minutes are logged as their own event, so the home screen and history can show them ("+20 min earned").
- **Timing:** Phase 1.5, right after the core works on the phone.

### Screens
1. **Onboarding:** what Toll does → grant permissions → set week-1 limits (defaults 3 h / 5 h) and the floor → pick an item in another room and scan its barcode once (Phase 2).
2. **Gate** (shown over Instagram when it lands on a paid screen): today's paid minutes and open count, what this entry costs, what the next one will cost. Buttons: **Messages (free)**, **Stories (free)**, **Pay toll**, **Leave**. Stories needs its own button because the stories tray sits on top of the home feed, which is paid. Toll opens the first story in the tray for you; leaving the story viewer lands back on the gate.
3. **Toll challenges:** typing a sentence; thumb hold on a moving dot with a countdown; barcode scan.
4. **Timer** (Petr's decision, 2026-10-05): a small, circular, translucent dial in the **top-left** of the screen, on paid and unrecognised screens, **only from 50% of the daily limit**. It shows today's paid time against the limit. It never takes touches. During a quick pass, the quick-pass countdown takes its place.
5. **"Still here?" interrupt.**
6. **Toll home:** a big **Quick pass** button showing how many are left today ("3 left today"), then today (paid minutes, opens, current tier), this week's limit, a week-by-week trend.
7. **Settings:** limits, floor, taper rate; pending loosening changes with their countdown; the "turn Toll off" request and its countdown.

### v1 scope (proposed)
- **Phase 0, feasibility probe:** a tiny app Petr installs that shows whether Toll can reliably tell DM inbox/thread, a reel from a DM, feed, Reels tab, Explore and stories apart on Petr's Instagram version. Everything below depends on this.
- **Phase 1, core:** detect Instagram, classify the screen, count paid minutes and opens, Gate, the tier ladder (typing, "Still here?", moving-dot hold with doubling), reflex penalty, weekly taper, corner timer, Toll home, quick pass, 24 h delay on loosening. Toll stays easy to switch off during Phase 1 so testing isn't painful.
- **Phase 1.5, earn time:** the tasks above, an "Earn time" card on the home screen, and `TimeEarned` in the engine.
- **Phase 2, hardening:** greyscale, barcode challenge, coverage of Instagram Lite. (Self-protection and the 24 h turn-off were pulled forward and built on 2026-10-05.)

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
| Storage | JSON files in app storage: an append-only event log, settings, step progress | On-device, no account; replaying the log rebuilds the engine's state, so there's no database to migrate. |
| Barcode scanning (Phase 2) | CameraX + ZXing (EAN-13/EAN-8/UPC) | Reads the barcode on an everyday item; works offline, no Google Play Services dependency. |
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
Event log + settings  ◄──  Toll app (home, quick pass, settings, earn time, typing + barcode challenges)
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
- **Typing and barcode challenges** run in a Toll activity opened over Instagram. The keyboard and camera work reliably in an activity, and Android lets apps with an active accessibility service open activities from the background.
- **No pasting:** suggestions off, no copy/paste menu, and any single edit that inserts more than one word is rejected. That blocks paste and autofill but still allows swipe typing.
- **Barcode (Phase 2):** during setup Petr scans the barcode of an everyday item kept in another room (shampoo, a cereal box), and Toll stores its value; a product barcode isn't secret, so it isn't hashed. Over the limit, each pass needs walking there and scanning that same item again; any other barcode is rejected. Known, accepted bypasses: a photo of the barcode, or a second copy of the product. No printer needed.
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
As built in Phase 1 (2026-10-05), in the app's private storage:

| File | Contents |
|---|---|
| `events/<toll day>.jsonl` | Append-only log of everything the engine was told: screen kind changes, screen on/off, tolls paid, "Still here?" dismissed, quick passes, time earned. Ticks aren't logged. Replayed with `TollEngine.replay` on every start. |
| `settings.json` | Settings in force (limits, taper, floor, day start, quick passes, earn per task and cap) plus loosening changes waiting out their 24 h |
| `enforcing` | Exists once Petr pressed Start (holds when) |
| `steps.json` | Step counter baseline and today's progress |
| `probe_off` | Test mode switched off |
| `dumps/` | Probe screen dumps (test mode only) |

Today's paid minutes, tier, limit, visits and passes left are always computed by replaying the log, never stored, and the history chart replays each past day's log. Phase 2 adds the enrolled barcode value, the turn-off request time and Petr's original colour settings to `settings.json`. Size: well under 1 MB a year.

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

### Where Tech left off (2026-10-05, 19:20)
**Phase 0 is complete.** The button test passed on 2026-10-05: *Open messages* clicks `direct_tab`; *Open a story* always taps the bubble like a finger, because Instagram sometimes accepts the accessibility click and then does nothing. Back-and-retry works from inside a story.

**Phase 1 core is built and on the phone (not yet started):**
- **Fast classifier:** rules use exact view IDs; live, each is one `findAccessibilityNodeInfosByViewId` (`LiveScreenQuery`); in tests, a `SnapshotQuery` answers the same rules from fixtures. The order is DM thread, story, then bottom tabs (a single `tab_bar` lookup skips them all when the bar is hidden), then the rest. Measured on the phone: median ~0.1 s per screen read, at most ~0.4 s (was 0.16–1.6 s walking the tree). Android 13 prefetching alone barely helped.
- **New free rules (Product, 2026-10-05):** `SEARCH` (account results) is free and split from the paid Explore grid. Profiles are free, and origins like chats: a single item opened from a chat, profile or Saved is free until the item changes. `SAVED` exists, but its rule is still missing (see next steps).
- **Session:** `SessionTracker` (classifier → `ScreenKind`), `TollCore` (settings, Start, engine session, restart replay, history; JVM-tested) and `TollRepository` (thread, ticks, `StateFlow<HomeState>`, quick-pass launch). Files: `files/events/<day>.jsonl` (append-only log, replayed on start), `files/settings.json`, `files/enforcing`. A clock anchored to the monotonic clock prevents skipping days by changing the phone's time.
- **Service:** draws `TollOverlays` from each decision while Instagram is in front. Free shortcuts tap through the gate (`touchThrough`). Screen on/off comes from a receiver. Toll's typing challenge counts as free time inside the visit. Test mode (the Phase 0 panel) is on by default.
- **Nothing is charged until Petr presses "Start week 1 today".** Before that, no events reach the engine.
- 92/92 JVM tests pass; everything is pushed to the private GitHub repo.

**Saved/profiles walkthrough (2026-10-05, 6 dumps still on the phone, not yet pulled):** the log shows:
- Saved screens: UNKNOWN.
- A post from a profile: free; swipe onward: paid.
- A post's item key is just the author, so it only changed because a second post scrolled in. It needs caption/timestamp IDs.
- Scrolling a saved item's list was classified as FEED (paid, right result for the wrong reason).

**Next (Tech):**
1. Pull the 6 dumps. Add Saved rules (collections, grid, saved-posts list as a free origin), a stronger post item key, fixtures and replay tests.
2. First real use after Start: watch gate latency end to end (event → overlay), visits and charging in the log (`adb logcat -s TollProbe TollSession`).
3. Phase 1.5 (Product decided): earn time. Duolingo time in front is measured in the service; push-ups (proximity) and steps (step counter, needs ACTIVITY_RECOGNITION) in a small foreground component; logged as `TimeEarned`.
4. Phase 2 as planned: greyscale (adb grant), barcode pass (enrol + scan), self-protection, Instagram Lite.

**Working notes:** build with `cmd.exe /c build.cmd <task>`; `adb.exe` at `/mnt/c/Users/Petr/AppData/Local/Android/Sdk/platform-tools/`; git in WSL, pushed to a private GitHub repo with `gh` as the credential helper. Each session commits its own files, and only pushes green commits that don't depend on the other's uncommitted files. Raw dumps stay in `toll/dumps/` (git-ignored); fixtures are scrubbed.

**File ownership:** the Product/UI session owns `rules/`, `MainActivity.kt`, `probe/ProbeOverlay.kt`, `ui/` (including `ui/home/` and `ui/overlay/`), `res/font/`, `res/drawable/` and `res/mipmap-anydpi/`. The Tech session owns everything else (Gradle, manifest, `res/xml`, `res/values`, `signatures.json`, `classifier/`, `probe/` except the overlay, `service/`, `session/`, their tests and the fixtures), and runs Gradle and adb. Session names change between runs (petr-c6 → petr-3a for Tech; petr-6e → petr-53 for Product); go by role.

### What Tech still needs
Nothing from Product right now. The Saved dumps come off the phone when it's reconnected.
