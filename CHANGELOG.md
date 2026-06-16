# WoSBlock Changelog

## 0.1.8 - In Development

### Fixed

- Filter hopper signs now block non-matching manual inventory insertion through shift-click, cursor placement, hotbar swap, and drag actions.

## 0.1.7 - In Development

### Fixed

- Filter hopper signs now block item entities picked up from the world, not only items moved in by another inventory.
- Filter sign parsing now ignores automation status lines such as `Filter enabled` when looking for a second-line item argument.
- Documented that hoppers can combine `/filter` and `/sender` signs.

## 0.1.6 - In Development

### Added

- Writing `/filter` without an item ID now uses the item in the player's main hand, consumes one, and writes the resolved item ID onto the sign.

## 0.1.5 - In Development

### Added

- Automation signs now write a second-line confirmation when created: `Sorting enabled`, `Filter enabled`, `Sender enabled`, or `Receiver enabled`.
- Sign confirmation preserves a second line that already contains another automation command.

## 0.1.4 - In Development

### Fixed

- Wireless sender hoppers now continue through multiple occupied hopper slots during one scan, so bursts of incoming items do not leave later slots stuck behind the first moved stack.

## 0.1.3 - In Development

### Fixed

- Fixed wireless sender hopper duplication by transferring from the fresh live hopper block state instead of repeatedly reading a scanned tile snapshot.
- Wireless hopper transfers now remove exactly the number of items accepted by the receiver chest, including partial-stack transfers.

## 0.1.2 - In Development

### Fixed

- Wireless `/sender <custom_code>` hoppers now actively drain into matching `/receiver <custom_code>` chests instead of relying on vanilla hopper output events.
- Automation signs can now contain multiple command lines, so one chest can be both `/sortable` and `/receiver <custom_code>`.
- Receiver chests can also use multiple nearby signs for separate automation roles.

## 0.1.1 - In Development

### Changed

- Replaced temporary chest sort and hopper filter UI buttons with sign-based automation.
- Sortable chests now use a nearby `/sortable` sign.
- Filter hoppers now use a nearby `/filter <itemid>` sign.
- Wireless hopper routing now uses a nearby `/sender <custom_code>` sign on the hopper and a matching `/receiver <custom_code>` sign on the destination chest.
- The Hopper Wireless Link scroll now points players to the sign setup instead of starting the old right-click linking flow.
- Updated README and site automation docs for the new sign commands.

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
- MySQL migration runner with `wos_schema_migrations` and versioned SQL files under `db/migrations`.
- RPG starter island rebuild command via `/is rebuild`.
- Configurable Auction House per-player listing cap and seller cancellation flow with a 10% fee.
- Chest sort and hopper filter buttons inside container UIs, replacing sneak-right-click container hijacking.
- Implemented/configured the full build-list set of 20 custom scrolls and 20 custom enchant books.
- `/wosblock listcustom` for listing configured scroll and custom enchant IDs before using give commands.

### Changed

- Black Market configuration moved out of `config.yml`.
- Quest, fishing, scroll, enchant, generator, and black-market systems use dedicated YAML files.
- Daily/weekly/monthly quest completion timers and active quest progress persist through the selected storage backend.
- Coin balances are stored per world and through the selected storage backend.
- Gather quests no longer progress from item pickup. They are verified from inventory/container contents at turn-in.
- Gather and mining turn-ins consume the required items at claim time.
- Starter island generation now keeps only the starter tree and chest; the guest book is now a chest item players can place wherever they want.
- HUD and Questie lock/unlock commands and display text were removed; only toggle commands remain.
- Custom enchants can coexist on the same item instead of replacing the previous custom enchant.
- New island parcels are reserved from the loaded island cache and placed in a linear row to prevent simultaneous `/is start` overlap.
- `/is clear` now blocks immediate restart until storage deletion, entity cleanup, and block clearing finish.
- `/is clear` now purges island-bound player data, including island-world balance, quest progress/completions, island inventory context, seller auction listings, and void recovery cache.
- Players who die on an island now respawn back on an island instead of the main world spawn.

### Storage

- MySQL tables:
  - `wos_islands`
  - `wos_balances`
  - `wos_quest_completions`
  - `wos_quest_progress`
  - `wos_auction_listings`
  - `wos_schema_migrations`

### Documentation

- README and site content updated for WoSBlock naming, current commands, EnderWorld Egg, Black Market rotation, quest persistence, auction persistence, split configs, and MySQL setup.
