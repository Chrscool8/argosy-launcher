---
name: code-quality
description: Code quality standards and patterns for Argosy. Use BEFORE writing or modifying code to ensure consistency with project patterns.
---

# Code Quality Standards

Essential reminders for writing consistent, maintainable code in this project.

---

## Mandatory Requirements

These are NON-NEGOTIABLE. A feature missing any of these is INCOMPLETE.

### 0. Comments: code is for code, the PR is for explainers

Default to zero. Before keeping any comment, rewrite it as a noun phrase. If it
survives, keep it; if it cannot, delete it.

- Survives: `JVM heap budget: 11 GB total, 16 GB CI runner.`
- Cannot: `The Kotlin daemon carries the compile, so it takes the larger share.`

A comment states what a declaration IS. The moment it explains why a choice was
made, it belongs in the commit message and the PR description, which `git blame`
retrieves from the line anyway. Any of these words means the sentence is arguing,
so delete it: because, so, since, which is why, rather than, instead of, not just,
to avoid, otherwise, this ensures, deliberately, on purpose, note that, we, our.

Also delete any number or comparison that came out of a debugging session. "17x",
"was 2h40m", "under X it did Y" is history, not contract.

Zero inline `//` inside function bodies. No single-line `/* */` or `/** */`
anywhere, including declaration-level KDoc: the one-line block form is a rephrased
`//`. A genuinely needed KDoc uses the multi-line block form above a non-obvious
PUBLIC contract. Never on a private declaration; rename it instead. KDoc states the
contract. It never narrates what the body does, argues for a design, or carries
history.

Two backstops exist. For Claude sessions, `.claude/hooks/smell-guard.py` checks the
word list after each edit is written. For every contributor, the blocking `rules` job
in `.github/workflows/build.yml` runs `scripts/ci/agentic-smells.py` against
`scripts/ci/smell-rules.json`, whose `inline-comment` and `disguised-block-comment`
rules flag added lines on a pull request. Passing either is not the goal; not needing
them is.

### 1. Input Handling (TV + Touch)
All interactive UI components MUST have:

**Touch Support:**
- Use `Modifier.clickableNoFocus { }` (from `ui/util/Modifiers.kt`)
- NEVER use plain `Modifier.clickable()` - it enables Compose TV focus

**Controller Support:**
- `InputHandler` implementation for D-pad navigation
- Proper index wrapping with `.mod(size)` (NOT `% size`)
- Visual focus state via `isFocused: Boolean` prop

**Focus Management:**
- NO Compose focus for navigation or selection. `focusable()` appears once per
  rendered surface, as that surface's root key sink: `ArgosyApp` for the control
  surface, and the presentation branch of both `ArgosyActivity` and
  `SecondaryHomeActivity`. Each activity renders either `ArgosyApp` or
  `PresentationSlotContent`, so each needs a sink on the presentation side. A surface
  rendered without one leaves its window with nothing focusable and Android ANRs the
  activity with "does not have a focused window". The other legitimate
  exception is `FocusRequester` for soft-keyboard text entry (~20 `ui/` files); any of
  these becomes a violation the moment focus decides what is SELECTED rather than what
  is TYPED INTO.
- Focus index stored in ViewModel
- Manual focus visuals via `FocusIndicators` (`ui/primitives/Focus.kt`): fill/halo/stripe/ring/lift;
  never movement or scale except lift, which is reserved for cover tiles
  (`FocusIndicators.Tile` is the only preset that sets `lift`)

A component that only handles ONE input modality is INCOMPLETE.
Pattern: Check existing components for `InputHandler` + `clickableNoFocus` pairing.

