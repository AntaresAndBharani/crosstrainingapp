# Architecture & Tech Stack Guidelines

## 1. Clean Architecture & Layering
- **UI Layer (`com.fractanomics.crosstraining.ui`):**
  - Jetpack Compose screens and reusable components.
  - ViewModels orchestrate UI state and handle user events. No direct database or network access in UI.
- **Data Layer (`com.fractanomics.crosstraining.data`):**
  - **Local:** Room database, DAOs, Entities.
  - **Remote:** Firebase Firestore, Authentication, Google Credential Manager.
  - **Repository Pattern:** Expose domain models via `Flow` or `suspend` functions.
- **Utility Layer (`com.fractanomics.crosstraining.util`):**
  - Pure helper functions, extensions, formatters, and math/time calculations.

## 2. Kotlin & Coroutines Standards
- Target: Kotlin JVM 17.
- Use structured concurrency (`viewModelScope`, `coroutineScope`).
- Avoid `GlobalScope` or blocking `runBlocking` in production code.
- Prefer immutable data structures (`List`, `val`, `data class`).
