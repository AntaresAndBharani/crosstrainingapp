# MCP Communication & Sync Requirements Quality Checklist: Reliable Multi-Order & Active-Cycle Communication

**Purpose**: Reviewer-level requirements quality validation ("Unit Tests for English") across MCP batching, active-cycle resolution, tombstone sync, and transport reliability
**Created**: 2026-11-10
**Feature**: [spec.md](../spec.md) | [plan.md](../plan.md) | [contracts/mcp-tools.md](../contracts/mcp-tools.md)

## Requirement Completeness

- [x] CHK001 Are batch mutation requirements explicitly specified for cycle goals (including simultaneous upserts, rep-scheme conversions, and deletions) as well as exercises, personal records, weight entries, and daily logs? [Completeness, Spec §FR-001]
- [x] CHK002 Are dedicated cycle discovery operations (`get_active_cycle` and `list_cycles`) and default active-cycle targeting requirements defined for all cycle-scoped mutations? [Completeness, Spec §FR-005, §FR-007]
- [x] CHK003 Are safety annotation requirements (`readOnly`, `destructive`, `idempotent`, `openWorld`) documented across every tool category? [Completeness, Spec §FR-003, Contract §Tool Catalog]

## Requirement Clarity

- [x] CHK004 Is the precedence order for resolving the active cycle (explicit mobile `isActiveCycle` marker → highest non-deleted `cycleId` with recency tie-breaker → `1` for empty accounts) unambiguously specified? [Clarity, Spec §FR-006, Data Model §CycleSummary]
- [x] CHK005 Are strict AI client schema compatibility rules quantified with concrete structural prohibitions (`0` `$ref` pointers, `0` `exclusiveMinimum` keywords, `^[a-z0-9_]{1,60}$` tool names)? [Clarity, Spec §FR-004]

## Requirement Consistency

- [x] CHK006 Do the mobile synchronization active-cycle stamping (`FR-009`) and tombstone deletion rules (`FR-010`) align with Constitution Principle V without requiring local-only schedule bitmask sync or `firestore.rules` document-ID changes? [Consistency, Spec §FR-009, §FR-010, Assumptions]
- [x] CHK007 Are LLM batching guidance requirements (`FR-002`) consistent between server-level instructions and individual tool descriptions? [Consistency, Spec §FR-002, Plan §Summary]

## Acceptance Criteria & Measurability

- [x] CHK008 Are multi-order consolidation (`1` tool call, `< 3s` warm latency) and first-turn active-cycle accuracy (`100%`) quantified in `SC-001` and `SC-002`? [Measurability, Spec §SC-001, §SC-002]
- [x] CHK009 Is concurrent write safety quantified with a concrete concurrency target (`10` concurrent mutations with `0` lost updates)? [Measurability, Spec §SC-004]

## Scenario & Edge Case Coverage

- [x] CHK010 Is single-step rep-max conversion (e.g., `2RM Snatch` → `3RM Snatch`) addressed without leaving orphan goals or resurrected rows after mobile sync? [Edge Case, Spec §Edge Cases, §SC-005]
- [x] CHK011 Is duplicate new-exercise creation within the same batch request (case-insensitive deduplication) explicitly specified? [Edge Case, Spec §Edge Cases]
- [x] CHK012 Are empty-account cycle initialization (`Cycle 1`) and idempotent deletion of non-existent records defined? [Edge Case, Spec §Edge Cases]

## Non-Functional & Transport Requirements

- [x] CHK013 Are HTTP transport compliance rules (`Allow: POST, DELETE` on `405 GET /mcp`, `Mcp-Protocol-Version` CORS exposure, and JSON `Accept` header normalization) specified? [Non-Functional, Spec §FR-012, Contract §HTTP Transport]
- [x] CHK014 Is rate-limit isolation defined separately for authenticated user identities versus shared AI proxy egress IPs (`66.249.83.*`)? [Non-Functional, Spec §FR-013, Contract §Rate Limiting]

## Notes

- All 14 requirement-quality checks (`CHK001`–`CHK014`) audited against `spec.md`, `plan.md`, `data-model.md`, and `contracts/mcp-tools.md` and verified satisfied.
