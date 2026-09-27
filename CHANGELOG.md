## Unreleased

## Changed

- Build: Tango's HudLib now comes from Modrinth Maven (`maven.modrinth:dynamic-hudlib:1.2.0+<mc>`) for 26.1.2 and 26.2 instead of the sibling `../TangosHudLib` build; it is still embedded via `include`. 26.3 keeps the local build until HudLib 1.2.0+26.3 is published.

## 1.5.0

First stable release of the 1.5.0 line; the beta sections below list everything that changed since 1.4.0. Player-facing notes live in `MODRINTH.md`.

## Added

- Add support for Minecraft 26.3.x.
- Add a `Market price basis` setting under `Protection` (`MarketGuardConfig.marketPriceBasis`, enum `ReferencePriceBasis`: `LOWEST_BIN` default, `MEDIAN`, `FAVOURABLE`); the Auction Price HUD and the over-/underbidding checks resolve the reference through `AuctionReferencePrice.purchaseReference(basis)` / `listingReference(basis)`, so the HUD shows the same price and percentage the warning uses. The HUD labels the row by what it shows (`Lowest BIN: X`, `Median: ~X`, `Market: ~X`), names it in the verdict (`25% above Lowest BIN - overpriced`), never softens a verdict against a Lowest BIN, and when the reference is low quality appends the disagreeing signal (`(avg X)` behind a Lowest BIN, `(BIN X)` behind a median) or `(few sales)`.
- Add an `Update notifications` setting under `General` that controls the Modrinth update message on join.
- Expose a `marketguard-api` Fabric entrypoint. `MarketGuardApi` is now an interface implemented by `MarketGuardApiEntrypoint`, so other mods can discover it with `FabricLoader.getEntrypoints` and share MarketGuard's Lowest BIN, Bazaar and Player API requests instead of sending their own.
- Add `MarketGuardSettingsApi` so companion mods such as PackCore can read and toggle update notifications during modpack setup.

## Changed

