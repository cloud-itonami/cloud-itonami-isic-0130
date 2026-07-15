(ns propagation.store-test
  (:require [clojure.test :refer [deftest is testing]]
            [propagation.store :as store]))

;; ──────────────────────── Batch Retrieval ──────────────────────

(deftest nursery-batch-test
  (testing "retrieve an existing batch"
    (let [batch-data {:propagation-method :cutting/softwood :rooting-percent 65.0}
          st {:batches {"batch-001" batch-data}}
          result (store/nursery-batch st "batch-001")]
      (is (= result batch-data))))

  (testing "nonexistent batch returns nil"
    (let [st {:batches {}}
          result (store/nursery-batch st "nonexistent")]
      (is (nil? result)))))

;; ──────────────────────── Batch Status Checks ──────────────────────

(deftest batch-already-logged-test
  (testing "logged batch is detected"
    (let [st {:batches {"batch-001" {:logged? true}}}
          result (store/batch-already-logged? st "batch-001")]
      (is (true? result))))

  (testing "unlogged batch returns false"
    (let [st {:batches {"batch-001" {:logged? false}}}
          result (store/batch-already-logged? st "batch-001")]
      (is (false? result))))

  (testing "nonexistent batch returns false"
    (let [st {:batches {}}
          result (store/batch-already-logged? st "batch-001")]
      (is (false? result)))))

(deftest batch-shipment-finalized-test
  (testing "finalized shipment is detected"
    (let [st {:batches {"batch-001" {:shipment-finalized? true}}}
          result (store/batch-shipment-finalized? st "batch-001")]
      (is (true? result))))

  (testing "non-finalized shipment returns false"
    (let [st {:batches {"batch-001" {:shipment-finalized? false}}}
          result (store/batch-shipment-finalized? st "batch-001")]
      (is (false? result)))))

;; ──────────────────────── Batch Registration ──────────────────────

(deftest log-batch-test
  (testing "logging a batch marks it as logged"
    (let [st {:batches {}}
          batch-data {:propagation-method :cutting/softwood}
          result (store/log-batch st "batch-001" batch-data)]
      (is (true? (get-in result [:batches "batch-001" :logged?])))))

  (testing "logging preserves batch data"
    (let [st {:batches {}}
          batch-data {:propagation-method :cutting/softwood :rooting-percent 65.0}
          result (store/log-batch st "batch-001" batch-data)]
      (is (= (:propagation-method (get-in result [:batches "batch-001"])) :cutting/softwood))
      (is (= (:rooting-percent (get-in result [:batches "batch-001"])) 65.0)))))

;; ──────────────────────── Shipment Finalization ──────────────────────

(deftest finalize-shipment-test
  (testing "finalizing a batch marks it as finalized"
    (let [st {:batches {"batch-001" {:propagation-method :cutting/softwood}}}
          result (store/finalize-shipment st "batch-001")]
      (is (true? (get-in result [:batches "batch-001" :shipment-finalized?]))))))

;; ──────────────────────── Audit Trail ──────────────────────

(deftest audit-trail-test
  (testing "audit trail is initially empty"
    (let [st {:facts []}
          result (store/audit-trail st)]
      (is (empty? result))))

  (testing "appended facts appear in audit trail"
    (let [st {:facts []}
          fact1 {:t :test-fact :detail "test 1"}
          fact2 {:t :test-fact :detail "test 2"}
          st' (store/append-fact st fact1)
          st'' (store/append-fact st' fact2)
          result (store/audit-trail st'')]
      (is (= (count result) 2))
      (is (= (first result) fact1))
      (is (= (second result) fact2)))))

(deftest append-fact-test
  (testing "appending a fact increases ledger length"
    (let [st {:facts []}
          fact {:t :governor-hold :op :log-propagation-batch}
          result (store/append-fact st fact)]
      (is (= (count (:facts result)) 1))
      (is (= (first (:facts result)) fact)))))
