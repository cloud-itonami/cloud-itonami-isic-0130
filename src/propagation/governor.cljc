(ns propagation.governor
  "NurseryOperationsGovernor — independent censor for propagation batch
  operations. Enforces HARD checks (immediate hold, no override) and
  SOFT checks (escalate for human approval). All operations are :propose
  only; no direct technique execution or treatment decisions pass through.

  HARD holds:
    1. batch-not-registered? — batch-id must exist in SSoT
    2. health-concern-unescalated? — any flagged health concern blocks
       further work until explicitly escalated
    3. low-confidence? — proposals below 0.7 confidence escalate
    4. supply-order-high-cost? — orders above 2000 units escalate

  Proposals that survive all HARD checks still reach :request-approval
  for human sign-off when their nature demands it (high-stakes op, or
  low-confidence recheck)."
  (:require [propagation.registry :as registry]
            [propagation.facts :as facts]))

(defn batch-not-registered-violations
  "HARD: batch must be registered before any operation."
  [request _context _proposal store]
  (let [batch-id (get-in request [:subject :batch-id])]
    (when-not (registry/batch-exists? store batch-id)
      {:type :batch-not-registered
       :description (str "Batch " batch-id " not in nursery registry")
       :severity :hard})))

(defn health-concern-unescalated-violations
  "HARD: any open health concern on the batch blocks coordination ops.
  (Health flags must be explicitly escalated; they don't auto-resolve.)"
  [request _context _proposal store]
  (let [batch-id (get-in request [:subject :batch-id])
        batch (get @(:batches store) batch-id)]
    (when batch
      (let [open-concerns (filter (fn [concern]
                                    (not (:escalated? concern)))
                                  (:pending-concerns batch))]
        (when (seq open-concerns)
          {:type :health-concern-unescalated
           :description (str "Batch " batch-id " has "
                             (count open-concerns)
                             " unescalated health concern(s)")
           :severity :hard
           :concerns open-concerns})))))

(defn low-confidence-violations
  "SOFT: proposals with confidence < 0.7 are flagged for escalation."
  [_request _context proposal _store]
  (let [confidence (get-in proposal [:metadata :confidence] 1.0)]
    (when (< confidence 0.7)
      {:type :low-confidence
       :description (str "Proposal confidence " confidence " below threshold 0.7")
       :severity :soft})))

(defn supply-order-high-cost-violations
  "SOFT: supply orders >= 2000 units escalate for approval."
  [request _context proposal _store]
  (when (= :order-supplies (:op request))
    (let [cost (get-in proposal [:value :estimated-cost] 0)]
      (when (>= cost 2000)
        {:type :supply-order-high-cost
         :description (str "Supply order cost " cost " exceeds threshold 2000")
         :severity :soft}))))

(defn check
  "Runs all governor checks. Returns a verdict map:
   {:passed? true | false
    :hard-holds [violations...] -- immediate hold
    :soft-flags [violations...] -- escalate for approval
    :cites [reasons...]}"
  [request context proposal store]
  (let [hard-1 (batch-not-registered-violations request context proposal store)
        hard-2 (health-concern-unescalated-violations request context proposal store)
        soft-1 (low-confidence-violations request context proposal store)
        soft-2 (supply-order-high-cost-violations request context proposal store)
        hard-holds (filter some? [hard-1 hard-2])
        soft-flags (filter some? [soft-1 soft-2])]
    {:passed? (and (empty? hard-holds))
     :hard-holds hard-holds
     :soft-flags soft-flags
     :cites [(str "op=" (:op request))
             (str "batch=" (get-in request [:subject :batch-id]))]}))

(defn hold-fact
  "Audit fact when a governor check holds an operation."
  [request _context verdict]
  {:t :held
   :op (:op request)
   :subject (get-in request [:subject :batch-id])
   :disposition :hold
   :holds (:hard-holds verdict)
   :flags (:soft-flags verdict)})
