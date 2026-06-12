# WoSBlock

High-performance Paper island plugin scaffold for multiplayer skyblock-style servers.

## Current Features

- Dedicated void island world with parcel spacing, compact starter island, starter cave, one bedrock core, starter chest, crops, lava/water buckets, and the three core NPC eggs.
- Core NPCs: Auctioneer, Thief, and Clerk. They are invulnerable, non-AI, non-collidable managed NPCs.
- EnderWorld Egg item for NPC relocation/capture. Core NPC relocation is guaranteed for trusted island players; other NPC captures can fail.
- Auction House via `/ah sell <price>` with configurable min/max material price limits.
- Auction listings persist in MySQL or flat-file storage.
- Black Market uses `black-market.yml`, shows one random global offer every 30 minutes, and supports rare spawner offers.
- Island-only item tooltips show Black Market price and Auction House min/max price, then disappear outside island context.
- Clerk quest GUI with pagination, 30 starter quests, active quest progress, repeatable/daily/weekly/monthly schedules, and persisted cooldowns/progress.
- Gather and mining quests verify required items in player inventory or island containers at claim time and consume them on turn-in.
- MySQL storage via HikariCP, plus flat-file YAML storage. Database work runs asynchronously.
- Persistent per-world coin balances, island data, quest completions/progress, and auction listings.
- Per-world inventory contexts so normal-world and island inventories do not mix.
- O(1) cached cobblestone generator weight tables and configurable mining XP per ore.
- Custom fishing loot tables, scrolls, custom enchant books, trophies, material compactor, hopper filter UI, chest sorting, void item recovery, and island waypoints.
- Island HUD and Questie displays only appear in island context.
- Per-player border display and directional island expansion scrolls.

## Build

```bash
mvn clean package
```

The shaded plugin jar is produced at:

```text
target/wosblock-0.1.0.jar
```

## Installation

1. Build the jar.
2. Copy `target/wosblock-0.1.0.jar` into your Paper server `plugins/` folder.
3. Restart the server.
4. Edit generated configs under `plugins/WoSBlock/`.
5. Restart or run `/wosblock reload`.

## Config Files

On first startup WoSBlock creates:

```text
config.yml
quests.yml
fishing.yml
scrolls.yml
enchants.yml
generator.yml
black-market.yml
```

Use `config.yml` for storage, island, NPC, auction limits, and achievement settings. Use the specialized files for larger systems. Specialized systems do not fall back to `config.yml`.

## MySQL Dev Database

Start the local developer database:

```bash
docker compose up -d
```

Use this plugin config:

```yaml
storage-type: mysql

mysql:
  jdbc-url: "jdbc:mysql://localhost:3306/wosblock?useSSL=false&serverTimezone=UTC"
  username: "skyblock"
  password: "change-me"
```

Stop it:

```bash
docker compose down
```

Remove database data:

```bash
docker compose down -v
```

MySQL tables currently used:

```text
wos_islands
wos_balances
wos_quest_completions
wos_quest_progress
wos_auction_listings
```

Flat-file mode stores player stats under:

```text
plugins/WoSBlock/player-stats/
```

Per-world inventory context files are stored under:

```text
plugins/WoSBlock/world-inventories/
```

## Player Commands

```text
/is start
/is create
/is home
/is leave
/is clear
/is trust <player>
/is settings
/is balance
/is compact
/is setwaypoint <1-3>
/is waypoint <1-3>
/ah sell <price>
/hud toggle
/hud lock
/questie toggle
/questie lock
```

## Admin Commands

`/wosblock` commands are admin-only. `/wos` remains as a short alias.

```text
/wosblock reload
/wosblock givescroll <id> [amount]
/wosblock giveenchant <id> [amount]
/wosblock givetrophy <tier>
/wosblock giveenderworldegg [amount]
```

The same admin subcommands also work with `/wos`, for example `/wos reload` and `/wos givescroll fly`.

Permission node:

```text
wosblock.admin
```

By default, OPs have this permission.

## Useful Test IDs

```text
/wosblock givescroll cobble-generator-xp
/wosblock givescroll repair
/wosblock givescroll feed-heal
/wosblock givescroll haste-speed
/wosblock givescroll fly
/wosblock givescroll void-fall-negation
/wosblock givescroll island-boundary-expander
/wosblock giveenchant blast-mining
/wosblock giveenchant auto-smelt
/wosblock giveenchant telekinesis
/wosblock givetrophy legendary
/wosblock giveenderworldegg
```

## Development Notes

- Java package: `com.wosblock`
- Main class: `com.wosblock.WoSBlockPlugin`
- Maven artifact: `wosblock`
- Plugin name: `WoSBlock`

Keep `README.md`, `CHANGELOG.md`, and `site/` content in sync whenever gameplay, config, commands, or storage behavior changes.