**Modal input capture (learned the hard way, 2026-07-06):** a modal/dialog/overlay that
RENDERS without taking the input stack is INCOMPLETE - gamepad input drives the screen
behind it (dual-focus ghost, drawer-over-dialog). Every modal must use exactly one of:
(A) `ModalInputEffect`/`pushModal` (self-contained; `ArgosyConfirmModalHost` does this for
you - pushes IMMEDIATELY on open, never deferred to a lifecycle event), (B) the host
handler guarding the modal's visibility state in EVERY nav method (up/down/left/right/
confirm/back AND prev/next section/trigger - partial guards leak the unguarded
directions), or (C) a handler the host stores and delegates to. `Modal`/`CenteredModal`/
`ModalScaffold` are pure visuals - they capture NOTHING by themselves.
Mechanism-B guards MUST return `InputResult.HANDLED`, never UNHANDLED: the app-level
fallback treats an unhandled result as "no modal here" and runs global actions (LEFT
opens the drawer, Menu toggles it) straight over the modal. A guard that returns
UNHANDLED is the bug, not a guard.

### 2. Footer Hints (V2: the control is the guide)
Authority: `design-handoff/CONTROL-FOUNDATIONS.md`. The bar surfaces what is NOT obvious
from the focused control; the inline affordance carries the interaction.
- A / B / d-pad / Back alone never justify a bar - if they are the only candidates, no bar.
- Hints that earn the bar: screen-global verbs (search, filter), shoulder paging (LB/RB),
  non-obvious bound buttons (X/Y, triggers).
- When space is limited, shed OBVIOUS guides first (d-pad, then A/B); never drop a
  non-obvious hint to keep an obvious one. `FooterHint.hidePriority()` implements this
  order (X/Y highest keep priority, A/B low, d-pad lowest).
- A/Confirm means enter/commit/toggle, NEVER adjust; sliders/steppers/enums adjust on
  Left/Right only.

### 3. Lazy List Scrolling
A scrolling collection of repeated items uses LazyColumn, LazyRow or LazyVerticalGrid.
Column and Row stay the tools for fixed layout.
- Lazy lists compose only visible items and keep scroll state for gamepad navigation
- A repeated, scrolling collection gets a lazy list even when it is small today

---

### User-Facing Text

Every string a user reads is a resource id. Raw English in a `text =`, `title =`,
`subtitle =`, `label =`, `message =` or `contentDescription =` argument is a defect,
and `scripts/ci/smell-rules.json` fails the build on one (AS-7).

- Add the key to the `res/values/strings_<area>.xml` that owns the screen, named
  `<area>_<component>_<role>` for the usage site.
- **Identical English at two usage sites gets two keys.** Never deduplicate. Seven
  `LibretroCoreRegistry` display names are byte-identical to save-tree folder names
  in `EmulatorRegistry.retroArchSaveDirByCore`; fusing them breaks save-path
  resolution for every user who already has saves on a server.
- **A string is a label or a token, never both** (AS-8). Anything compared, stored
  in DataStore, used as a map key, an `options.indexOf(...)` entry, a route or a
  path component stays a literal. `indexOf` misses tend to `coerceAtLeast(0)` rather
  than throw, so getting this wrong resets a user's setting with nothing logged.
- **Labels for `data/` and `domain/` types live in `ui/common/`** as extension
  properties, following `labelRes` in `ui/common/CompletionStatusUi.kt`. `R` never
  crosses inward. That file is the label pattern only. Its `color` property still
  holds raw `Color(0xFF...)` literals; completion colors come from
  `ColorTokens.Domain.Completion`.
- **Carriers are `@StringRes Int`, never `(Context) -> String`.** A lambda is not
  stability-safe under `compose_stability_config.conf`, and ViewModels outlive the
  activity recreation a locale change triggers.
- **Never put a gamepad button letter inside a string.** A/B are user-swappable;
  render `InputGlyph` beside the text. The sole exception is a row whose purpose is
  to change that very mapping.
- Never translate upstream identifiers: core ids, libretro/RetroArch option tokens
  and shader names, core and emulator display names, platform slugs, paths, package
  names. `libretro/coreoptions/**` stays English by decision.
- Use `<plurals>` and `pluralStringResource` instead of `"$n item(s)"`.
- Add an XML translator comment above any string whose English is ambiguous out of
  context. This app splits hard on "Save" (verb on an action, noun in save sync),
  "State" (save state vs completion status), "Play", "Frame", "Core", "Channel",
  "Collection". The comment is the only context a translator gets.

