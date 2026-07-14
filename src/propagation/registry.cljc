(ns propagation.registry
  "Named predicates and checkers used by the governor to gate operations.
  Each predicate names a domain rule: registered batches, cost thresholds,
  confidence criteria. The governor always applies these against the SSoT
  (Store) before committing.")

(defn batch-exists?
  "True if batch-id is registered in the SSoT."
  [store batch-id]
  (contains? @(:batches store) batch-id))

(defn supply-order-within-threshold?
  "True if the order cost is below the escalation threshold (2000 base units).
  Supply orders above this amount always escalate for approval."
  [cost-estimate]
  (< cost-estimate 2000))

(defn confidence-sufficient?
  "True if proposal confidence meets minimum threshold (0.7).
  Low-confidence proposals are always escalated."
  [confidence]
  (and confidence (>= confidence 0.7)))

(defn all-pending-concerns-noted?
  "True if all health concerns on the batch have been reviewed.
  Any unreviewed concern blocks scheduling/supply decisions."
  [batch]
  (every? (fn [concern]
            (contains? concern :reviewed-at))
          (:pending-concerns batch)))
