<p align="center">
  <img src="app/src/main/res/drawable/logo.png" width="200" alt="Brew ni Cat logo">
</p>

<h1 align="center">Brew ni Cat POS</h1>

<p align="center">
  <em>Offline-first Android point of sale for a cat-themed coffee shop — with multi-device cloud sync, thermal receipt printing, and in-place self-updating.</em>
</p>

<p align="center">
  <img alt="platform" src="https://img.shields.io/badge/platform-Android%208.0%2B-3DDC84">
  <img alt="kotlin" src="https://img.shields.io/badge/Kotlin-2.0.21-7F52FF">
  <img alt="compose" src="https://img.shields.io/badge/Jetpack%20Compose-BOM%202024.12-4285F4">
  <img alt="room" src="https://img.shields.io/badge/Room-schema%20v19-orange">
  <img alt="version" src="https://img.shields.io/badge/release-1.1.0%20(10134)-blue">
  <img alt="license" src="https://img.shields.io/badge/license-MIT-lightgrey">
</p>

<p align="center">
  <a href="https://www.facebook.com/profile.php?id=100084186931413">
    <img alt="Brew ni Cat on Facebook" src="https://img.shields.io/badge/Facebook-Brew%20ni%20Cat-1877F2?logo=facebook&logoColor=white">
  </a>
  <a href="https://vt.tiktok.com/ZSQfGqNWc/">
    <img alt="Brew ni Cat on TikTok" src="https://img.shields.io/badge/TikTok-Brew%20ni%20Cat-000000?logo=tiktok&logoColor=white">
  </a>
</p>

---

## What it is

A single-store POS built to keep selling when the internet does not. Every order is written to a local Room database first and pushed to Supabase in the background, so a dropped connection never blocks a sale. When two devices are online — the counter tablet and the owner's phone — orders, voids, stock and expenses reconcile between them within seconds.

The register runs on a Redmi Pad 2 in the shop. Updates ship over the air: the app checks a cloud table on launch, downloads the new APK, and hands it to the system installer.

| | |
|---|---|
| **Register app** | Kotlin · Jetpack Compose · Material 3 · Room · WorkManager |
| **Cloud** | Supabase (PostgREST + Realtime + Auth, row-level security) |
| **Dashboard** | Next.js 14 · React 18 · Tailwind · supabase-js |
| **Hardware** | Bluetooth ESC/POS thermal printer |
| **Size** | ~2.5 MB release APK (R8 shrunk, obfuscation off so crash traces stay readable) |

---

## Screens

Three screens behind a launcher activity — deliberately flat, because speed at the counter beats navigation depth.

| Screen | Purpose |
|---|---|
| **Dashboard** | The register. Category tabs, product configuration sheet (flavor → size → extras), cart, held-order queue, discounts, cash/GCash checkout, expense entry. |
| **History** | **PIN-protected.** Paged order log with date-range filter, swipe-to-reveal share/edit/void, expense timeline, cashier breakdown, the per-item **Food & Drink Totals** breakdown (per day / week / month), and the Z-Reading summary + print. |
| **Inventory** | Raw materials, stock levels, reorder thresholds, and the recipe (BOM) editor mapping menu variants and flavors to ingredient deductions. |

Three overlays sit above them:

- **`PinScreen`** — guards History, which exposes takings and settings.
- **`SyncLoginGate`** — one-time store-account sign-in. Selling works fully without it; cloud sync stays off until it's done.
- **`AppUpdateGate`** — checks for a newer build on launch, downloads it, and opens the installer. Dismissible with *Hide* while it downloads.

---

## Architecture

Three-layer split, MVVM at the edge, no DI framework — dependencies are wired by hand in `AppContainer` (`CattasticPosApp.kt`) and resolved lazily.

```
app/src/main/java/com/example/cattasticpos/
├─ data/
│  ├─ local/           Room database, DAOs, entities, seeder, migrations, safety backup
│  ├─ repository/      Repository implementations over the DAOs
│  └─ sync/            OrderSyncMerger — the single cloud→local merge path
├─ domain/
│  ├─ model/           Cart, order, menu, config, inventory models
│  ├─ repository/      Repository interfaces
│  ├─ usecase/         Checkout, cart maths, voids, edits, recipe resolution, export
│  ├─ strategy/        Discount strategies
│  ├─ catalog/         Per-product add-on catalog
│  └─ service/         ESC/POS receipt printing
├─ service/            Supabase auth, realtime websocket, self-update
├─ worker/             WorkManager jobs
└─ ui/                 Compose screens, ViewModels, theme, adaptive/glass components
```

