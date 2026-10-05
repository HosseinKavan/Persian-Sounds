# صدای واژه — Persian First/Last Sound Learning App

A simple Android app for first-grade Persian learners. It presents 50 flash-card-style words, reads each Persian word aloud, asks the child to say the first sound and then the last sound, checks the speech-recognition result, and keeps per-word practice statistics locally.

## Included in v1

- 50 words imported from `source/persian_1st_grade_words.xlsx`.
- Kid-friendly pictogram/emoji illustration for every word.
- Persian Text-to-Speech (`fa-IR`) with automatic reading when a card opens and a repeat button.
- Microphone button for first-sound and last-sound practice.
- On-device speech recognition is preferred on supported Android versions/devices; otherwise the system recognizer is used.
- Forgiving Persian sound/letter matching (for example `د`, `دال`, and common recognizer variants).
- Per-word statistics: attempts, correct, wrong, first-sound results, last-sound results, and accuracy.
- Stats are stored only on the phone with `SharedPreferences`.
- The app itself does **not** save audio recordings.

## Important limitation of v1 speech checking

Android speech recognition is designed mainly for words and phrases, not isolated phonemes. A child saying only a very short sound such as /د/ may sometimes be transcribed as a letter name or a similar short Persian word. `SoundMatcher.kt` therefore uses a small alias table to be tolerant. For a production-grade school product, the next technical step would be a dedicated on-device phoneme classifier trained on children's Persian speech.

## GitHub Actions APK

The workflow `.github/workflows/build-apk.yml` builds and uploads `SedayeVazheh-v1-debug.apk` on every push to `main`.