## Pre-Implementation Questions

Before writing code, answer these questions:

1. **Scope**: What are ALL the places this feature touches? (UI, data, settings, navigation)
2. **States**: What are the error, loading, empty, and success states?
3. **Lifecycle**: What happens on rotation? Backgrounding? Return from other app?
4. **First-run**: Does this behave differently on fresh install vs existing data?
5. **Consistency**: What existing patterns should this follow? (Find and cite file:line examples)

If you cannot answer these, investigate before writing code.

---

## Core Principles

### SOLID
- **Single Responsibility**: Each class/function does one thing well
- **Open/Closed**: Extend behavior without modifying existing code
- **Liskov Substitution**: Subtypes must be substitutable for base types
- **Interface Segregation**: Small, focused interfaces over large ones
- **Dependency Inversion**: Depend on abstractions, not concretions

### DRY (Don't Repeat Yourself)
- Extract common logic into shared utilities
- Reuse existing components before creating new ones
- Check for existing patterns in codebase before implementing

### KISS (Keep It Simple, Stupid)
- Prefer simple, readable solutions over clever ones
- Avoid premature optimization
- Don't add features "just in case"

### YAGNI (You Aren't Gonna Need It)
- Only implement what's currently needed
- Remove unused code rather than commenting it out
- Don't build for hypothetical future requirements

## Project-Specific Patterns

### UI State Management
- State flows from ViewModel to Composables via `StateFlow`
- Use delegates for section-specific logic (e.g., `DisplaySettingsDelegate`)
- Update state via `_uiState.update { it.copy(...) }`
- Keep UI state in single `UiState` data class per screen

### StateFlow writes MUST be atomic (lost-update trap, learned 2026-07-22)
`_uiState.value = _uiState.value.copy(...)` is a read-modify-write that silently
loses concurrent updates. On screens with several collectors (feed, profile,
prefs), a field written by one collector gets wiped by another and never
re-emits - the avatar-doodle fields vanished on exactly the busy social screens
this way while quiet screens worked. Rules:
- ALWAYS `_uiState.update { it.copy(...) }` - never assign `.value` from a read
  of `.value`, even though older code in some files still does.
- NEVER write back a pre-suspend snapshot: `val s = _uiState.value` ... suspend
  call ... `_uiState.value = s.copy(...)` clobbers everything that landed during
  the suspend. Compute the async value first, then `update {}` with fields from
  the lambda's current state.
- When adding a collector to an existing ViewModel, do not copy its legacy
  write pattern; use `update {}` and convert wide-window writers you touch.

### Compose Stability Contract (NON-NEGOTIABLE)
`app/compose_stability_config.conf` declares four packages stable to the Compose
compiler: `data.model.**`, `data.local.entity.**`, `domain.model.**` and `ui.**`.
It also names single in-repo classes: `core.game.AchievementUi`,
`hardware.CompanionInGameState`, and services passed into composables (`RomMRepository`,
`SaveSyncRepository`, `SaveCacheManager`, `PlaySessionTracker`, `SnapshotSyncRouter`). A
named class keeps only fixed public references (val Flows and functions);
`scripts/ci/service_stability_checks.py` fails a public `var`, mutable collection or
snapshot state on one. Add an in-repo class only when it meets that shape. This is what keeps cold compiles at ~4 min instead of 1h+ (StabilityInferencer
recursion), but the compiler NO LONGER VERIFIES stability for covered classes -- we
promise it. A violation does not crash or warn; it silently skips recompositions and
the UI goes stale. Rules for ALL new/edited code in covered packages and classes:
- State/model data classes: `val`-only, including constructor params. Never add `var`.
- Collections in state are replaced via `copy(...)`, never mutated in place.
- Never read a plain `var`/non-State property of a ViewModel or delegate inside
  composition. UI-visible data reaches composables ONLY via `StateFlow`/`collectAsState`
  or params.
- A data class that genuinely needs mutable fields must live OUTSIDE `ui/`; a `var` or
  a `MutableList/Set/Map` in one under `ui/` fails `scripts/ci/stability_checks.py`, in
  the write-time hook and in CI.