- Publish as a stable release instead of a beta.
- Replace the static `MarketGuardApi.lookupCached...`/`request...` methods with the entrypoint instance methods of the same name.
- Rewrite the Auction Price HUD lines: the item name in its rarity colour, grey labels with coloured values — `Auction: ` + gold price, `Market: ` + white `~X` (plus a yellow `(few sales)` for a single-signal reference or `(volatile)` when the signals disagree) — and one `advice` verdict line driven by the configured over-/underbidding thresholds (`Roughly ...` and never red for low-quality references; disabled thresholds fall back to the defaults; an auction at or below the API's Lowest BIN is `Cheapest BIN right now[, N% below/above market]` in green regardless of reference quality, or red `Cheapest BIN, but N% above market - overpriced` when a reliable reference puts it over the overbidding threshold) with the coin difference appended in grey (`... (+390k coins)`, omitted at exactly the market price). The separate `difference` row is gone (`normalizeRows` drops it from saved layouts; `LEGACY_AUCTION_PRICE_HUD_ROWS` and `AUCTION_PRICE_HUD_ROW_IDS` with it). Price-trend and resale warnings only when `MarketRiskEvaluator` reports high risk.
- Rewrite the Player HUD lines and presets: `trade` = name, `Never seen before` / `Seen once` / `Seen N times`, blacklist status (`ON YOUR BLACKLIST - be careful` / `Not on your blacklist`, only while ScamScreener is installed; `profile`/`all` show `ScamScreener not installed` otherwise) + status line only when the player or their SkyBlock profile cannot be resolved (`ok`/`partial` produce no line); `compact` adds `wealth` and `profile_value`; `partial` no longer produces a line; rows `finance_status`, `finance_history` and `unavailable` were removed (dropped from existing configs by `normalizeRows`); `data` shows `Updated HH:mm` or `Data may be outdated`.
- Remove the Minion production forecast (`Observation`/`Forecast`, rows `forecast`/`forecast_status`, the `marketguard.hud.minion.forecast.*` keys, `BazaarProfit.valueDelta`); the Minion HUD shows `Held coins: X` and `Storage sells for: X`, the Forge HUD `Forge items sell for: X` (`+` when stacks are unpriced), plus the unpriced-stack count (hidden while no price is known yet).
- Rename the HUD editor row labels to plain terms in both languages and add the missing `marketguard.hud.row.volatility`/`liquidity` labels (the editor showed the raw keys); editor previews use the same number format as the real lines.
- `PlayerHud` reads `MarketGuardConfig.playerHudPreset` directly; the mirrored `PlayerHud.Preset` enum, `setPreset` and the `writeChanges` hook are gone, so `/mg reload` applies a changed preset immediately.
- Default HUD screen and row lists live once as `MarketGuardConfig.DEFAULT_*` constants; `HudCustomization` and `normalizeValues` use them.
- `MarketGuardConfig.save()` and `ProfitTrackerHud.setDisplayEnabled()` are `void`; MidnightLib never reports write failures, so the unreachable rollback branches were removed.
- Add `HudScreenGroup.key()` for the config key of a screen group.
- `VisibleProfileValue.estimate(knownFinance, items, lookup)` replaces the always-null `purse` parameter and the unused balance counters.
- Command feedback names `scamscreener_marketguard/config.json`; `formatPrice` takes a `long`.

## Fixed

- Read the BIN purchase price from the auction item instead of the `Buy Item Right Now` button, restoring overbidding protection for purchases.
- Keep overbidding and underbidding checks running when `AuctionReferencePrice` is low quality: a purchase is compared against the highest of the Lowest BIN / 7-day / 30-day signals and a listing against the lowest (`purchaseReference()` / `listingReference()`), so a price that at least one signal supports is never blocked (a 170k listing of an item whose Lowest BIN is 170k but whose averages are 1.4M/2.1M passes; a 60k purchase of an item whose signals are 3k/5k/8k is blocked). Agreeing signals keep the median; the HUD keeps showing the median.
- Scale the reference price to the auction's stack size (`SkyBlockItemUtil.getStackCount`: stack count or Hypixel's `47x` name prefix) in the protection checks and the Auction Price HUD; the API's Lowest BIN and averages are per unit, so a `47x Shiny Orb` stack at 400k was compared against the price of a single orb (`3918% above market`). For stacks the HUD appends the per-item price in grey to the auction and reference rows (`(8,511 each)`). `LowestBinData.LookupResult` gains `lowestBinCount` (the API's new `count` field, 1 when absent; the old 6-argument constructor delegates), and when the cheapest per-unit listing is a stack the HUD names it: `Lowest BIN: 64x for 300,000 coins (4,688 each)`.
- Price level-100 pets against their own `TYPE;TIER+100` reference and use the API's 0-based pet tiers (`COMMON` was mapped to the `UNCOMMON` key, `UNCOMMON` had no key at all).
- Stop multiplying Bazaar instant buy/sell chat totals by the quantity again in the Profit Tracker.
- Keep a partially claimed Bazaar buy order pending until it is fully claimed, and drop a cancelled unfilled order instead of letting it suppress the cost of the next identical order.
- Write `profit_tracker.json` to a temp file and move it atomically, so a crash mid-save no longer wipes the all-time profits.
- Use the non-notifying Lowest BIN lookup in the Trade Guard and in `MarketGuardApi.lookupCachedLowestBin`, so unrelated auctioneers no longer trigger blacklist chat notices.
- Send Player API requests through `HttpClient` (hostname verification, chunked decoding) instead of a hand-rolled TLS socket.
- Keep the stale warning in the Player HUD `data` row (`Data may be outdated`) and surface failed finance requests as `Finance data unavailable` instead of claiming finance-based values.
- Only announce a Modrinth update when the listed release is actually newer than the installed version.
- Use `Locale.ROOT` for HUD screen keys so HUDs stay visible on Turkish/Azeri system locales.
- Keep Player HUD rows of other presets when reordering rows in the editor.
- Preserve an empty HUD screen list (HUD disabled everywhere) across restarts instead of restoring the defaults.
- Recognise the Player HUD target more robustly: `Seller: [MVP+] Name` lore lines with a rank tag, `Name' Profile` titles, surrounding whitespace, and trade titles whose partner name Hypixel cut off (completed from the tab list when unambiguous). Unrecognised `Seller:` lines and `You ...` titles are logged with `/mg debug`.

## Performance

- Scrape container slots for the Trade Guard, Minion, Forge and Auction Price HUDs once per client tick (`AbstractContainerScreen.tick`) instead of once per rendered frame, and skip the BIN item-slot work while the slot's stack instance is unchanged.
- Resolve Bazaar/Lowest BIN item ids by display name through a per-snapshot name index instead of scanning every product with a regex on each lookup; `LowestBinData.lookupPriceData` resolves the product once per lookup instead of four times.
- Record lobby encounters off the render thread and skip the SQLite round-trip when the /locraw response reports the same lobby with no new players (previously every 10 s response opened a connection and ran one INSERT per tab-list entry on the render thread).
- Record Bazaar profit as plain cash flow; the sell-side FIFO lot matching never changed the number and is gone, and buys no longer accumulate `TrackedBazaarPosition` entries.
- Drop tracked auction positions, Bazaar positions and pending auction listings older than 14 days on load and save so the store no longer grows without bound.
- The Profit Tracker widget skips the scoreboard profile lookup while the HUD is disabled, outside SkyBlock or without a player.
- Finance data flagged `stale` by the server is displayed as such but no longer re-requested before the cache TTL expires.

