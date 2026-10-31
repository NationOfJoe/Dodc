# Hue Hive

An original tile-matching game for 2 to 4 players, played on a honeycomb. You build rows
of colours and glyphs. Play it in the browser or as an Android app:

- **On one device:** play against the computer (Easy, Medium or Hard), pass and play with
  friends, or mix the two.
- **Online:** 2–4 people, each on their own phone or computer, join a room with a five-letter code.
- **Groups and sessions:** a group is a set of players who keep score together. A group can
  have many sessions (one per game night, say), and each session has its own scoreboard
  (wins, total points, games, best score). Groups can live on one device or online, where
  everyone with the group code can see them. Offline games can also count toward an online group.

## Rules in short

- 75 hex tiles: 5 colours × 5 glyphs, 3 copies of each. Everyone holds 6 tiles.
- Tiles touching in a straight line form a **row**. A honeycomb has three directions, so one
  tile can be in up to three rows.
- In every row, **colour** must be all the same or all different, and so must **glyph**.
  No row may hold two identical tiles or more than 5 tiles. Rainbow rows (all colours and
  all glyphs different) are allowed.
- On your turn you can **place** tiles, **swap** tiles with the bag, or **pass**. Tiles you place
  must go in one straight line, join into one unbroken row, and touch the hive.
- Each row you create or extend scores 1 point per tile. A completed row of 5 is a **full comb**
  (+5). When the bag is empty, the first player to use their last tile gets +5 and the game ends.
  If nobody places a tile for two full rounds, the game ends.

The in-game **How to play** screen shows illustrated examples.

## Layout

```
huehive/
  web/
    public/          the client (plain HTML/CSS/JS, no build step)
      js/engine.js   rules engine, shared by browser, Android and server
      js/ai.js       computer players
      js/board.js    honeycomb rendering, pan / zoom / tap
      js/store.js    groups, sessions, settings on the device; API client
      js/app.js      screens and game flow
    server.js        online server (Node, no dependencies)
    test/            rules, AI and server tests (node --test)
    Dockerfile
  android/           Android app: a WebView that bundles web/public as its assets
```

## Run the web version and server

Requires Node 18 or newer. There is nothing to install.

```bash
cd huehive/web
npm start             # http://localhost:8080
npm test
```

The server hosts the web app and the online API on the same port. It saves groups and
scoreboards to `DATA_DIR/db.json` (default `huehive/web/data`). Live rooms are kept in memory.

| Variable   | Default        | Meaning                     |
| ---------- | -------------- | --------------------------- |
| `PORT`     | `8080`         | HTTP port                   |
| `DATA_DIR` | `./data`       | Where groups/scores are saved |

To deploy it anywhere that runs Docker (Fly.io, Render, Railway, a home server):

```bash
docker build -t hue-hive huehive/web
docker run -p 8080:8080 -v huehive-data:/data hue-hive
```

To play online with friends, share the server's URL, or an invite link from the room screen
(`https://your-server/?room=ABCDE`). Use HTTPS when the server is on the public internet.

## Android app

The `:huehive` Gradle module packages `web/public` into an APK. The rules engine and AI run on
the phone, so offline games need no network.

```bash
./gradlew :huehive:assembleDebug
# APK: huehive/android/build/outputs/apk/debug/
```

GitHub Actions builds it on every push. Download the **hue-hive-debug-apk** artifact from the
workflow run.

For online play in the app, open **Settings** and enter your server's address, for example
`https://hive.example.com` or `http://192.168.1.20:8080` on your home network.
Browser and app players can share the same rooms.
