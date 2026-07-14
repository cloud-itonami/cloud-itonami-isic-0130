(ns propagation.sim
  "Demo / simulation driver for the propagation-batch operations actor.
  Walks through a sample scenario: logging a batch, scheduling operations,
  flagging a health concern, and placing a supply order."
  (:require [propagation.facts :as facts]
            [propagation.store :as store]
            [propagation.operation :as op]
            [propagation.propagationadvisor :as advisor]))

(defn run
  "Run the demo scenario."
  []
  (println "========== Propagation Operations Actor Demo ==========\n")

  (let [st (store/mem-store)
        adv (advisor/mock-advisor)
        actor (op/build st {:advisor adv})
        ctx {:actor-id "demo-actor" :phase 2}]

    ;; Scenario 1: Log a new batch
    (println "1. Logging new batch (Tomato seedlings)...")
    (let [batch-1 {:batch-id "BATCH-001"
                   :species "Solanum lycopersicum"
                   :growth-stage :seedling
                   :quantity 500}
          result (op/execute actor
                             {:op :log-batch-record :subject batch-1}
                             ctx)]
      (println "   Result:" (:disposition result))
      (println "   Audit:" (take 2 (:audit result))))

    ;; Scenario 2: Schedule a field operation
    (println "\n2. Scheduling transplant operation...")
    (let [result (op/execute actor
                             {:op :schedule-field-operation
                              :subject {:batch-id "BATCH-001"
                                        :operation :transplant
                                        :scheduled-date "2026-07-20"}}
                             ctx)]
      (println "   Result:" (:disposition result)))

    ;; Scenario 3: Flag a health concern (always escalates)
    (println "\n3. Flagging health concern...")
    (let [result (op/execute actor
                             {:op :flag-plant-health-concern
                              :subject {:batch-id "BATCH-001"
                                        :concern-type :leaf-spot
                                        :description "Brown spots on 15% of leaves"}}
                             ctx)]
      (println "   Result:" (:disposition result))
      (println "   Reason:" (:reason result)))

    ;; Scenario 4: Order supplies (low cost)
    (println "\n4. Ordering supplies (within threshold)...")
    (let [result (op/execute actor
                             {:op :order-supplies
                              :subject {:batch-id "BATCH-001"
                                        :item "potting-soil"
                                        :quantity 100
                                        :estimated-cost 1500}}
                             ctx)]
      (println "   Result:" (:disposition result)))

    ;; Scenario 5: High-cost supply order (escalates)
    (println "\n5. Ordering high-cost supplies...")
    (let [result (op/execute actor
                             {:op :order-supplies
                              :subject {:batch-id "BATCH-001"
                                        :item "greenhouse-heater"
                                        :quantity 1
                                        :estimated-cost 3500}}
                             ctx)]
      (println "   Result:" (:disposition result))
      (println "   Reason:" (:reason result)))

    ;; Print audit log
    (println "\n========== Audit Log ==========")
    (doseq [entry (store/audit-log st)]
      (println entry))

    (println "\n✓ Demo complete")))

#?(:clj
   (defn -main [& _args]
     (run)))