- The whole of `ui/` is covered, `ui.primitives` included, so a new state class is
  stable wherever it is written. Writing one outside `ui/` re-opens the inference
  recursion that produces hour-long compiles.
- If the conf file is absent on the current branch, these rules are still the house
  style; they just aren't load-bearing yet.

### Input Handling (TV/Gamepad) - CRITICAL

#### InputHandler Interface

All controller input goes through `InputHandler` (`ui/input/InputHandler.kt`). This is the
actual interface -- every method has a default `UNHANDLED` body, so implementors override
only what they handle:

```kotlin
interface InputHandler {
    fun onUp(): InputResult = InputResult.UNHANDLED
    fun onDown(): InputResult = InputResult.UNHANDLED
    fun onLeft(): InputResult = InputResult.UNHANDLED
    fun onRight(): InputResult = InputResult.UNHANDLED
    fun onConfirm(): InputResult = InputResult.UNHANDLED
    fun onBack(): InputResult = InputResult.UNHANDLED
    fun onMenu(): InputResult = InputResult.UNHANDLED
    fun onSecondaryAction(): InputResult = InputResult.UNHANDLED
    fun onContextMenu(): InputResult = InputResult.UNHANDLED
    fun onPrevSection(): InputResult = InputResult.UNHANDLED
    fun onNextSection(): InputResult = InputResult.UNHANDLED
    fun onPrevTrigger(): InputResult = InputResult.UNHANDLED
    fun onNextTrigger(): InputResult = InputResult.UNHANDLED
    fun onSelect(): InputResult = InputResult.UNHANDLED
    fun onLeftStickClick(): InputResult = InputResult.UNHANDLED
    fun onRightStickClick(): InputResult = InputResult.UNHANDLED
    fun onRightStickUp(): InputResult = InputResult.UNHANDLED
    fun onRightStickDown(): InputResult = InputResult.UNHANDLED
    fun onRightStickLeft(): InputResult = InputResult.UNHANDLED
    fun onRightStickRight(): InputResult = InputResult.UNHANDLED
    fun onLongConfirm(): InputResult = InputResult.UNHANDLED
    fun onLongSelect(): InputResult = InputResult.UNHANDLED
}
```

#### Button Names and Swap Resolution

`InputButton` (`ui/components/FooterHint.kt`) uses INTENT names, not physical positions:

```kotlin
enum class InputButton {
    A, B, X, Y,
    DPAD, DPAD_UP, DPAD_DOWN, DPAD_LEFT, DPAD_RIGHT, DPAD_HORIZONTAL, DPAD_VERTICAL,
    LB, RB, LB_RB, LT, RT, LT_RT,
    RIGHT_STICK_VERTICAL,
    START, SELECT
}
```

There are NO position-named values (`SOUTH`/`EAST`/`WEST`/`NORTH` do not exist).
`InputButton.A` means "the confirm intent", wherever the user has mapped it. User button
swaps are resolved at exactly two points -- never by choosing a different enum value:

1. **Icon render:** `InputButton.toPainter()` reads `LocalABIconsSwapped` /
   `LocalXYIconsSwapped` / `LocalSwapStartSelect` and picks the physical glyph
   (e.g. `InputButton.A` draws `FaceRight` when AB is swapped).
2. **Keyevent dispatch:** `mapKeycodeToGamepadEvent(keyCode, swapAB, swapXY, swapStartSelect)`
   in `ui/input/GamepadInputHandler.kt` routes the physical keycode to the intended
   `GamepadEvent` (e.g. `KEYCODE_BUTTON_X` -> `SecondaryAction` when XY is swapped).

| InputButton | Handler Method | Typical Use |
|-------------|----------------|-------------|
| `A` | `onConfirm()` | Select/Confirm (never adjust) |
| `B` | `onBack()` | Back/Cancel |
| `X` | `onContextMenu()` | Full context menu |
| `Y` | `onSecondaryAction()` | Quick action (favorite, add) |

#### CRITICAL: Never Hardcode Button Names