118 Kotlin source files. Design notes worth knowing before changing things:

- **`OrderSyncMerger` is the only path** that turns a cloud order into a local row. All three downloaders — realtime, historical pull, periodic catch-up — funnel through it so they can never disagree.
- **`CalculateCartUseCase` is the single source of truth for money.** Every surface formats with `%.0f`, so discounts are settled to a whole peso there. Without that, receipts don't add up and the drawer doesn't reconcile against the Z-Reading.
- **Downloaded orders become new local rows** with an autoincrement id, deduplicated on `remoteId`. Two devices that both created "order #5" cannot collide.

---

## Data model

Room schema **v19** — nine entities, fourteen migrations. `exportSchema = false`, and there is no destructive-migration fallback, so every schema bump needs a real migration path. Migrations are not all schema changes: `18 → 19` alters no table at all and exists only so a one-time data repair runs exactly once per device.

| Entity | Holds |
|---|---|
| `CategoryEntity` / `ItemEntity` | Menu catalog; variants and per-flavor prices stored as JSON on the item |
| `OrderEntity` / `OrderItemEntity` | Orders and lines, plus `deviceId`, `syncStatus`, `remoteId`, `isVoided`. (`isServed` remains in the schema and still syncs, but the Preparing/Served workflow was retired from the UI.) |
| `InventoryEntity` | Raw materials, stock, reorder threshold |
| `RecipeMappingEntity` | BOM: menu item + variant/flavor target → ingredient + quantity |
| `ExpenseEntity` | Cash-drawer expenses |
| `VoidRecordEntity` | Void audit trail — voids are soft, orders are flagged and never deleted |
| `AppConfigEntity` | Single row (`id = 1`): targets, float, PIN hash, cashiers/GCash JSON, Supabase URL/key, device id |

> ⚠️ **`app_config` is written with `OnConflictStrategy.REPLACE`.** Every writer must carry the whole row forward. Rebuilding the entity from scratch silently blanks the sync fields and re-mints the device id on the next read.

**Recipe resolution** (`RecipeDeductionResolver`) stacks additively: base rows (`variantName = null`) always apply, size and flavor rows add on top, and a composite target like `"4pcs|Shrimp Whisker"` replaces the size-only rows when both match.

**Combos** (`ComboBundleResolver`) expand into component items before deduction, so a combo draws down the same stock as its parts. Component `sizeVariantName` must be the variant **name**, not its id — `CatalogConsistencyTest` enforces this.

---

## Cloud sync

Offline-first, last-writer-wins on status. The register never blocks on the network.

**Auth.** `SupabaseAuthManager` owns the store-account session (password grant; refresh token in SharedPreferences). Row-level security rejects the anon key for business data, so every read and write carries the session token. The one exception is `app_release`, kept anon-readable so signed-out devices still receive updates.

**Upload.** New orders `POST` under a globally unique id, `deviceBucket × 1e9 + localId`. Once uploaded the `remoteId` is stored and later changes `PATCH` instead of re-posting. Status (`is_voided`, `is_served`) always syncs; totals and line items sync **only for orders this device owns**, so one terminal can never rewrite another's takings.

**Download.** Three paths, one merger:

| Path | Trigger |
|---|---|
| `SupabaseRealtimeManager` | Websocket `postgres_changes` on `orders` |
| `HistoricalPullWorker` | Once per install, paged |
| `SyncWorker` catch-up | Every 15 min, and after each checkout |

None of them restock on a remote void. The device that performed the void restocks locally and uploads corrected levels; everyone else picks those up through the normal inventory sync. Restocking on each terminal would multiply the stock back.

A row marked `PENDING` locally is never overwritten by a download — it carries a change that hasn't reached the cloud yet, and the upload phase publishes local truth instead. Voids are the exception: they are monotonic, so a remote void always sticks.

