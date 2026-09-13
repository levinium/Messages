# Levinium Messages

**An unofficial fork of [Fossify Messages](https://github.com/FossifyOrg/Messages) with two notification and scrolling fixes.**

This is not a Fossify product and is not affiliated with, endorsed by, or supported by the Fossify
project. Please do not report problems with this fork to them — open an issue
[here](https://github.com/levinium/Messages/issues) instead.

It installs as its own app (`org.fossify.messages.levinium`), so it sits alongside Fossify Messages
rather than replacing it. Only one app can be your default SMS app at a time.

## Why this fork exists

Two things in Fossify Messages annoyed me enough to fix. Both are reported upstream, and if they are
fixed there this fork stops having a reason to exist:

- [FossifyOrg/Messages#877](https://github.com/FossifyOrg/Messages/issues/877) — notifications for
  messages you are already reading
- [FossifyOrg/Messages#878](https://github.com/FossifyOrg/Messages/issues/878) — new messages not
  scrolled into view

## What's different from upstream

### Notifications are skipped for messages already on screen

Upstream posts a notification even when the message arrives in the conversation you currently have
open, so an active back-and-forth leaves a notification to dismiss for every reply.

`ThreadActivity` already tried to cancel that notification while visible, but the cancel runs from an
async event while the receiver posts the notification immediately afterwards — so it usually fires
before the notification exists and does nothing.

This fork skips posting it at the source instead:

- A conversation only counts as showing the message when it is **scrolled to its end**. Scrolled up,
  you still get a notification, because the message lands below the fold where it is easy to miss.
- Scrolling down to a message that raised a notification **dismisses that notification** and marks it
  read.
- The alert the notification would have played is **played in its place**, so nothing arrives
  silently. It goes through the conversation's own notification channel and honors Do Not Disturb and
  the ringer mode — a muted conversation or a silenced phone stays quiet.
- Messages shown this way are marked as read, so a conversation stops being listed as unread while
  you are reading it.

The same skipping is available for the conversations list, where the incoming message is already
visible as it arrives.

All of it is optional, under **Settings → Notifications**, defaulting to the behavior above:

- Skip notifications for the open conversation
- Skip notifications on the conversations list
- Still play a sound or vibrate for skipped notifications

Turning the first one off restores upstream behavior for an open conversation completely.

### New messages scroll into view

Receiving a message while sitting at the bottom of a conversation often left it half cut off below
the fold, needing a manual scroll to read what had just arrived.

Two bugs sat in the same block. The "is the user at the bottom" test required the last item to be
exactly one position below the last visible one — but when you really are at the bottom that
difference is zero, so the check failed in exactly the case it existed for. And when it did fire, it
scrolled to the last index of the *previous* list, which is the message before the one that arrived.

## Install

Grab the APK from [Releases](https://github.com/levinium/Messages/releases) and install it. You will
need to allow installing from your browser or file manager.

To actually receive messages, set it as your default SMS app: **Settings → Apps → Default apps → SMS
app**. Your message history lives in Android's system message store, not in the app, so switching
between messaging apps does not move or lose your texts.

Releases are signed with a personal key. Android will not install an update signed with a different
key over an existing install, so updates must come from this repo.

## Building it yourself

Requires JDK 17+ and the Android SDK (compileSdk 36).

```sh
./gradlew assembleFossDebug      # debug build, installs as org.fossify.messages.levinium.debug
./gradlew assembleFossRelease    # release build; needs keystore.properties, see app/build.gradle.kts
```

## License

GPL-3.0, the same license as upstream — see [LICENSE](LICENSE).

This is a modified version of Fossify Messages. The original work is copyright the Fossify project
and its contributors; modifications described above are copyright their respective authors. The
Fossify name, logo and branding belong to the Fossify project and are not used by this fork.