## Removed

- The synchronous snapshot fetch path (`SnapshotCache.getSnapshot`, `SyncFetcher`, `LowestBinData.getLowestBin`, `BazaarData.getProduct`) and the two `live-api` tests that were its only users.
- Dead code: `PlayerFinanceData` fields never read (`donatedIds`, `specialIds`, `fetchedAt`, profile `name`/`selected`), `HudCustomization.toggleRow`/`moveRow`/`moveRowTo`/`visibilityLabel`, `TradeGuardHud.Offer.empty`, `ProfitTracker.confirmBazaarFill` and the no-arg profit getters, `ProfitTrackerStore` path-less overloads, `MarketGuard.id`, the `ModrinthUpdateChecker` copy of `currentVersion`, `AuctionSlots.CREATE_BIN_CONFIRM` and the unused `AuctionSlots.ITEM_PRICE` name pattern, `AuctionInventory.MAIN_COOP`, the `exceedsAbsoluteThreshold` wrappers, the mixin's `BYPASS_TITLE`/`AtomicInteger` bypass bookkeeping and `shouldTriggerBlacklistCheckOnOpen`, unused Stonecutter swaps/constants and duplicated `processResources` inputs.
- Lang keys left without users (after the HUD rewrite): `marketguard.hud.row.title`, `marketguard.hud.scamscreener.installed`, `marketguard.hud.rows`, `marketguard.hud.rows.drag`, `marketguard.hud.visible`, `marketguard.hud.hidden`, `marketguard.midnightconfig.playerHudPreset` (+ `.tooltip`).

## 1.5.0-beta.3

## Added

- Add observed Minion production forecasts using current Bazaar instant-sell prices.
- Add auction price volatility and Bazaar liquidity warnings.
- Add an item-side Trade Guard for the Hypixel trade screen with conservative warning suppression when prices are missing, stale, or low confidence.
- Add known profile-value, skill, finance, and Museum context through the normalized ScamScreener API finance contract.

## Fixed

- Keep the Player HUD encounter count consistent between the live widget and editor preview.

## Changed

- Use a quality-rated reference assembled from Lowest BIN, 7-day, and 30-day auction prices for HUD guidance and auction protection.
- Remove the template title row from every HUD widget and from the HUD row editor.
- Remove obsolete `title` rows from existing HUD configurations during normalization.

## 1.5.0-beta.2

## Fixed

- Cache the Player HUD's ScamScreener blacklist lookup to avoid repeated render-time lookups and debug-log spam.
- Parse chunked HTTP responses from the Player API correctly.
- Keep the UUID row visible in the Player HUD's `profile` preset.

## Changed

- Player HUD requests without an explicit `profileId` now work with the API's selected-profile resolution.

## 1.5.0-beta.1

- Calculate the Auction Price HUD difference against the API's 7-day average instead of Lowest BIN.
- Keep rendering available player fields when the Player API reports partial or unavailable data.
- Add the `All` Player HUD preset and an option to render unavailable values as `n/a`.
- Move the Player HUD profile selector into the dedicated Player HUD editor.
- Show configured `n/a` rows even when the Player API request itself is unavailable.
- Add a Player HUD layout picker with load, edit, save/update, delete, share, and clipboard import actions.
- Expose a typed `PlayerApiUnavailableException` when the reachable Player API endpoint returns no usable result.
- Show real HUD examples in the row editor and move rows between visible and hidden columns with drag and drop.
- Add support for Minecraft 26.2.x
- Add MidnightLib-backed client configuration for auction protection and MarketGuard HUDs.
- Track personal and co-op bank interest together, plus Allowance income, in the Profit Tracker HUD.
- Add a confirmed settings action for resetting all persisted Profit Tracker data.
- Fix the MidnightLib HUD settings crash caused by missing metadata on custom controls.
- Align the custom HUD editor with MidnightLib's layout and hide duplicate raw list fields.
- Split HUD settings into per-HUD editors with drag-and-drop row ordering.
- Open the settings screen one tick after `/mg` or `/marketguard` so chat closing cannot immediately hide it.

## 1.4.0

- Add support for Minecraft 26.2.x