**Cloud tables:** `orders`, `order_items`, `categories`, `items`, `inventory`, `recipe_mappings`, `expenses`, `app_release`.

**Background jobs:**

| Worker | Schedule |
|---|---|
| `SyncWorker` | Every 15 min, plus an immediate one-off after checkout, receipt edit, serve toggle or config change |
| `HistoricalPullWorker` | Once per install |
| `LowStockCheckWorker` | Every 6 h → notification (needs `POST_NOTIFICATIONS`) |

---

## Self-updating

There is no Play Store listing. Distribution is one GitHub release asset plus a pointer row in Supabase.

```
build release APK  →  clobber the 1.1.0 release asset  →  insert an app_release row
                                                              ↓
                          tablet checks on launch, downloads, opens the installer
```

`AppUpdateManager` reads `app_release` ordered by `version_code` descending and offers the top row when it exceeds `BuildConfig.VERSION_CODE`.

Three rules keep this working:

1. **Every build must carry the same signature.** `app/keystore/release.jks` is committed on purpose — relying on a per-machine debug key broke in-place updates once already. Debug and release are both pinned to it.
2. **`versionName` stays `1.1.0`; `versionCode` increments.** The tag and asset URL never change; the asset is clobbered in place.
3. **Never sideload a build numbered above what's published.** Android hard-blocks downgrades (`INSTALL_FAILED_VERSION_DOWNGRADE`), and with `allowBackup="false"` the only recovery is uninstall — which wipes the entire local database.

> The `mandatory` column on `app_release` is **currently ignored** — `AppUpdateGate` never reads it. Updates are automatic regardless; the flag would need wiring up before it means anything.

---

## Web dashboard (`web/`)

Next.js 14 App Router against the same Supabase project via `supabase-js`, live-updating through realtime subscriptions on orders, items, inventory, categories and expenses. Styled as an Apple-inspired design system with a light/dark theme toggle (persisted, follows the OS preference on first visit).

It mirrors the register's owner-facing reporting: an **End of Day report** with a report-day picker (pull up any past day's takings the morning after) showing net revenue and profits, plus the per-item **Food & Drink Totals** with a per day / week / month toggle — both computed from the already-synced orders, no extra tables.

```bash
cd web
npm install
npm run dev      # http://localhost:3000
```

Falls back to the shipped project URL and publishable key when `NEXT_PUBLIC_SUPABASE_URL` / `NEXT_PUBLIC_SUPABASE_ANON_KEY` are unset, so it runs with no configuration. Set them to point at a different environment.

---

## Build and run

**Requirements:** JDK 17+, Android SDK 34, Android Studio (latest stable).

```bash
git clone https://github.com/RodneeGlenMartin/Brew-ni-Cat-POS.git
cd Brew-ni-Cat-POS

./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Debug builds are signed with the same pinned key as release, so one installs over the other without uninstalling.

```bash
./gradlew :app:testDebugUnitTest     # 51 unit tests
./gradlew :app:lintDebug             # must stay at 0 errors — lint failures fail the build
./gradlew :app:assembleRelease       # R8-shrunk, signed, ~2.5 MB
```

Bluetooth printing needs real hardware; it cannot be exercised on an emulator.

---

## Releasing

```bash
# 1. bump versionCode in app/build.gradle.kts (leave versionName alone)
# 2. verify
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleRelease

# 3. publish the APK, overwriting the existing asset
cp app/build/outputs/apk/release/app-release.apk BrewNiCatPOS.apk
gh release upload 1.1.0 BrewNiCatPOS.apk --clobber

# 4. point the tablets at it — insert a row into public.app_release
#    with the new version_code, version_name 1.1.0, and the same apk_url
```

Verify the published asset through the API, not the download URL — the CDN serves a stale copy for a minute or two after a clobber:

```bash
AID=$(gh api repos/RodneeGlenMartin/Brew-ni-Cat-POS/releases/tags/1.1.0 \
      --jq '.assets[]|select(.name=="BrewNiCatPOS.apk")|.id')
gh api -H "Accept: application/octet-stream" \
      repos/RodneeGlenMartin/Brew-ni-Cat-POS/releases/assets/$AID > check.apk
