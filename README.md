# WearOS Bridges

**Island Bridges** for **Wear OS** watches, in Kotlin and Jetpack Compose.

Bridges - Hashiwokakero - is a Japanese logic puzzle. The board is a scatter of
numbered islands; the number on each says how many bridges must end there. Bridges
run straight, horizontally or vertically, at most two between any pair, and never
across each other or over an island. Finish every island's count, leave the whole
archipelago joined into one piece, and the board is solved. Every board has exactly
one answer, and none of them ever needs a guess.

Everything runs on the watch: no phone, no network, no account.

This is a port of
[AmazfitBridges](https://github.com/dchernykh1984/AmazfitBridges), the same game as
a Zepp OS mini app. The **1,671 shipped boards are the same files, byte for byte**,
and the rules, the layout proportions and the eleven translations are carried over
unchanged; the implementation is new.

## Playing it

- **Tap an island** to pick it up. The lanes it can still build along light up, and
  a second tap on one of its neighbours puts a bridge in - tap the same pair again
  for a double, and once more to take it away. Tapping the island itself puts it
  back down.
- **Tap a lane** to do the same thing without picking anything up first: a bridge
  already on the board is one tap from becoming a double and two from being gone.
  Islands win a tap that could be either, because an island is the smaller target
  and the one a finger was aiming at.
- **Drag** to move the board around. A cell is a fixed, fingertip-sized square on
  every size, so 7x7 fits the screen whole while the larger boards are bigger than
  it and are dragged into view. A drag never builds a bridge, and a tap that wobbles
  a little still counts as a tap.
- **Undo** takes back the last bridge; **Menu** pauses, which stops the clock -
  thinking time counts, staring at the pause menu does not.
- **7x7 / 9x9 / 11x11 / 13x13** picks the size. They are named by size rather than
  by difficulty because size is honestly what they differ in: an audit of the
  shipped collection found a 13x13 needs exactly the same reasoning as a 7x7, only
  more of it.
- **Built-in / Random** picks where a board comes from. The collection is dealt
  without repeating a board until the whole pool has been played; **Random** builds
  a fresh one on the wrist instead.
- A best time and a solved count are kept **per size and per source**, because
  working through a collection that never repeats itself is a different thing from
  rolling a new board every time.
- **Languages** - English, Russian, German, French, Italian, Spanish, Portuguese,
  Dutch, Polish, Czech and Kazakh. The watch's own language is followed, and all
  eleven are offered individually in the system per-app language list - so Kazakh,
  which Zepp OS had no device-language code for and could never select, finally
  reaches the people it was translated for.

## The boards

The collection ships as four plain-text files in `wear/src/main/assets/boards/`,
copied unchanged from the Zepp OS app - 171 boards at 7x7, 700 at 9x9, 500 at
11x11 and 300 at 13x13, 190KB in all. A test decodes every one of them, checks it
round-trips back to the same text and that its islands and lanes are structurally
sound; another runs the solver over them to confirm each has exactly one answer
reachable without guessing.

**Random** builds a board on the watch instead. It grows a layout one island at a
time, least-connected first so the board spreads out rather than clustering, then
puts each candidate through the same solver twice - once to prove the answer is
unique, once to prove it can be reached by deduction alone - and keeps the first of
up to fourteen candidates that passes both. That is a search, so it runs on a
background dispatcher with the screen saying **Building...**; a pause on the main
thread would be a frozen screen rather than a thinking one.

Islands are only ever placed inside the disc inscribed in the square grid. The grid
is square and the watch is round, so an island in a grid corner is drawn into the
bezel - on the smallest board it was sliced in half before the player had touched
anything. A disc is convex, so no bridge between two cells inside it ever leaves it,
and bridges need no rule of their own.

## Devices

Round watches, **Wear OS 3 (API 30) and newer**. Built and tested against a
**OnePlus Watch 2R** (466x466 round, Wear OS 5).

## Setup

```bash
git clone https://github.com/dchernykh1984/WearOSBridges.git
cd WearOSBridges
```

A JDK 17 and the Android SDK (compileSdk 36) are all that is needed; Gradle comes
with the repository through the wrapper. Point the build at your SDK with a
`local.properties` holding `sdk.dir=/path/to/Android/sdk`, or export `ANDROID_HOME`.

## Develop

```bash
./gradlew testDebugUnitTest   # the JVM unit tests
./gradlew koverVerify         # unit tests + the coverage floor
./gradlew ktlintCheck         # formatting
./gradlew detekt              # static analysis
./gradlew lintDebug           # Android Lint, including the Wear OS checks
./gradlew assembleDebug       # build the APK
./gradlew connectedDebugAndroidTest   # instrumented tests (needs a watch or emulator)
./gradlew installDebug        # install on a watch over ADB
```

The whole pull-request gate in one line, which is exactly what CI runs:

```bash
./gradlew ktlintCheck detekt lintDebug testDebugUnitTest koverVerify assembleDebug assembleRelease
```

### Layout of the code

```
wear/
  src/main/AndroidManifest.xml         watch-only, standalone, no permissions
  src/main/assets/boards/              the shipped collection, as it left Zepp OS
  src/main/java/com/dchernykh/bridges/
    MainActivity.kt                    the single activity
    BridgesViewModel.kt                the state the screen draws
    game/Puzzle.kt                     the rules: islands, lanes and what may be built
    game/BoardFormat.kt                the text the collection is stored as
    game/Solver.kt                     one answer, and no guessing needed
    game/Generator.kt                  a board built on the wrist
    game/Grid.kt                       the layout while it is being grown
    game/Playfield.kt                  which cells of a square grid a round watch has
    game/Collection.kt                 dealing without repeating a board
    game/Progress.kt                   the clock and the record decision
    game/Level.kt                      the four sizes and the two sources
    game/Mulberry32.kt                 the Zepp OS generator's RNG, digit for digit
    layout/BoardGeometry.kt            where the board sits, and what a tap hit
    layout/Camera.kt                   how far the map may be dragged
    layout/RoundGeometry.kt            chord maths that keeps content off the bezel
    store/BoardSource.kt               the collection, read from the APK's assets
    store/SettingsStore.kt             progress, on Preferences DataStore
    ui/                                the Compose screens
  src/main/res/values*/strings.xml     the screen strings, a table per language
  src/test/                            JVM unit tests, the solver included
  src/androidTest/                     instrumented tests - what needs a device
tools/make-launcher-icons.sh           regenerates the icon from the Zepp OS one
config/detekt/detekt.yml               static-analysis overrides
gradle/libs.versions.toml              every dependency and plugin version
```

The rule that shapes it: anything a test can reach without a device - the rules,
the solver, the generator, the board format, the geometry, the camera - is a plain
Kotlin class outside the Compose layer, and `koverVerify` holds it to a floor of 80
(the suite sits at 99). Only what genuinely needs a device is exempt, and each
exemption is written down where it is made, with the instrumented test that covers
it instead.

## Pre-commit hooks (contributors)

```bash
uv tool install pre-commit   # or: pipx install pre-commit
pre-commit install
pre-commit install --hook-type commit-msg --hook-type pre-push
```

On commit: whitespace and line endings, YAML/TOML/XML well-formedness, a non-ASCII
guard on source and config (translations in `res/values-*/` are exempt - that is
what they are for), and a check that apostrophes in string resources are escaped,
which is an aapt2 error rather than a warning. On the commit message: Conventional
Commits. On push: ktlint, detekt and the unit tests.

## Continuous integration and releases

Every pull request must pass: pre-commit, `actionlint`, commitizen, the Gradle gate
above, a CodeQL analysis, an OSV dependency scan and the instrumented tests on two
Wear OS emulators.

Releases are automated with `release-please`: it maintains a version-bump PR from
the Conventional Commits and, when merged, tags a GitHub Release. The release build
then produces a **signed APK**, verifies its signature, records a build-provenance
attestation and attaches the APK and its R8 mapping file to the release.

Verify a published APK came from this repository:

```bash
gh attestation verify wearos-bridges-<version>.apk --repo dchernykh1984/WearOSBridges
```

### Dependency locking

`wear/gradle.lockfile` pins every transitive version. After changing a dependency,
regenerate it with the **Update lockfiles** workflow (or
`./gradlew :wear:dependencies --write-locks`) and commit the result.

## License

Released under the [MIT License](LICENSE).
