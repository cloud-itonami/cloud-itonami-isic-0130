(ns propagation.propagationadvisor
  "PropagationAdvisor — the sealed LLM node that makes proposals for
  propagation-nursery coordination ops. This is a mock advisor for now;
  real LLM integration is a future concern.

  Proposal shape:
    {:op :operation-name
     :value {...details...}
     :effect :propose
     :confidence 0.0-1.0
     :summary \"Human-readable summary\"
     :cites [\"reason 1\" \"reason 2\"...]
     :metadata {:...}}

  All proposals use :effect :propose only. The governor and phase gate
  decide whether to escalate or commit.")

(defprotocol Advisor
  (-advise [this store request] "Make a proposal for the given request"))

(defrecord MockAdvisor []
  Advisor
  (-advise [_this store request]
    (let [op (:op request)]
      (case op
        :log-batch-record
        {:op :log-batch-record
         :value {:batch-id (get-in request [:subject :batch-id])
                 :species (get-in request [:subject :species])
                 :growth-stage (get-in request [:subject :growth-stage])
                 :quantity (get-in request [:subject :quantity])}
         :effect :propose
         :confidence 0.95
         :summary "Log propagation batch to registry"
         :cites ["batch provenance verified"]
         :metadata {:source :mock :model :mock-advisor}}

        :schedule-field-operation
        {:op :schedule-field-operation
         :value {:batch-id (get-in request [:subject :batch-id])
                 :operation (get-in request [:subject :operation])
                 :scheduled-date (get-in request [:subject :scheduled-date])}
         :effect :propose
         :confidence 0.80
         :summary "Schedule field operation"
         :cites ["batch registered" "schedule available"]
         :metadata {:source :mock}}

        :flag-plant-health-concern
        {:op :flag-plant-health-concern
         :value {:batch-id (get-in request [:subject :batch-id])
                 :concern-type (get-in request [:subject :concern-type])
                 :description (get-in request [:subject :description])}
         :effect :propose
         :confidence 1.0
         :summary "Escalate health concern (always human approval)"
         :cites ["health concern flagged for escalation"]
         :metadata {:source :mock}}

        :order-supplies
        {:op :order-supplies
         :value {:item (get-in request [:subject :item])
                 :quantity (get-in request [:subject :quantity])
                 :estimated-cost (get-in request [:subject :estimated-cost])}
         :effect :propose
         :confidence 0.85
         :summary "Order supplies for batch"
         :cites ["supply inventory checked"]
         :metadata {:source :mock}}

        ;; default
        {:op op
         :value {}
         :effect :propose
         :confidence 0.5
         :summary "Operation proposed"
         :cites ["default proposal"]
         :metadata {:source :mock :op op}}))))

(defn mock-advisor
  "Returns a mock advisor for testing."
  []
  (MockAdvisor.))

(defn trace
  "Audit trace of advisor reasoning."
  [request proposal]
  {:t :advised
   :op (:op request)
   :subject (get-in request [:subject :batch-id])
   :confidence (get-in proposal [:metadata :confidence] 1.0)
   :summary (:summary proposal)})
