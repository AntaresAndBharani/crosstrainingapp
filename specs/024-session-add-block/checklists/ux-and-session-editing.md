# UX & Session Editing Requirements Quality Checklist: Unified Add Block Workflow (`SPEC-024`)

**Purpose**: Validate completeness, clarity, consistency, and edge-case coverage of UX, state, and persistence requirements for adding blocks in `Edit Session` and `Log Session`.  
**Created**: 2026-11-15  
**Feature**: [spec.md](file:///c:/Users/rogal/workspaces/ws-gym/crosstrainingapp/specs/024-session-add-block/spec.md)

**Note**: Reviewer-owned requirements-quality review artifact (`Depth: Standard`, `Audience: Reviewer (PR)`, `Focus: Session UX Flows & State Safety`).

## Requirement Completeness & Routing Clarity

- [x] CHK001 Are the exact entry triggers (`+ Add Block` and `Load Block ▾`) and initial tab selection rules unambiguously specified for both empty/exhausted library states and unloaded-library states? [Completeness, Clarity, Spec §FR-001, §FR-002]
- [x] CHK002 Are selection semantics inside `Saved Blocks` explicitly specified so already-loaded blocks cannot be pre-counted or re-added silently? [Clarity, Spec §FR-003]
- [x] CHK003 Is the distinction between originally hydrated edited-session blocks (hidden pre-fill hint) and newly appended saved blocks in `Edit Session` (pre-fill + visible `"Pre-filled from last session ({Date})"` hint) explicitly defined? [Consistency, Spec §FR-004]
- [x] CHK004 Are default title resolution rules and zero-chip title-only movement resolution rules clearly specified for `Custom Block` creation? [Completeness, Spec §FR-005, §FR-006]

## Persistence, State Safety & Coexistence

- [x] CHK005 Are persistence boundaries between session-only custom blocks (`id = 0L`, zero `Routine` rows) and optional library-saved blocks (`Save to Available Blocks library` toggle, default OFF) specified without ambiguity? [Completeness, Spec §FR-007]
- [x] CHK006 Are post-addition viewport visibility requirements (auto-scrolling to newly appended blocks across Saved, Custom, and Quick Parse additions) specified? [Completeness, Spec §FR-008]
- [x] CHK007 Are dirty tracking (`BackHandler` discard guard), existing session title preservation in `Edit Session`, and re-hydration suppression while the Add Block sheet is open explicitly defined? [Coverage, Spec §FR-009]
- [x] CHK008 Are residual legacy block deletion rules (`canRemove` when simplified blocks exist) defined consistently for mixed workouts? [Consistency, Spec §FR-010]

## Responsive UX, Accessibility & Compatibility

- [x] CHK009 Are keyboard/IME clearance (`imePadding()`), minimum touch target sizing (`>= 48.dp`), `360dp` / `fontScale = 1.4f` resilience, and merged TalkBack semantics for the library-save toggle specified? [Non-Functional, Spec §FR-011]
- [x] CHK010 Are database schema (`v10`), security rules (`firestore.rules`), and deterministic test coverage boundaries documented clearly? [Constraints, Spec §FR-012, Plan §Constitution Check]

## Notes

- Audited `spec.md` and `plan.md` against all `CHK001..CHK010` items; zero gaps or contradictions identified (`10/10` satisfied).
