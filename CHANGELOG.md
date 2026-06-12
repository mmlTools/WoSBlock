# WoSBlock Changelog

## 0.1.0 - In Development

### Renamed

- Completed the WoSBlock plugin rebrand across Java packages, Maven metadata, plugin metadata, README, changelog, and site copy.
- Renamed main class to `com.wosblock.WoSBlockPlugin`.
- Renamed Maven artifact to `wosblock`.
- Renamed admin permission to `wosblock.admin`.
- Added `/wosblock` as the primary admin command and kept `/wos` as an alias.
- Updated local MySQL dev database name to `wosblock`.

### Added

- Dedicated `black-market.yml` for Black Market prices, 30-minute rotation, and rare spawner offers.
- Thief now shows one random global Black Market offer at a time.
- Island-only item tooltips for Black Market price and Auction House min/max price.
- Auction House listing persistence in MySQL and flat-file storage.
- MySQL schema readiness gating for quest and auction reads/writes.
- Clerk quest GUI pagination.
- 30 starter Clerk quests.
- EnderWorld Egg item and `/wosblock giveenderworldegg`.
- Per-world inventory contexts.
- `/is leave` and `/is clear`.
- Directional island boundary expansion.
- Island HUD and Questie displays scoped to island context.
- `.gitignore` excluding build output, local runtime files, `build-instructions.md`, and `site/`.

### Changed

- Black Market configuration moved out of `config.yml`.
- Quest, fishing, scroll, enchant, generator, and black-market systems use dedicated YAML files.
- Daily/weekly/monthly quest completion timers and active quest progress persist through the selected storage backend.
- Coin balances are stored per world and through the selected storage backend.
- Gather quests no longer progress from item pickup. They are verified from inventory/container contents at turn-in.
- Gather and mining turn-ins consume the required items at claim time.
- Starter island generation is compact and includes buckets, crops, NPC eggs, and a small cave.

### Storage

- MySQL tables:
  - `wos_islands`
  - `wos_balances`
  - `wos_quest_completions`
  - `wos_quest_progress`
  - `wos_auction_listings`

### Documentation

- README and site content updated for WoSBlock naming, current commands, EnderWorld Egg, Black Market rotation, quest persistence, auction persistence, split configs, and MySQL setup.
