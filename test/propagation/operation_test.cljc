(ns propagation.operation-test
  (:require [clojure.test :refer [deftest is testing]]
            [propagation.operation :as op]
            [propagation.store :as store]
            [propagation.propagationadvisor :as advisor]))

(deftest log-batch-commits
  (testing "Logging a valid batch commits successfully in phase 2"
    (let [st (store/mem-store)
          adv (advisor/mock-advisor)
          actor (op/build st {:advisor adv})
          batch {:batch-id "BATCH-001"
                 :species "Solanum lycopersicum"
                 :growth-stage :seedling
                 :quantity 500}
          result (op/execute actor
                             {:op :log-batch-record :subject batch}
                             {:actor-id "test" :phase 2})]
      (is (= :commit (:disposition result)))
      (is (seq (:audit result))))))

(deftest high-cost-order-escalates
  (testing "High-cost supply order escalates for approval"
    (let [st (store/mem-store)
          batch {:batch-id "B1" :pending-concerns []}
          _ (store/upsert-batch st batch)
          adv (advisor/mock-advisor)
          actor (op/build st {:advisor adv})
          result (op/execute actor
                             {:op :order-supplies
                              :subject {:batch-id "B1"
                                        :item "heater"
                                        :quantity 1
                                        :estimated-cost 3500}}
                             {:actor-id "test" :phase 2})]
      (is (= :escalate (:disposition result))))))

(deftest phase-0-escalates-most
  (testing "Phase 0 (demo) escalates most operations"
    (let [st (store/mem-store)
          batch {:batch-id "B1" :pending-concerns []}
          _ (store/upsert-batch st batch)
          adv (advisor/mock-advisor)
          actor (op/build st {:advisor adv})
          result (op/execute actor
                             {:op :log-batch-record
                              :subject batch}
                             {:actor-id "test" :phase 0})]
      (is (= :escalate (:disposition result)))
      (is (= :demo-phase (:reason result))))))

(deftest audit-trail-recorded
  (testing "All operations record audit facts"
    (let [st (store/mem-store)
          batch {:batch-id "B1" :pending-concerns []}
          _ (store/upsert-batch st batch)
          adv (advisor/mock-advisor)
          actor (op/build st {:advisor adv})
          _ (op/execute actor
                        {:op :log-batch-record :subject batch}
                        {:actor-id "test" :phase 2})]
      (is (seq (store/audit-log st))))))