**WRONG:** a string resource whose English names a button, or a `Text` that draws a
button letter as a hand-built hint. Both show the wrong button once the user swaps
them.

**CORRECT:** `FooterBar` with the `InputButton` enum and a resource label.
```kotlin
FooterBar(
    hints = listOf(
        InputButton.Y to stringResource(R.string.social_friends_hint_favorite)
    )
)
```

`FooterBar` takes `hints: List<Pair<InputButton, String>>`; each hint's icon resolves the
swap automatically via `toPainter()`. Labels come from `stringResource` with keys named
`<area>_<component>_<role>`.

#### Remove Compose Focus System

TV UI does NOT use Compose's built-in focus. Always use `clickableNoFocus`:

```kotlin
import com.nendo.argosy.ui.util.clickableNoFocus

Modifier.clickableNoFocus { onItemClick(index) }
Modifier.clickableNoFocus(enabled = isEnabled) { onItemClick(index) }
```

Plain `Modifier.clickable { }` is the wrong form. It enables TV focus and breaks
gamepad navigation.

**Why `clickableNoFocus`?**

Compose's `clickable()` enables TV focus by default, which conflicts with this app's manual InputHandler-based focus system. Using `clickable()` directly causes:
- Double focus visuals (Compose ring + manual glow/border)
- Unpredictable navigation when Compose and InputHandler fight for control
- Broken gamepad input when Compose steals focus

The `clickableNoFocus` extension (defined in `ui/util/Modifiers.kt`) disables Compose focus while preserving touch support.

**Focus Management:**
- Use `isFocused: Boolean` prop for visual focus state
- Manage focus index in ViewModel, not Compose focus system
- `Modifier.focusable()` belongs only to a surface's root key sink, one per rendered
  surface (`ArgosyApp` for control; the presentation branch of `ArgosyActivity` and
  `SecondaryHomeActivity`). Never add one to a row, tile or control
- `FocusRequester` is allowed ONLY for soft-keyboard text entry and those root key
  sinks. Using it to move selection between rows is the violation

**Material3 Components with Built-in Focus:**

Some Material3 components have built-in TV focus. Disable it:

```kotlin
Switch(
    checked = isEnabled,
    onCheckedChange = onToggle,
    modifier = Modifier.focusProperties { canFocus = false },
    interactionSource = remember { MutableInteractionSource() }
)
```

Other components that may need similar treatment: `Checkbox`, `RadioButton`, `Slider`.

#### Proper Index Calculation

Use `.mod()` for wrapping, NOT `%` operator:
```kotlin
val newIndex = (currentIndex + direction).mod(items.size)
```

`%` keeps the sign of the left operand, so `-1 % 5` is `-1`, not `4`, and wrapping
backwards past the first item breaks.

#### Dual Input Support (Touch + Controller)

Every interactive element needs BOTH. Touch goes through `clickableNoFocus`:
```kotlin
Modifier.clickableNoFocus { viewModel.selectItem(index) }
```

The controller goes through an `InputHandler` that moves a ViewModel-owned focus index:
```kotlin
class MyInputHandler(private val viewModel: MyViewModel) : InputHandler {
    override fun onConfirm(): InputResult {
        viewModel.selectItem(viewModel.uiState.value.focusedIndex)
        return InputResult.HANDLED
    }

    override fun onRight(): InputResult {
        viewModel.moveFocus(1)
        return InputResult.HANDLED
    }
}
```

The ViewModel owns the wrap:
```kotlin
fun moveFocus(delta: Int) {
    _uiState.update { state ->
        state.copy(focusedIndex = (state.focusedIndex + delta).mod(state.items.size))
    }
}
```

### Footer Hints
- Only show hints for controls available in current context
- Use `FooterBar` with `InputButton` enum - NEVER hardcode button letters
- Add hints for L1/R1 when shoulder buttons have actions

### Navigation
- Settings sections: Add to `SettingsSection` enum
- Navigation state in ViewModel, not Composable
- Use `navigateBack()` with proper parent section restoration

