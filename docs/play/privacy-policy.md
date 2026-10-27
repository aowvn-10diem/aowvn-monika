# Privacy Policy — Aow Monika

> **Draft, pending owner approval.** Items marked [UNVERIFIED] or [owner to decide] must be resolved before publishing.
> Effective date: [owner to decide]

Aow Monika is an Android app from the aow.vn community. This page explains what data the app handles, why, and what you can control.

## 1. What data is handled

**Stays on your device and is not sent anywhere:** your game library, save files and backups, settings, and reading history. Game files you pick are read on the device only.

**Automatic error reports.** When a game core or the app crashes, freezes, shows no picture, or runs out of memory, the app sends a report to AowVN's server so the errors can be fixed. A report contains:

- app version, time, and error type;
- device information: manufacturer, model, Android version, CPU architecture, and hardware;
- device state at the time of the error: memory, free storage, and network status [UNVERIFIED: whether battery level is included];
- recent technical logs and the trail of actions before the error;
- the name of the game running, if the error happened during play.

Before sending, the app replaces private file paths, tokens, secret keys, and email addresses. **Automatic error reports are on by default.** You can turn them off in Settings under "Tự gửi báo lỗi game" (automatic game error reports). [owner to decide: whether the default stays on in the Play build]

**Error reports you send.** When you report an error for a game, you can write a description and attach a screenshot. Nothing is sent until you press send.

**Google account (optional).** If you sign in with Google through aow.vn, the app stores on the device: a user ID (uid), your name, email, profile photo, and sign-in tokens. Tokens are kept in the app's private storage. [UNVERIFIED: which features need sign-in, and what data goes to Firebase when you use them]

**On-screen translation (optional).** You choose the translation provider and enter that provider's API key yourself. Text recognised on your screen is sent directly to the provider you chose, not through AowVN's server. If you turn on direct image reading (AI vision) and choose an AI provider or endpoint you configure (which may include Gemini), the current screen frame, as a JPEG image, is sent to that provider to be read and translated. [owner to decide: how this is declared under Play's data rules]

**Configuration download.** The app downloads a configuration file (system list, links, notice settings) from AowVN's server. That request contains no personal information.

**Reading posts and forum content.** The app reads content from aow.vn. As with any internet connection, the server receives your IP address and the app's name and version.

## 2. What the app does not do

- No advertising, and no third-party analytics or tracking code in the app.
- No access to your location, contacts, camera, or microphone.
- We do not sell data.
- Based on our code review at the time of writing, the app does not upload your game files, ROMs, or save files to AowVN's server.

## 3. Permissions the app uses

This table applies to the GitHub build. The Play build may differ; see the note at the end.

| Permission | Purpose |
|---|---|
| INTERNET | Read posts, download configuration, and download games and engine packages you choose |
| POST_NOTIFICATIONS | Notify you of new posts |
| FOREGROUND_SERVICE, FOREGROUND_SERVICE_DATA_SYNC | Download and extract games in the background |
| QUERY_ALL_PACKAGES | Recognise installed apps (external emulators) so they open correctly |
| MANAGE_EXTERNAL_STORAGE, READ/WRITE_EXTERNAL_STORAGE | Read game files you pick and write save files |
| REQUEST_INSTALL_PACKAGES | Install an APK you choose (the Play build will remove this) |
| VIBRATE | Haptic feedback when you tap |

The Play build will remove the restricted permissions and APK installation. This list will be updated when the Play build is complete. [pending V150]

## 4. How long data is kept

- Data on your device: deleted when you uninstall the app or clear its data in Android settings.
- Error reports already sent to AowVN's server: [owner to decide: retention period].

## 5. Your choices

- Turn off automatic error reports in Settings.
- If you do not sign in, the app holds no account data on the device. [UNVERIFIED: whether a sign-out option clears tokens on the device]
- To request deletion of error reports you sent: [owner to decide: contact channel and verification].

## 6. Children

The app is not directed at children under 13. [owner to decide: confirm against Play policy]

## 7. Changes to this policy

When features change, we update this page and set a new effective date.

## 8. Contact

[UNVERIFIED: email or official contact channel chosen by the owner]. Public source code: https://github.com/aowvn-10diem/aowvn-monika
