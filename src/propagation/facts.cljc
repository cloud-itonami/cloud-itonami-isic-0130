(ns propagation.facts
  "Nursery batch record facts and predicates for plant-propagation
  operations coordination. A propagation batch is a named collection of
  plants at a specific growth stage, with registered provenance,
  environmental conditions, and propagation schedule. The actor never
  makes health-treatment or direct-technique decisions; it only
  coordinates and logs the nursery's back-office workflow: batch
  recording, scheduling field operations, escalating health concerns,
  and ordering supplies.")

(defn batch-registered?
  "True if the batch record is verified/registered in the nursery system."
  [batch]
  (boolean (:batch-id batch)))

(defn growth-stage-valid?
  "True if the growth stage is one of the known propagation stages."
  [stage]
  (contains? #{:seed :seedling :transplant :hardening :ready}
             stage))

(defn batch-record-complete?
  "True if the batch has all required fields for a valid record."
  [batch]
  (and (:batch-id batch)
       (:species batch)
       (:growth-stage batch)
       (:quantity batch)
       (growth-stage-valid? (:growth-stage batch))
       (pos? (:quantity batch))))

(defn propagation-batch
  "Constructs a new propagation batch record with provenance and
  environmental metadata."
  [batch-id species growth-stage quantity & opts]
  (merge {:batch-id batch-id
          :species species
          :growth-stage growth-stage
          :quantity quantity
          :created-at (js/Date.)
          :health-status :normal
          :pending-concerns []}
         (first opts)))

(defn batch-with-health-flag
  "Returns batch with health concern flagged for escalation."
  [batch concern-type description]
  (assoc batch :pending-concerns
         (conj (:pending-concerns batch)
               {:concern-type concern-type
                :description description
                :flagged-at (js/Date.)
                :escalated? false})))
