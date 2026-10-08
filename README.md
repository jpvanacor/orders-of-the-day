# Orders of the Day

Time-block app for João's weekly routine: what to do right now with a countdown and checklist, today's timeline, and this month's recurring chores.

- Web app: https://jpvanacor.github.io/orders-of-the-day/
- Android app (notifications at the start of every block): https://github.com/jpvanacor/orders-of-the-day/releases/latest/download/orders-of-the-day.apk

## How versions work

`index.html` is the whole app. GitHub Pages publishes it about a minute after each push, and the Android workflow builds a new APK from the same file and attaches it to a release named after `APP_VERSION`. Raising `APP_VERSION` makes the Android app show a "new version" bar with a download button. Every earlier version stays in the commit and release history.

The Android signing key (`android/app/orders-release.p12`) is kept in the repository so each build can update the previous install. Only install APKs from this repository's releases.
