# cloud-itonami-isic-0130: Plant Propagation Nursery Operations Coordination Actor

**ISIC Rev. 5 0130** — Plant Propagation

A distributed actor for autonomous, compliant coordination of plant-propagation-nursery operations: propagation-source intake → propagation (cuttings, grafting, tissue culture, seed sowing) → rooting/establishment → hardening-off → rooting-rate/genetic-fidelity/phytosanitary inspection → nursery-stock shipment logistics. Sealed LLM advisor; independent Governor enforcement; append-only audit ledger. **Not equipment control.** Greenhouse climate-control, irrigation/misting systems, and grafting/cutting tool operation remain exclusive to licensed nursery staff and regulators.

## Scope

This actor coordinates **facility-operations workflow** for nurseries that propagate plants **for sale or transplant** (cuttings, tissue-culture plantlets, grafted stock, seedlings raised specifically to be propagated onward) -- this is what distinguishes it from growing-to-harvest of a specific commodity crop (e.g. ISIC 0111/0121):
- Propagation batch logging (propagation-method parameters, rooting/take/germination-rate results, evidence checklist)
- Equipment maintenance scheduling (greenhouse climate control, irrigation/misting systems, propagation benches)
- Quality concern escalation (low rooting rate, genetic-fidelity mismatch, pest/pathogen detection)
- Nursery-stock shipment coordination

**Out of scope:**
- Direct greenhouse/irrigation/propagation-equipment control (nursery staff exclusive)
- Phytosanitary-certification authority (human inspector/regulator only)
- Regulatory interpretation (proposals cite jurisdiction specifications; the Governor enforces only published requirements)

## Design

### Governor (Independent Compliance Layer)

The Governor is the separation-of-powers enforcement. It never trusts the advisor's confidence for anything viability- or compliance-relevant, and it always wins over the advisor.

- **Hard HOLD** (un-overridable):
  - Operation outside the closed allowlist (`:op-not-allowed`) — includes any proposal that would touch greenhouse/irrigation/propagation-equipment control or phytosanitary-certification authority
  - Proposal asserting an `:effect` other than `:propose` (`:effect-not-propose`)
  - Nursery/batch record not independently verified/registered before any action (`:batch-not-registered`)
  - No jurisdiction citation (`:no-spec-basis`) — can't verify requirements without one
  - Evidence checklist incomplete (`:evidence-incomplete`)
  - Rooting/take/germination rate below the propagation method's minimum required rate (`:rooting-rate-below-minimum`)
  - Hardening-off period incomplete (`:hardening-period-incomplete`)
  - Genetic-fidelity risk undeclared — off-type / somaclonal-variation mislabeling (`:genetic-fidelity-risk`)
  - Pest or pathogen detected on the batch's own screening (`:pest-or-disease-detected`)
  - Phytosanitary inspection overdue (`:phytosanitary-inspection-overdue`)
  - Facility sanitation/cross-contamination-control score insufficient (`:sanitation-score-insufficient`)
  - Unresolved quarantine flag (`:quarantine-flag-unresolved`)
  - Batch already logged / shipment already finalized (double-commit guards)
- **Escalate** (human sign-off always required):
  - `:log-propagation-batch` / `:coordinate-shipment` — real actuation events, always require nursery-operator sign-off even when the Governor is otherwise clean
  - `:flag-quality-concern` — a quality concern (low rooting rate, pest/pathogen detection, genetic-fidelity mismatch) is never auto-resolved by advisor confidence alone
  - Low advisor confidence (below `governor/confidence-floor`, 0.6)
- **Commit** (advisor proposal approved; Governor clean; not a mandatory-escalation op):
  - Routine, low-stakes proposals only — in this actor's current allowlist that is effectively `:schedule-maintenance` when clean

### Operations (Proposals)

Closed allowlist — the advisor may **only** ever propose these four operation types, all `:effect :propose`:

- **`:log-propagation-batch`** — Log a cutting/graft/tissue-culture/seed-sown batch's provenance, propagation-method parameters, and rooting/take/germination-rate results into nursery records (always requires human sign-off)
- **`:schedule-maintenance`** — Propose greenhouse/irrigation/propagation-equipment maintenance (routine, low risk)
- **`:flag-quality-concern`** — Surface a quality concern (e.g. low rooting rate, genetic-fidelity mismatch, pest/pathogen detection); always escalates
- **`:coordinate-shipment`** — Finalize shipment of nursery stock (always requires human sign-off)

Any proposal for an operation outside this allowlist — most importantly anything that would amount to direct greenhouse/irrigation/propagation-equipment control, or phytosanitary-certification authority — is refused unconditionally by the Governor (`:op-not-allowed`), regardless of advisor confidence.

## Testing

```bash
# Run full test suite
clojure -M:test

# Check code quality
clojure -M:lint

# Run demo simulation
clojure -M:run
```

## Standalone Use

This repo is **forkable outside the workspace**. If cloning standalone (not in the kotoba-lang monorepo), override `:local/root` paths in `deps.edn`:

```clojure
{:deps {io.github.kotoba-lang/langchain {:git/url "https://github.com/kotoba-lang/langchain" :git/tag "v0.1.0"}
        io.github.kotoba-lang/langgraph {:git/url "https://github.com/kotoba-lang/langgraph" :git/tag "v0.1.0"}}}
```

## License

AGPL-3.0-or-later. Forking/contribution welcome; see `CONTRIBUTING.md`.

## Security

Report security issues to the issue tracker or private disclosure; see `SECURITY.md`.

---

Part of **cloud-itonami**: autonomous actor fleet for regulated industries. See [github.com/cloud-itonami](https://github.com/cloud-itonami).