### Preferences
- New prefs do NOT go in `UserPreferencesRepository`. That file holds ZERO DataStore
  keys - it is a pure aggregator that combines seven domain repos
  (`displayPrefs`, `syncPrefs`, `controlsPrefs`, `storagePrefs`, `appPrefs`,
  `builtinPrefs`, `sessionPrefs`) into one `UserPreferences` flow.
- The chain is: DataStore key + default + getter/setter in the OWNING domain repo
  under `data/preferences/` (e.g. `DisplayPreferencesRepository`,
  `BuiltinEmulatorPreferencesRepository`) -> its own `Preferences` data class ->
  the `UserPreferences` aggregation -> settings state -> consumption site.
  Mirror the sibling key naming in the repo you are adding to; prefixes differ
  between repos.
- Use `companion object { fun fromString() }` pattern for enum persistence
- Keep display names in UI layer, not data layer

### Theming
- Use `CompositionLocal` for cross-cutting styling concerns
- Provide via `ALauncherTheme` wrapper
- Read via `LocalXxx.current` in Composables

### Glow Effects
- Use `drawIntoCanvas` with `BlurMaskFilter` for proper glow
- Example pattern from GameCard:
  ```kotlin
  Modifier.drawBehind {
      drawIntoCanvas { canvas ->
          val paint = Paint().apply { color = glowColor.copy(alpha = glowAlpha) }
          val frameworkPaint = paint.asFrameworkPaint().apply {
              maskFilter = android.graphics.BlurMaskFilter(glowRadius, BlurMaskFilter.Blur.NORMAL)
          }
          canvas.nativeCanvas.drawRoundRect(...)
      }
  }
  ```
- Do NOT use simple `drawRect` for glow - it won't blur

### Light/Dark Mode & Colors
- Check theme: `LocalLauncherTheme.current.isDarkTheme`
- Use `MaterialTheme.colorScheme` for context-appropriate colors:
  - `primary` / `onPrimary`: Accent elements and their text
  - `surface` / `onSurface`: Backgrounds and their text
  - `primaryContainer` / `onPrimaryContainer`: Focused/selected items
  - `surfaceVariant` / `onSurfaceVariant`: Secondary surfaces, muted text
- For overlays/scrims:
  - Dark mode: `Color.Black.copy(alpha = X)`
  - Light mode: `Color.White.copy(alpha = X)`
- Semantic colors via `LocalLauncherTheme.current.semanticColors`:
  - `warning`, `success`, `error` for status indicators
- V2 primitives read their surface and text ramp from `LocalArgosyTheme`
  (`ArgosyThemeTokens` in `ui/theme/ArgosyTokens.kt`)
- Never hardcode colors - always derive from theme. New color values go through the
  `design-tokens` skill

## Architecture Rules

These rules prevent the structural debt that required a major cleanup. Violating them creates problems that compound over time.

### Layer Boundaries (NON-NEGOTIABLE)

```
UI (ui/) --> Domain (domain/) --> Data (data/)
   ^              ^                  |
   |              |                  |
   +--------------+------------------+
     Dependencies flow inward only
```

- **Domain layer** (`domain/`): NO Compose imports. This half HOLDS in the tree and is a
  hard rule. The "no Android framework imports / pure Kotlin" half is LAW but currently
  **aspirational - known debt**; do not add new violations, and see the violator list below.
- **Data layer** (`data/`): NO Compose imports (`Color`, `ImageVector`, `Icons.*`). Use extension properties in `ui/common/` to attach UI concerns to domain types (see `CompletionStatusUi.kt` pattern).
- **UI layer** (`ui/`): NO direct DAO imports. Use repositories. LAW but currently
  **aspirational - known debt**; violator list below.

