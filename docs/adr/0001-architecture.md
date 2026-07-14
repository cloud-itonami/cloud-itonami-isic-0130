# ADR-0001: Nursery Operations Governor architecture

## Status

Accepted. `cloud-itonami-isic-0130` is implemented as a plant-propagation
back-office coordination actor, following the established langgraph-clj
StateGraph + independent Governor + Phase 0→3 rollout pattern.

## Context

`cloud-itonami-isic-0130` is ISIC Rev.5 class "Plant propagation" — a
nursery operation that coordinates seed-to-seedling-to-hardening workflow
for plant growers. The blueprint's scope is OPERATIONS COORDINATION only
(batch logging, scheduling, escalation), NOT propagation technique or
plant-health decisions. Those remain the responsibility of qualified
propagators and agronomists; this actor surfaces and escalates concerns,
never directs action.

Unlike energy or water operations (which have high-stakes real-world
actuations — battery dispatch, tariff settlement), propagation has no
physical actuations: all operations are `:propose` only, gated for human
approval when high-stakes or low-confidence.

## Decision

### Decision 1: Operations Coordination scope, not technique execution

The actor coordinates four proposal operations:
- `:log-batch-record` — new batch registration (no capital/crop risk)
- `:schedule-field-operation` — propagation/transplanting schedule (always
  advisory, human-controlled)
- `:flag-plant-health-concern` — surface disease/pest anomaly (always
  escalates; no treatment authority)
- `:order-supplies` — seed/media/input procurement (advisory, cost-gated)

HARD invariant: no operation can execute propagation technique (rooting,
hardening, vernalization) or apply treatment (fungicide, pesticide,
nutrient). These remain outside the actor's scope.

### Decision 2: HARD checks for registration and escalation

Three HARD holds (governor checks 1–3):
1. **batch-not-registered** — batch-id must exist in nursery registry
   (prevents orphaned records)
2. **health-concern-unescalated** — any flagged health concern blocks
   further work until explicitly escalated (prevents silent problems)
3. **low-confidence** — proposals below 0.7 confidence escalate (default
   cautious posture)

SOFT flags:
- **supply-order-high-cost** — orders ≥ 2000 units escalate for approval
  (domain threshold; configurable per nursery)

### Decision 3: Phase gates match existing pattern

Phase 0 (demo): escalate everything except HARD holds  
Phase 1 (pilot): escalate everything except HARD holds  
Phase 2 (supervised): commit if governor approves, escalate on soft flags  
Phase 3 (full): commit if governor approves  

Follows the pattern `cloud-itonami-isic-3512` (energy) established.

### Decision 4: Batch entity, audit trail discipline

Primary entity is a `batch`:
- `:batch-id` (unique, immutable key)
- `:species` (plant name)
- `:growth-stage` (seed, seedling, transplant, hardening, ready)
- `:quantity` (plant count)
- `:pending-concerns` (health flags, each with escalation status)

Operations never directly mutate the batch; proposals are always gated and
audited. Committed operations record a fact to the audit log, then upsert
the batch.

### Decision 5: Store protocol, MemStore + future Datomic

`propagation.store/Store` protocol with MemStore (atom-backed, dev/test)
reference implementation. DatomicStore (langchain.db-backed) is a future
seam, matching pattern across this fleet.

## Consequences

(+) Scope is tight and clear: coordination only, not agronomic authority.
(+) HARD checks prevent silent registration gaps and unescalated health
    concerns.
(+) All operations are `:propose` only; commits require human approval or
    phase-3 autonomy, matching "no direct technique/treatment execution"
    invariant.
(+) Audit trail is complete: every operation is logged with advisor trace,
    governor verdict, and commit fact.
(+) The pattern is proven: reuses the same langgraph-clj + Governor +
    Phase shape as energy, water, insurance, and other implemented actors.
(-) No real-time health monitoring or proactive anomaly detection — the
    actor responds to escalated concerns, not predicts them.
(-) Supply-order cost threshold (2000) is a placeholder; will need to be
    tuned per nursery/region.

## Verification

- `cloud-itonami-isic-0130`: `clojure -M:dev:test` green (7 tests),
  `clojure -M:lint` clean, `clojure -M:dev:run` demo walks four proposal
  scenarios with expected escalations, no exceptions.
- Commit hash: embedded in superproject ADR-2607141200 (registry update).
