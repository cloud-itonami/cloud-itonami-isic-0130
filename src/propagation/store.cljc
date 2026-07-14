(ns propagation.store
  "Store protocol — single source of truth (SSoT) for propagation batches
  and audit records. Implemented by MemStore (atom-backed, dev/test) and
  DatomicStore (langchain.db-backed, production).")

(defprotocol Store
  (fetch-batch [store batch-id] "Retrieve a batch by ID")
  (upsert-batch [store batch] "Insert or update a batch")
  (append-audit [store fact] "Append an audit trail fact")
  (audit-log [store] "Retrieve full audit log"))

(defrecord MemStore [batches audit]
  Store
  (fetch-batch [_this batch-id]
    (get @batches batch-id))

  (upsert-batch [_this batch]
    (swap! batches assoc (:batch-id batch) batch)
    batch)

  (append-audit [_this fact]
    (swap! audit conj fact))

  (audit-log [_this]
    @audit))

(defn mem-store
  "Create an in-memory Store for dev/test."
  []
  (MemStore. (atom {}) (atom [])))

(defn write-ops
  "Names of all write operations this actor can perform."
  []
  #{:log-batch-record
    :schedule-field-operation
    :flag-plant-health-concern
    :order-supplies})
