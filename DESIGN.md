# OSM Welcome Tool — Design System

## Product Purpose

Monitors OpenStreetMap changesets globally (no geographic bias) and identifies new mappers to welcome. Analyzes contributions, classifies users (newcomer, returning, power user), and facilitates sending welcome messages. Designed to be a considerate consumer of OSM ecosystem APIs — the UI always communicates data recency so users understand when information may be stale.

---

## Brand & Visual Style

**Cartographic Material** — A modern corporate style infused with Material Expressive principles. Inspired by cartography, featuring a palette of grass green, water blue, and earth brown. The UI is lightweight and recedes to prioritize geographic data and content.

**API Gentle Design principle**: The UI never pretends data is always fresh. It embraces offline-capable patterns and communicates recency transparently (timestamps, stale warnings). This honesty reduces user frustration and reinforces trust.

**UI Refinement**: **Fading Edges** (vertical gradients) in scrollable areas soften the transition between content and navigation bars.

---

## Screens

### 1. User List (`user_list`)
- **Search bar** (Rounded 12dp) + category filter chips.
- **LazyColumn** with "Expressive" cards: `shape = RoundedCornerShape(24.dp)`, elevation 1dp.
- **Stale data banner**: When data is not current, a non-intrusive banner at the top shows "Last updated: [timestamp]" in `bodySmall` on `tertiaryContainer`.
- **Fading Edge**: Top gradient for smooth scroll fading.
- TopAppBar with `Settings` icon and dynamic background transition.

### 2. User Detail (`user_detail/{userId}`)
- Large avatar (120dp) + name in `headlineMedium`.
- Detail cards: `RoundedCornerShape(24.dp)`, internal padding of 20dp.
- Row of 3 active **StatusCards** on `primaryContainer`.
- Rounded action buttons (16dp) with clear iconography.
- Each detail card shows `lastUpdated` timestamp in `labelSmall` when data is cached.

### 3. Settings (`settings`)
- **ExpandableSettingsSection**: Card `RoundedCornerShape(24.dp)`, elevation 2dp.
- **Expressive Layout**: Generous spacing (20-24dp padding) to avoid clutter.
- **Fading Edge**: Top gradient for fluid scrolling under the TopAppBar.
- Danger section with `error` background (90% alpha).
- Sync interval: slider or picker with minimum 15 min, default 30 min.
- Notifications: default OFF, explicit toggle to enable.

### 4. Licenses (`licenses`)
- Open source libraries with search + license filter chips.

### 5. Offline / Stale state (cross-cutting)
- All list and detail screens detect stale data and show a visual indicator.
- **Banner pattern**: `Surface` on `tertiaryContainer`, rounded 12dp, containing an `Icon` (info) + `Text` ("Data may be stale, last updated: [relative time]") + optional `TextButton` ("Refresh").
- The banner collapses or disappears after a manual refresh or when fresh data arrives from the scheduled worker.

---

## Colors

### Light theme (Cartographic Material)

| Role | Hex | Example |
|------|-----|---------|
| **Primary** (Grass Green) | `#436900` | Buttons, links, active icons |
| On Primary | `#FFFFFF` | Text on primary |
| Primary Container | `#7DB031` | Active card backgrounds |
| On Primary Container | `#263E00` | Text on container |
| **Secondary** (Water Blue) | `#47617B` | Informational accents |
| On Secondary | `#FFFFFF` | |
| Secondary Container | `#C5DFFE` | |
| On Secondary Container | `#4A637D` | |
| **Tertiary** (Earth Brown) | `#645E4D` | Card backgrounds, details |
| On Tertiary | `#FFFFFF` | |
| Tertiary Container | `#A8A18D` | |
| On Tertiary Container | `#3C3829` | |
| **Error** | `#BA1A1A` | |
| Background | `#FCF9F8` | Screen background |
| Surface | `#FCF9F8` | |
| On Surface | `#1B1C1C` | Primary text |
| Surface Variant | `#E5E2E1` | Neutral card backgrounds |
| On Surface Variant | `#434938` | Secondary text |
| Outline | `#737A67` | Borders |
| Outline Variant | `#C3C9B3` | Light borders |

### Dark theme (Cartographic Material)

| Role | Hex |
|------|-----|
| Primary | `#A1D754` |
| Primary Container | `#314F00` |
| Secondary | `#AFC9E7` |
| Secondary Container | `#2F4962` |
| Tertiary | `#CEC6B1` |
| Tertiary Container | `#4C4737` |
| Background | `#1B1C1C` |
| Surface Variant | `#434938` |

---

## Typography

**Plus Jakarta Sans** (approximated via system SansSerif). Soft curves and open counters for high legibility.

| Style | Weight | Size | LineH | LetterSp |
|-------|--------|------|-------|----------|
| `displayLarge` | Bold | 57sp | 64sp | -0.25sp |
| `headlineLarge` | Bold | 32sp | 40sp | — |
| `headlineMedium` | Bold | 28sp | 36sp | — |
| `titleLarge` | SemiBold | 22sp | 28sp | — |
| `titleMedium` | Bold | 18sp | 24sp | 0.15sp |
| `bodyLarge` | Normal | 16sp | 24sp | 0.5sp |
| `bodyMedium` | Normal | 14sp | 20sp | 0.25sp |
| `labelLarge` | Medium | 14sp | 20sp | 0.1sp |
| `labelMedium` | Medium | 12sp | 16sp | 0.5sp |

---

## Shapes

- **Small** (chips, specific inputs): 8dp-12dp radius.
- **Medium** (standard cards): 16dp radius.
- **Large / Expressive** (settings cards, list cards): **24dp** radius (`rounded-xl`).

---

## Icons

Material Icons with emphasis on primary and secondary theme tones. Larger icons (18-20dp) for interactive controls.

---

## Notifications

- User notifications are **opt-in** (default off).
- When enabled, they follow the system notification channel settings (no forced sound/vibration).
- Two channels: "New changesets in area" and "New mapper detected".

---

## Locale

Italian (Primary), English (Fallback). Full i18n via `strings.xml` is a future goal.
