# cloud-itonami-isic-0130: Plant Propagation Operations Coordination

Open Business Blueprint for ISIC Rev.5 0130 (plant propagation) — a
nursery-operations coordination actor (Propagation Advisor ⊣
NurseryOperationsGovernor) with append-only audit ledger, `clj`/`cljs`
portable code, and phase-gated rollout.

## What this actor does

Coordinates a plant-propagation nursery's back-office workflow:
- **Log batches** — register new seed/seedling batches to the nursery registry
- **Schedule operations** — propose transplant/hardening schedules (advisory only)
- **Flag health concerns** — surface disease/pest anomalies (always escalates)
- **Order supplies** — propose seed/media/input procurement (cost-gated escalation)

## What this actor does NOT do

- Execute propagation techniques (rooting, hardening, vernalization)
- Make plant-health treatment decisions (fungicide, pesticide, nutrient)
- Directly control nursery equipment or labor dispatch

These remain the responsibility of qualified propagators and agronomists.
This actor *surfaces and escalates* concerns; humans decide actions.

## Core architecture

- **PropagationAdvisor** — sealed LLM node making proposals (`:effect :propose` only)
- **NurseryOperationsGovernor** — independent censor applying three HARD checks
  (batch registration, unescalated health concerns, low confidence) and one SOFT
  flag (high-cost supply orders)
- **Phase gates** — Phase 0/1 (demo/pilot) escalate most operations; Phase 2/3
  (supervised/full) commit if governor approves
- **Store protocol** — MemStore (dev/test) and DatomicStore (future) backing
- **Audit ledger** — every operation logs advisor trace, governor verdict, and
  commit fact

See `docs/adr/0001-architecture.md` for full design.

## Getting started

```bash
# Build
clj -M:dev:compile

# Test
clj -M:dev:test

# Run demo
clj -M:dev:run

# Lint
clj -M:lint
```

## Operations reference

### `:log-batch-record`
Log a new propagation batch to the nursery registry.

**Request:**
```clojure
{:op :log-batch-record
 :subject {:batch-id "BATCH-001"
           :species "Solanum lycopersicum"
           :growth-stage :seedling
           :quantity 500}}
```

**Governor checks:** batch-not-registered? (must be new id), batch-record-complete? (all fields)

**Typical disposition:** `:commit` (phase 2+) or `:escalate` (phase 0/1)

### `:schedule-field-operation`
Propose a field operation (transplant, hardening).

**Request:**
```clojure
{:op :schedule-field-operation
 :subject {:batch-id "BATCH-001"
           :operation :transplant
           :scheduled-date "2026-07-20"}}
```

**Governor checks:** batch-not-registered?, health-concern-unescalated?

**Typical disposition:** `:commit` (phase 2+ if governor clean) or `:hold` (if
batch unregistered)

### `:flag-plant-health-concern`
Escalate a health concern (disease, pest anomaly) for human review. Always
escalates; no low-confidence option.

**Request:**
```clojure
{:op :flag-plant-health-concern
 :subject {:batch-id "BATCH-001"
           :concern-type :leaf-spot
           :description "Brown spots on 15% of leaves"}}
```

**Governor checks:** batch-not-registered?

**Typical disposition:** `:escalate` (always human approval; no auto-commit)

### `:order-supplies`
Propose seed/media/input procurement.

**Request:**
```clojure
{:op :order-supplies
 :subject {:batch-id "BATCH-001"
           :item "potting-soil"
           :quantity 100
           :estimated-cost 1500}}
```

**Governor checks:** batch-not-registered?, low-cost or high-cost soft flag

**Typical disposition:** `:commit` (cost < 2000) or `:escalate` (cost ≥ 2000,
requires approval)

## HARD invariants

1. **Batch must be registered.** All operations require the batch-id to exist in
   the nursery registry (prevents orphaned/ghost batches).

2. **Health concerns must be explicitly escalated.** A batch with flagged
   concerns blocks further work until those concerns are reviewed and marked
   resolved. (Prevents silent problems.)

3. **No direct technique/treatment execution.** The actor never proposes or
   commits to rootin, hardening, or treatment operations. These remain human
   decisions.

4. **All operations are `:propose` only.** The advisor never commits directly;
   all proposals flow through the governor and phase gate.

## Audit trail

Every operation records:
- **:advised** — advisor proposal (confidence, summary, reasoning)
- **:held** — governor hold (violation details)
- **:committed** — operation committed to store (basis, summary)

Query the store's audit log to trace any operation's full decision chain.

## Testing

```bash
clj -M:dev:test
```

Runs 7 tests covering:
- Governor HARD/SOFT logic
- Operation execution and audit trail
- Phase gate behavior
- High/low cost supply order escalation

## Production deployment

For production use, replace `MemStore` with `DatomicStore` (langchain.db)
and configure phase gating (Phase 0→3 rollout). See kotoba-server docs.

## License

AGPL-3.0-or-later
