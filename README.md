# Morse Trainer

An Android app that emulates a pocket Morse code trainer card — the brass-on-black
dichotomic chart where you start at the aerial, take a circle for a dit and a bar for a
dah, and read off the letter where you land.

The chart is transcribed node for node from the card: all 26 letters, same grid, same
shapes, same axis-aligned traces. It is not a generic Morse tree redrawn — `CardChart.kt`
carries the actual printed coordinates.

## Screens

| Tab | What it does |
| --- | --- |
| **Card** | The chart itself. Tap any pad to hear that letter; the trace from the aerial lights up element by element as it sends. |
| **Key** | Send practice. Tap for a dit, hold for a dah, pause to end a letter. The chart follows your keying live and the decoder prints what you actually sent. |
| **Train** | Listening practice. A letter is sent, you name it. Picks are weighted towards the letters you keep missing. |
| **Send** | Type a message and send it — sidetone, camera flash, and vibration. |
| **Setup** | Speed, Farnsworth spacing, tone pitch and volume, key mode, progress reset. |

## How it works

**Sidetone** (`audio/SideToneEngine.kt`) — one `AudioTrack` stays open for the life of
the app and is fed silence when the key is up. Phase is continuous and the gain is ramped
through a raised cosine over ~5 ms, so elements start and stop softly instead of clicking.

**Timing** (`core/MorseCode.kt`) — PARIS standard, one unit is `1200 ms / wpm`. When
overall speed is set below character speed you get ARRL Farnsworth spacing: characters
stay fast, the gaps between them stretch. Verified against the standard — PARIS at
20 wpm takes exactly 3000 ms.

**Sending** (`audio/MorsePlayer.kt`) — every element is scheduled against a fixed start
instant rather than by sleeping for each gap in turn, so a slow frame does not accumulate
into audible drift across a long message.

**Decoding** (`input/KeyDecoder.kt`) — hold length decides dit versus dah; the silence
after a release decides whether the character ended. With "follow my speed" on, the unit
estimate tracks how you actually key rather than the speed setting, which matters more
than the configured speed for a hesitant fist.

## Building

Nothing in this repo is compiled — it was written without an Android toolchain to hand,
so **it has not been through a compiler yet**. Expect to fix a stray import or two on
first sync.

You need JDK 17 and the Android SDK (API 35). Easiest path:

1. Open the project folder in Android Studio and let it sync — it will fetch Gradle and
   generate the wrapper.
2. Run on a device or emulator, `minSdk 26`.

From the command line, generate the wrapper first (the `gradle-wrapper.jar` binary is not
checked in), then build:

```bash
gradle wrapper --gradle-version 9.5.1 --distribution-type bin && ./gradlew assembleDebug
```

## CI

`.github/workflows/android.yml` mirrors the pipeline from `Normola/bt-intercepter`:
on every push to `main` or `claude/**` and every PR to `main`, it regenerates the Gradle
wrapper, runs unit tests and lint, builds the debug APK, and uploads the APK and lint
report as artifacts. Pushes to `main` additionally cut a GitHub release tagged
`v<versionName>-<run number>`, with a QR code pointing at the *stable* latest-release
URL — screenshot it once and it always resolves to the newest build.

## Notes

- The chart shows the card's 26 letters only. The keyer and sender handle digits and
  punctuation too — send `73` or `SOS?` and it works, it just has no pad to light up.
- Camera flash output needs no permission (`setTorchMode`); vibration uses `VIBRATE`.
- `tools/` holds two Node scripts that guard the parts most likely to be silently wrong.
  They need no toolchain beyond Node:

  ```bash
  node tools/verify_chart.js app/src/main/java/com/normo/morsetrainer/core/CardChart.kt
  ```

  checks every pad against the ITU alphabet and proves the layout still holds — no two
  pads in a cell, every edge axis-aligned, no trace running through an unrelated pad, no
  two traces overlapping, every shape matching its final element. It also prints the grid,
  which should look like the card.

  ```bash
  node tools/verify_timing.js
  ```

  checks dit/gap durations and the Farnsworth formula against PARIS at several speeds.
