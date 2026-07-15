(ns propagation.store
  "Store abstraction for plant-propagation-nursery batches. Current
  implementation operates on plain data (`{:batches {batch-id batch-map}
  :facts [...]}`); production should migrate this seam to Datomic/
  kotoba-server (the same seam point all cloud-itonami actors use) while
  keeping the same pure-function surface.

  A propagation batch is the minimal unit of work: one cutting/graft/
  tissue-culture/seedling-sowing run of a plant lineage, tracked from
  intake through propagation, rooting/establishment, hardening-off, and
  shipment. Representative batch keys:
    - :propagation-method keyword propagation-method id (see
      `propagation.facts/propagation-method-types`)
    - :jurisdiction keyword jurisdiction id (see
      `propagation.facts/jurisdictions`)
    - :rooting-percent finished-batch actual rooting/take/germination rate
    - :hardening-days actual number of days spent in hardening-off
    - :pest-or-disease-detected? true if laboratory/visual screening
      flagged a pest or pathogen
    - :sanitation-score 0-100 facility hygiene/cross-contamination-control
      score
    - :last-phytosanitary-inspection-date epoch-ms of the batch's most
      recent phytosanitary inspection
    - :propagation-sources cultivar/rootstock/clone lineage ids processed
      for this batch
    - :declared-fidelity-checks set of declared genetic-fidelity-check
      keywords
    - :evidence-checklist evidence items present for the batch
    - :quarantine-flag-raised? / :quarantine-flag-resolved? open
      phytosanitary quarantine flag
    - :logged? true once a `:log-propagation-batch` proposal commits
    - :shipment-finalized? true once a `:coordinate-shipment` proposal
      commits

  The ledger (`:facts`) is a separate append-only vector of audit facts,
  kept alongside `:batches` in the same store value.")

(defn nursery-batch
  "Retrieve a batch by id, or nil if it does not exist / is not yet
  registered."
  [st batch-id]
  (get-in st [:batches batch-id]))

(defn batch-already-logged?
  "True only if the batch exists and has already been marked logged."
  [st batch-id]
  (true? (:logged? (nursery-batch st batch-id))))

(defn batch-shipment-finalized?
  "True only if the batch exists and its shipment has already been
  finalized."
  [st batch-id]
  (true? (:shipment-finalized? (nursery-batch st batch-id))))

(defn log-batch
  "Register/update `batch-data` under `batch-id` and mark it logged
  (one-way flag). Used once a `:log-propagation-batch` proposal commits."
  [st batch-id batch-data]
  (assoc-in st [:batches batch-id] (assoc batch-data :logged? true)))

(defn finalize-shipment
  "Mark an existing batch's shipment as finalized (one-way flag). Used
  once a `:coordinate-shipment` proposal commits."
  [st batch-id]
  (assoc-in st [:batches batch-id :shipment-finalized?] true))

(defn audit-trail
  "Return the append-only audit ledger (empty vector if none yet)."
  [st]
  (get st :facts []))

(defn append-fact
  "Append `fact` to the store's audit ledger."
  [st fact]
  (update st :facts (fnil conj []) fact))