**Known debt: domain/ Android framework imports** (do not extend this list):
- `domain/usecase/game/LaunchGameUseCase.kt` (`android.content.Intent`)
- `domain/usecase/MigrateStorageUseCase.kt` (`android.util.Log`)
- `domain/usecase/MigratePlatformStorageUseCase.kt` (`android.util.Log`)
- `domain/usecase/PurgePlatformUseCase.kt` (`android.util.Log`)
- `domain/usecase/collection/GetCollectionsUseCase.kt` (`android.util.Log`)
- `domain/usecase/download/DownloadGameUseCase.kt` (`android.util.Log`)
- `domain/usecase/libretro/LibretroMigrationUseCase.kt` (`android.util.Log`)
- `domain/usecase/music/MeasureTrackLoudnessUseCase.kt` (`android.media.AudioFormat`, `MediaCodec`, `MediaExtractor`, `MediaFormat`, `android.util.Log`)
- `domain/usecase/save/RestoreCachedSaveUseCase.kt` (`android.util.Log`)
- `domain/usecase/state/GetUnifiedStatesUseCase.kt` (`android.util.Log`)
- `domain/usecase/state/PreLaunchStateSyncUseCase.kt` (`android.util.Log`)
- `domain/usecase/state/RestoreCachedStatesUseCase.kt` (`android.util.Log`)
- `domain/usecase/state/SyncStatesOnSessionEndUseCase.kt` (`android.util.Log`)

**Known debt: ui/ direct DAO use** (do not extend this list). It was built by
import-grep, so it under-reports: a fully-qualified type in a constructor
parameter or a reach through another ViewModel's public DAO field never shows up
in an import search. Before saying "this is not on the list", grep for `Dao` in
the file, not just the import block.
- `ui/screens/settings/SettingsViewModel.kt` (`SaveCacheDao`)
- `ui/screens/settings/delegates/BiosSettingsDelegate.kt` (`FirmwareDao`)
- `ui/screens/savesync/SaveSyncViewModel.kt` (`GameDao`, `PendingConflictDao`, `SaveSyncDao`)
- `ui/screens/gamedetail/GameDetailViewModel.kt` (`EmulatorConfigDao`, `GameDiscDao`, `GameFileDao`)
- `ui/screens/gamedetail/delegates/AchievementDelegate.kt` (`AchievementDao`)
- `ui/screens/gamedetail/delegates/PerGameSettingsDelegate.kt` (`EmulatorConfigDao`)
- `ui/screens/gamedetail/delegates/SaveManagementDelegate.kt` (`EmulatorSaveConfigDao`, `SaveSyncDao`)
- `ui/ArgosyViewModel.kt` (`PendingConflictDao`, fully-qualified constructor param)
- `ui/screens/gamedetail/delegates/DownloadDelegate.kt` (`GameFileDao`, fully-qualified constructor param)
- `ui/screens/settings/SettingsInitRouter.kt` (`vm.saveCacheDao.countNeedingRemoteSync()`, reached through SettingsViewModel)

### Repository Pattern (NON-NEGOTIABLE)

ViewModels and delegates in `ui/` MUST access data through repositories, never DAOs directly.

| DAO | Repository | Notes |
|-----|-----------|-------|
| `GameDao` | `GameRepository` | Available everywhere, including the companion (via `DualScreenManagerHolder`) |
| `PlatformDao` | `PlatformRepository` | Simple, works everywhere |
| `CollectionDao` | `CollectionRepository` | Simple, works everywhere |

There is no dual-screen DAO exception. Remaining direct DAO use in `ui/` is tracked in the
known-debt list above; do not add to it.

When adding new DAO methods that UI needs: add the method to the repository, not the ViewModel.

### Data Layer Change Checklist
When modifying DAOs, entities, foreign keys, or database queries:
1. Find ALL entities with FK constraints to affected tables
2. Check for existing migration methods (e.g., `migratePlatform`)
3. Verify operation order: create before reference, delete after dereference
4. Test with existing user data, not just fresh installs

### File Size Limits

| Type | Soft Limit | Action |
|------|-----------|--------|
| ViewModel | ~500 lines | Extract delegates (see `GameDetailViewModel` + `delegates/`) |
| Activity | ~500 lines | Extract managers/helpers (see `SecondaryHomeActivity`) |
| Repository | ~300 lines | Extract service classes (see `SaveSyncRepository` + services) |
| Composable file | ~400 lines | Extract private sub-composables |
| Preferences repo | ~300 lines | Split by domain (see 7 domain repos under `data/preferences/`) |

