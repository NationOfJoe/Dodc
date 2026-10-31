# Lances of the Realm

An original medieval strategy game for Android, inspired by the 8-bit home-computer
strategy games of the 1980s. You get a kingdom map, tournaments, castle raids and sieges.

The old king of Valdria is dead. Three rival lords reach for the crown, and you hold the
castle of Highmere. Win land with your army, gold in the jousting lists and glory with
daring raids. Storm all three rival castles to win. If Highmere falls, you lose.

## How to play

Your field army is the blue banner on the map. Each **season** it can **move** two steps through
your own lands. You can also muster troops; recruits gather at Highmere and join the army only
while it is home. Then you perform **one great deed**:

| Deed | What happens |
| --- | --- |
| **March on** | Attack a territory next to your army; if you win, the army advances into it. You fight the battle round by round, choosing **Charge**, **Hold**, **Flank** or **Retreat**. Hold beats Charge, Charge beats Flank, and Flank beats Hold. |
| **Besiege** | Marching on a rival's home castle starts a siege. Fire your catapults (two stones each): drag back and release. Batter the wall to weaken its defence, or lob shots into the bailey to thin the garrison. Then storm it. The more land a rival holds, the stouter their walls and the bigger their garrison. |
| **Raid castle** | Sneak into any rival castle and fight room by room: the gatehouse, the great hall, then the treasury. Tap the left half of the screen to block and the right half to strike. Tap high or low to match the guard's attack, which is telegraphed with a `!`. Win and you steal half their treasury. |
| **Hold a tournament** | Joust against a rival's champion for a purse or for land. Before each pass, tap while the swinging meter is in the gold band to lower your lance. A clumsy couch makes it waver. Then drag to steer your lance onto their shield as they gallop in. Hitting the gold boss unhorses them. |

Then **End season**: rivals recruit and campaign, taxes come in, and your troops are paid.
Your army defends whatever land it stands in. It rides home to lift a siege of Highmere only
if it is no more than one step away.

### The story

Illustrated story panels tell the tale of the war:

- **Your sister Tamsin is taken hostage** at the end of the first year. Raid her captor's
  castle and fight up the tower stair to free her. Her household knights will then join you.
- **Rival lords** write gloating letters when they take your land, and sometimes offer a
  year-long truce, which you can accept or refuse.
- **Random events**: plague in the camp, blighted or golden harvests, fire at Highmere,
  mercenaries for hire, and wandering knights who offer their lances.
- **Rebellion**: lands far from your army may rise up, more often as your realm grows.

The game has an original chiptune score and sound effects, synthesised in code at runtime.
You can toggle sound on the title screen or the map.

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
core/   Pure Kotlin game logic: map, campaign rules, rival AI, story events, battle, joust,
        duel and siege simulations, chiptune synthesiser and score, save format.
        Unit-tested on the JVM.
app/    Android app: a single Activity and a custom View that draws every screen with Canvas
        at a virtual 480x270 resolution, using a 16-colour retro palette. Story scenes are
        drawn in code and audio plays through AudioTrack. There are no bitmap or audio
        assets and no AndroidX dependencies.
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

---

## Also in this repository: Hue Hive

[`huehive/`](huehive/README.md) holds **Hue Hive**, an original tile-matching game for
2 to 4 players. It plays in the browser and on Android, online or against the computer,
and keeps group and session scoreboards. Its APK builds alongside this one (`./gradlew :huehive:assembleDebug`).

