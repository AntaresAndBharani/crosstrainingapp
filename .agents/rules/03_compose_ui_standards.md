# Jetpack Compose & UI Design Standards

## 1. State Hoisting & Statelessness
- All screen-level composables must be separated into:
  1. **Stateful Route:** Injects ViewModel, collects `StateFlow` via `collectAsStateWithLifecycle()`, and handles navigation callbacks.
  2. **Stateless Screen:** Pure composable receiving `(uiState: ScreenUiState, onAction: (ScreenAction) -> Unit)`.

## 2. Compose Performance & Recomposition
- Use `@Immutable` or `@Stable` data classes for UI states.
- Avoid instantiating new lambdas or calculating heavy data directly inside the `@Composable` body without `remember`.
- Use `derivedStateOf` when observing rapidly changing states (e.g. scroll offsets, timers).

## 3. Design System & Theming
- Strict adherence to Material 3 tokens (`MaterialTheme.colorScheme`, `MaterialTheme.typography`).
- Never hardcode raw hex colors or fixed pixel dimensions; use `dp`, `sp`, and theme colors.