**Decomposition patterns used in this codebase:**
- **Delegates**: ViewModel logic split by feature area (`DownloadDelegate`, `SaveManagementDelegate`)
- **Routers**: ViewModel method routing by category (`SettingsGeneralRouter`, `SettingsBuiltinRouter`)
- **Services**: Repository logic split by concern (`SaveSyncOrchestrator`, `SaveSyncApiClient`)
- **Facade**: Original class becomes thin facade, delegates to extracted classes

### LazyList Keys (NON-NEGOTIABLE)

Every `items()`, `itemsIndexed()`, and `LazyVerticalGrid` call MUST have a `key` parameter with a stable, unique identifier:

```kotlin
itemsIndexed(games, key = { _, game -> game.id }) { index, game -> ... }
items(apps.size, key = { apps[it].packageName }) { index -> ... }
```

A call without `key`, such as `itemsIndexed(games) { index, game -> ... }`, causes
unnecessary recomposition.

### Compose Performance

- Use `derivedStateOf` for values computed from state that don't need to trigger recomposition on every state change
- Avoid `.chunked()`, `.map()`, `.filter()` chains inside composable functions without `remember`/`derivedStateOf` -- they allocate on every recomposition
- Use `remember(key) { computation }` for expensive calculations

### Don't Copy-Paste Patterns

Before duplicating code, check for existing shared utilities:

| Pattern | Shared Utility | Location |
|---------|---------------|----------|
| Long-press scale animation | `rememberLongPressAnimationState`, `Modifier.longPressGraphicsLayer`, `Modifier.longPressGesture` | `ui/common/LongPressAnimation.kt` |
| GameEntity -> UI model | Check existing `toUi()` extensions | Model files or `*Mapper.kt` |
| PlatformEntity -> UI model | `toHomePlatformUi()` | `ui/screens/home/HomeModels.kt` |
| Modal dialogs | `Modal`, `CenteredModal` | `ui/components/` |
| Gradient extraction | `GradientColorExtractor` | `ui/common/GradientColorExtractor.kt` |
| Completion status icons/colors | Extension properties | `ui/common/CompletionStatusUi.kt` |

### Dual-Screen (Companion) Dependencies

The companion (dual-screen secondary display) runs in the SAME process as the launcher.
There is no `:companion` process and no companion ViewModel. `SecondaryHomeActivity`
(`hardware/SecondaryHomeActivity.kt`) reads every dependency from
`DualScreenManagerHolder.instance`, for example `dsm.preferencesRepository`,
`dsm.imageCacheManager` and `dsm.sessionStateStore`. Composables under `ui/dualscreen/`
reach the same instance (see `PresentOnCompanion.kt`).
- Any dependency exposed on `DualScreenManager` is available to the companion, including
  `GameRepository`
- `ArgosyActivity` constructs `DualScreenManager` by hand, passing its own injected fields
- To give the companion a new dependency, add the constructor parameter to
  `DualScreenManager` and pass it at the construction site in `ArgosyActivity`

## Completion Criteria

A feature is NOT done until all of these are true:

### Mandatory (blocking)
- [ ] Touch input works (`clickableNoFocus` on every interactive element)
- [ ] Controller input works (InputHandler with D-pad + A/B buttons, ViewModel-owned focus index)
- [ ] No plain `clickable` anywhere; `clickableNoFocus` removes the Compose focus ring and indication
- [ ] Index wrapping uses `.mod()` not `%`
- [ ] Footer hints updated (non-obvious hints only)
- [ ] Scrolling collections of repeated items use LazyColumn/LazyRow/LazyVerticalGrid
- [ ] Error handling is explicit (no silent failures)
- [ ] Every user-facing string is a resource id; no label doubles as a stored value
- [ ] Builds without errors

### Required
- [ ] Follows existing patterns (or documents why not)
- [ ] State managed in ViewModel, not locally (unless truly local)
- [ ] Focus state is visual prop (`isFocused`), not Compose focus
- [ ] Colors from theme, not hardcoded (works in light AND dark mode)
- [ ] No duplicate code that could be extracted
