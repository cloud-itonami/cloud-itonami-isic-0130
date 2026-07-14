(ns propagation.phase
  "Phase-based rollout gates for propagation operations. Each phase
  (0 = demo, 1 = internal pilot, 2 = supervised ops, 3 = full) gates
  which operations can commit vs. escalate.")

(def default-phase 0)

(def phase-names
  {0 :demo
   1 :pilot
   2 :supervised
   3 :full})

(defn verdict->disposition
  "Converts governor verdict into base disposition (no phase override yet).
  HARD holds -> :hold. Empty soft-flags -> :commit. Soft-flags -> :escalate."
  [verdict]
  (cond
    (seq (:hard-holds verdict)) :hold
    (seq (:soft-flags verdict)) :escalate
    :else :commit))

(defn gate
  "Applies phase-based gating to a base disposition. Phase 0/1 escalate
  almost everything; phase 2 commits if governor approves; phase 3 is
  full autonomy (no additional gatekeeping)."
  [phase request base-disposition]
  (case phase
    0 (if (= base-disposition :hold)
        {:disposition :hold :reason :demo-phase}
        {:disposition :escalate :reason :demo-phase})

    1 (if (= base-disposition :hold)
        {:disposition :hold :reason :pilot-phase}
        {:disposition :escalate :reason :pilot-phase})

    2 (case base-disposition
        :hold {:disposition :hold :reason :governor-hold}
        :escalate {:disposition :escalate :reason :governor-escalate}
        :commit {:disposition :commit})

    3 (case base-disposition
        :hold {:disposition :hold :reason :governor-hold}
        :escalate {:disposition :escalate :reason :governor-escalate}
        :commit {:disposition :commit})

    ;; default: conservative -- escalate
    {:disposition :escalate :reason :unknown-phase}))