aapt dump badging check.apk | head -1
```

---

## Tests

65 JVM unit tests over the pure logic — the parts where a mistake costs money.

| Suite | Covers |
|---|---|
| `CartPricingTest` | Line totals, per-unit add-on surcharges, all discount strategies, flavor encode/parse |
| `DiscountRoundingTest` | Whole-peso settlement across every discount and subtotal, discount-label round trip, add-on rebuild |
| `AddOnEncodingTest` | Add-on selection encode/parse round trips |
| `AddOnSurchargeRepairTest` | The one-time `18 → 19` data repair that re-prices historical add-on surcharges |
| `RecipeDeductionResolverTest` | Base / size / flavor / composite / add-on BOM stacking |
| `SyncIdentityTest` | Combo expansion maths, the cloud order-id contract and its `% 1e9` invariant |
| `AddOnCatalogTest` | Sweeps the seeded catalog so retired add-ons cannot reappear via the default branch |
| `CatalogConsistencyTest` | Unique ids, every combo component resolving to a real variant **name**, every recipe row targeting something that exists, no raw material starting below one serving |

The last two exist because catalog data drifts silently: a combo pointing at a variant id instead of a name, or an add-on duplicating a real SKU, will not fail to compile.

Compose UI and the sync workers have **no automated coverage** — they are verified by hand on device.

---

## Operational notes

- **PIN.** History is PIN-gated and the hash lives in `app_config`. There is a seeded default — change it in App Settings on a new install.
- **Voids are soft.** Orders are flagged `isVoided`, never deleted, and a `VoidRecordEntity` records reason, cashier and amount.
- **Backups are off.** `allowBackup="false"`, so uninstalling loses local data. Use **Export** in History to write orders and expenses to Downloads as CSV.
- **Money is whole pesos.** Every display and receipt uses `%.0f`, and discounts are rounded at calculation time so the stored figure always matches the printed one.
- **History is never rewritten.** Orders recorded before a pricing fix keep their original values, so past Z-Readings still reflect what was actually taken.
- **The publishable Supabase key is in source on purpose.** It is public by nature and grants nothing alone; RLS plus the store-account session are what protect the data. A `service_role` or `sb_secret_` key must never be committed.

---

## Known gaps

An honest list, so nobody rediscovers these the hard way:

- Rotating the tablet loses half-typed expense and inventory form state (`remember` rather than `rememberSaveable`).
- Room migrations only chain from schema **v6** upward, with no destructive fallback — a pre-v6 database fails to open and lands in `CrashActivity`.
- Bluetooth printing cannot be covered by automated tests.
- 95 lint warnings remain (mostly `DefaultLocale` on `%.0f`, harmless in en/PHP locales). 0 errors, which is enforced.
- `IMPLEMENTATION_SUMMARY.md`, `MULTIDEVICE_SYNC_GUIDE.md`, `QUICK_REFERENCE.md` and `SYNC_IMPLEMENTATION_COMPLETE.md` document the June 2026 sync work and are **partly out of date** — they predate authentication, the `remoteId` scheme and the current release pipeline. `RELEASE_INSTRUCTIONS.md` is current. Treat this README as authoritative where they disagree.

---

## The shop

This POS runs the counter at Brew ni Cat — a cat-themed coffee shop in Kabacan, Cotabato.

| | |
|---|---|
| **Address** | Segundo St, Poblacion, Kabacan, Cotabato 9407, Philippines — beside Pulido Eatery |
| **Find us** | [4R7G+9FC Kabacan](https://www.google.com/maps/search/?api=1&query=4R7G%2B9FC%20Kabacan%2C%20Cotabato) |
| **Mobile** | [0976 630 4785](tel:+639766304785) |
| **WhatsApp** | [+63 976 630 4785](https://wa.me/639766304785) |
| **Email** | [popotpulido06@gmail.com](mailto:popotpulido06@gmail.com) |
| **Facebook** | [Brew ni Cat](https://www.facebook.com/profile.php?id=100084186931413) |
| **TikTok** | [Brew ni Cat](https://vt.tiktok.com/ZSQfGqNWc/) |

---

## License

MIT — Copyright (c) 2026 Rodnee Glen Martin. See [LICENSE](LICENSE).
