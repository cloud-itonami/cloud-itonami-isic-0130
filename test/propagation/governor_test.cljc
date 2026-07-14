(ns propagation.governor-test
  (:require [clojure.test :refer [deftest is testing]]
            [propagation.governor :as governor]
            [propagation.store :as store]))

(deftest batch-not-registered-hold
  (testing "HARD hold when batch not in registry"
    (let [st (store/mem-store)
          req {:op :log-batch-record
               :subject {:batch-id "UNKNOWN"}}
          verdict (governor/check req {} {} st)]
      (is (not (:passed? verdict)))
      (is (seq (:hard-holds verdict))))))

(deftest health-concern-unescalated-hold
  (testing "HARD hold when batch has unescalated health concerns"
    (let [st (store/mem-store)
          batch {:batch-id "B1" :pending-concerns [{:escalated? false}]}
          _ (store/upsert-batch st batch)
          req {:op :log-batch-record :subject {:batch-id "B1"}}
          verdict (governor/check req {} {} st)]
      (is (not (:passed? verdict)))
      (is (seq (:hard-holds verdict))))))

(deftest low-confidence-soft-flag
  (testing "SOFT flag for low-confidence proposals"
    (let [st (store/mem-store)
          proposal {:metadata {:confidence 0.5}}
          verdict (governor/check {} {} proposal st)]
      (is (:passed? verdict))
      (is (seq (:soft-flags verdict))))))

(deftest high-cost-supply-order-soft-flag
  (testing "SOFT flag for supply orders >= 2000"
    (let [st (store/mem-store)
          req {:op :order-supplies
               :subject {:batch-id "B1"}}
          proposal {:value {:estimated-cost 2500}}
          verdict (governor/check req {} proposal st)]
      (is (:passed? verdict))
      (is (seq (:soft-flags verdict))))))

(deftest low-cost-supply-order-passes
  (testing "Low-cost supply order passes without soft flags"
    (let [st (store/mem-store)
          batch {:batch-id "B1" :pending-concerns []}
          _ (store/upsert-batch st batch)
          req {:op :order-supplies
               :subject {:batch-id "B1"}}
          proposal {:metadata {:confidence 0.9}
                    :value {:estimated-cost 1500}}
          verdict (governor/check req {} proposal st)]
      (is (:passed? verdict))
      (is (empty? (:soft-flags verdict))))))
