# Lances of the Realm

An original medieval strategy game for Android, inspired by the 8-bit home-computer
strategy games of the 1980s. You get a kingdom map, tournaments, castle raids and sieges.

The old king of Valdria is dead. Three rival lords reach for the crown, and you hold the
castle of Highmere. Win land with your army, gold in the jousting lists and glory with
daring raids. Storm all three rival castles to win. If Highmere falls, you lose.

## How to play

Each **season** you may muster troops freely, then perform **one great deed**:

| Deed | What happens |
| --- | --- |
| **March on** | Attack a territory on your border. You fight the battle round by round, choosing **Charge**, **Hold**, **Flank** or **Retreat**. Hold beats Charge, Charge beats Flank, and Flank beats Hold. |
| **Besiege** | Marching on a rival's home castle starts a siege. Fire your catapults (two stones each): drag back and release. Knock stones out of the wall to weaken its defence, or lob shots into the bailey to thin the garrison. Then storm it. |
| **Raid castle** | Sneak into any rival castle and fight its guards one after another. Tap the left half of the screen to block and the right half to strike. Tap high or low to match the guard's attack, which is telegraphed with a `!`. Win and you steal half their treasury. |
| **Hold a tournament** | Joust against a rival's champion for a purse or for land. Drag to steer your lance onto their shield as they gallop in. Hitting the gold boss unhorses them. |

Then **End season**: rivals recruit and campaign, taxes come in, and your troops are paid.
Your field army rides home to help defend Highmere if a rival besieges it.

Your progress is saved automatically.

## Heroes

| Hero | Leadership | Jousting | Swordplay |
| --- | --- | --- | --- |
| Aldric Ashford | 5 | 2 | 2 |
| Maren of Highcliff | 2 | 5 | 2 |
| Corwin Greymantle | 2 | 2 | 5 |
| Edda Thornwood | 3 | 3 | 3 |

## Project layout

```
core/   Pure Kotlin game logic: map, campaign rules, rival AI, battle, joust, duel and siege
        simulations, save format. Unit-tested on the JVM.
app/    Android app: a single Activity and a custom View that draws every screen with Canvas
        at a virtual 480x270 resolution, using a 16-colour retro palette. There are no
        bitmap assets and no AndroidX dependencies.
```

## Building

You need JDK 17+ and the Android SDK (API 35).

```bash
./gradlew :core:test          # run the game-logic tests
./gradlew :app:assembleDebug  # build app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:installDebug   # install on a connected device
```

Every push runs the **Build** GitHub Actions workflow. It runs the tests, builds the debug
APK and uploads it as the `lances-of-the-realm-debug-apk` artifact, so you can download
it from the workflow run and sideload it.
