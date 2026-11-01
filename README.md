# Value Forge Companion

An Android workspace for the [Value Forge Decky plugin](https://github.com/timekillr/value-forge-decky).
Run searches and manage your saved memory groups from your phone while the Decky
panel stays closed. The Steam Machine performs all memory reads and writes.

## First connection

1. Update Value Forge to **0.7.0 or newer** using https://is.gd/valueforge.
2. Open the plugin in Decky, enable **Phone companion**, and choose **Pair a phone**.
3. Install the signed **ValueForgeCompanion.apk** from this repository's Releases.
4. Keep your phone and Steam Machine on the same home network. Allow Android's
   local-network permission when requested by the app.
5. Enter the machine address and six-digit code shown in Decky. Compare the
   displayed fingerprint and choose **Match & pair** only when it matches.

Pairing is remembered across app, plugin, and machine restarts. The app reports
connection status and last response time, marks disconnected values stale, and
disables writes until connected. Change the machine address in
Connection if its IP changes; the saved identity and pairing are retained.

## 0.3.0 connection controls

The **Connect / Disconnect** button is visible above the game session on every
tab. Opening the app with a remembered pairing starts a connection attempt.
Automatic attempts last at most **10 seconds**, then stop until you press
**Connect**. A lost connection gets the same bounded retry window. Successful
connections continue normal live value/progress polling.

**Disconnect** immediately stops polling and cancels queued/in-flight connection
checks. It keeps pairing and cached groups, marks readings stale, and disables
memory edits. Returning from the background does not undo a manual disconnect
or restart an expired retry window. Connect explicitly starts a fresh window.
Late replies cannot reconnect the UI after it has stopped. Commands already sent
to the Steam Machine may finish; disconnect does not undo edits or stop freezes.
No Decky plugin update is required; continue using 0.7.0 or newer.

## 0.2.0 quick edits

Every search result has **Quick edit** immediately above **Save to group**.
Enter the new value and press **Apply quick edit** to write without saving the
address. Select several result checkboxes and use **Quick edit selected** to
apply one value to all of them. Each row uses its chosen **Result type**, including
ambiguous Auto results. The dialog reports successes and per-address failures.

The plugin rereads search and saved values after applying an edit. Matching
saved freeze targets adopt the new read-back value. Quick edits never create
saved entries or advance the scanner's last-search comparison baseline. They
require plugin 0.7.0 and are blocked if the connection, game session or search
changes. Install over the existing app to keep pairing; no re-pair is needed.

## 0.1.1 layout fix

The native container now sizes the entire HTML viewport inside Android's system
bar, display-cutout, and keyboard insets. The header stays below the status bar,
including on Android versions that enforce edge-to-edge rendering. Install the
new signed APK over 0.1.0 to keep the remembered pairing.

## Included functionality

- Complete process list, filtering, and one-tap connection.
- Auto and signed/unsigned 8-, 16-, 32-, 64-bit integers, float32 and float64.
- All comparisons: equals/not-equal, greater/less, changed/unchanged,
  increased/decreased, exact increased/decreased by X, and unknown initial value.
- Live scan progress while a search is running, with existing results retained.
- Result selection, concrete-type selection for ambiguous Auto matches, and
  saving single or multiple results into new or existing named groups.
- Multiple addresses per group, manual address entry, group rename/deletion,
  independent address labels/values, moves, removal, and freeze/unfreeze.
- Change every address in a group in one action, with per-address failure reports.
- Live saved-value refresh, shared session state with Decky, and reconnect.
- Normal phone text/number input and sign toggles; numeric values remain strings
  so large integers retain full precision.

The app displays the plugin's first 200 matches; all matches remain available to
narrow in the scanner. Saved addresses remain session-bound, just as in Decky.
One game session is active at a time. There is no cloud service, account, phone
root requirement, or router setup. The phone does not scan its own apps' memory.

## Connection details

The plugin's optional HTTPS listener uses port 52881. Pairing codes expire after
five minutes. The app pins the verified certificate and encrypts credentials
with Android Keystore. No credentials are exposed to its packaged UI scripts.
Removing a phone in Decky revokes access. App/network reconnects only read state;
they never automatically replay writes, scans, or freeze actions. Automatic
reconnection stops after 10 seconds and can be resumed with Connect.

The touch interface is packaged locally in the APK. A restricted WebView renders
it; native Java code handles TLS, certificate pinning, local-network permission,
credential storage, and lifecycle. External navigation and cleartext HTTP are
disabled. No page is downloaded from the Internet to render the UI.

See the plugin's [protocol documentation](https://github.com/timekillr/value-forge-decky/blob/main/COMPANION_PROTOCOL.md).

## Build and verification

Requires JDK 17, Android SDK platform **37.0**, build-tools **36.0.0**, and Node 22+.
Gradle 9.4.1 and Android Gradle plugin 9.2.1 are pinned.

```sh
npm ci
npm test
./gradlew :app:testDebugUnitTest :app:assembleRelease
```

Set `ANDROID_HOME` to your SDK, or add a git-ignored `local.properties` with its
`sdk.dir`. The release build produces `app/build/outputs/apk/release/app-release-unsigned.apk`.
The maintainer signs published APKs with the same private release key so normal
updates keep the app's remembered pairing. Signing keys/passwords are excluded
from Git. CI builds and tests an unsigned APK; the signed APK is attached to the
GitHub release separately. Do not install an unsigned build.

Automated checks exercise the touch UI with simulated native callbacks, native
address parsing, and the real plugin HTTPS API against an opt-in Linux test
process. They cover live progress, groups and edits, exact numeric strings,
offline recovery, token revocation, persistent identity, and stale-session guards.
Physical Android/SteamOS operation still needs on-device verification.

## Credits and license

Created by timekillr for Value Forge. Value Forge is built on
[Memory Deck](https://github.com/CameronRedmore/memory-deck) by Camzie99
(Cameron Redmore) and contributors, using [scanmem](https://github.com/scanmem/scanmem).
This repository contains the companion client; scanner source/notices remain in
the Decky repository. GPL-3.0-only; see LICENSE and THIRD_PARTY.md.
