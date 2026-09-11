(ns propagation.governor-test
  (:require [clojure.test :refer [deftest is testing]]
            [propagation.governor :as governor]))

(def ^:private now-ms #?(:clj (System/currentTimeMillis) :cljs (.now js/Date)))
(def ^:private ten-days-ago (- now-ms (* 10 24 60 60 1000)))
(def ^:private hundred-days-ago (- now-ms (* 100 24 60 60 1000)))

(def ^:private clean-batch
  {:propagation-method :cutting/softwood
   :jurisdiction :jp/maff
   :rooting-percent 70.0
   :hardening-days 20
   :pest-or-disease-detected? false
   :last-phytosanitary-inspection-date ten-days-ago
   :sanitation-score 85
   :propagation-sources [:rose/own-root-cutting]
   :declared-fidelity-checks #{}
   :evidence-checklist [:batch-intake-record :propagation-method-log :rooting-rate-test
                        :genetic-fidelity-check :phytosanitary-inspection :hardening-log]})

;; ──────────────────────── Hard Violations ──────────────────────

(deftest spec-basis-violation-test
  (testing "proposal with no jurisdiction citation is a hard violation"
    (let [req {:op :log-propagation-batch :subject "batch-001"}
          prop {:cites [] :value {:jurisdiction nil}}
          result (governor/check req {:actor-id "gov-1"} prop {})]
      (is (true? (:hard? result)))
      (is (some #(= (:rule %) :no-spec-basis) (:violations result)))))

  (testing "proposal with proper citation passes spec basis check"
    (let [batch-id "batch-001"
          store {:batches {batch-id clean-batch}}
          req {:op :log-propagation-batch :subject batch-id}
          prop {:cites [{:spec "ISO-12345"}] :value {:jurisdiction :jp/maff}}
          result (governor/check req {:actor-id "gov-1"} prop store)]
      (is (false? (:hard? result))))))

;; ──────────────────────── Rooting Rate Violations ──────────────────────

(deftest rooting-rate-violation-test
  (testing "batch with rooting rate below the propagation method's minimum triggers hard violation"
    (let [batch-id "batch-001"
          store {:batches {batch-id (assoc clean-batch :rooting-percent 50.0)}}
          req {:op :log-propagation-batch :subject batch-id}
          prop {:cites [{:spec "ISO-12345"}] :value {:jurisdiction :jp/maff} :confidence 0.8}
          result (governor/check req {:actor-id "gov-1"} prop store)]
      (is (true? (:hard? result)))
      (is (some #(= (:rule %) :rooting-rate-below-minimum) (:violations result)))))

  (testing "hardwood cutting has a lower rooting floor than softwood cutting"
    (let [batch-id "batch-002"
          store {:batches {batch-id (assoc clean-batch
                                            :propagation-method :cutting/hardwood
                                            :hardening-days 25
                                            :rooting-percent 55.0)}}
          req {:op :log-propagation-batch :subject batch-id}
          prop {:cites [{:spec "ISO-12345"}] :value {:jurisdiction :jp/maff} :confidence 0.8}
          result (governor/check req {:actor-id "gov-1"} prop store)]
      (is (false? (:hard? result))))))

;; ──────────────────────── Hardening Period Violations ──────────────────────

(deftest hardening-period-violation-test
  (testing "batch with hardening period below the propagation method's minimum triggers hard violation"
    (let [batch-id "batch-001"
          store {:batches {batch-id (assoc clean-batch :hardening-days 5)}}
          req {:op :log-propagation-batch :subject batch-id}
          prop {:cites [{:spec "ISO-12345"}] :value {:jurisdiction :jp/maff} :confidence 0.8}
          result (governor/check req {:actor-id "gov-1"} prop store)]
      (is (true? (:hard? result)))
      (is (some #(= (:rule %) :hardening-period-incomplete) (:violations result))))))

;; ──────────────────────── Genetic-Fidelity Violations ──────────────────────

(deftest genetic-fidelity-risk-violation-test
  (testing "batch with undeclared genetic-fidelity risk triggers hard violation"
    (let [batch-id "batch-001"
          store {:batches {batch-id (assoc clean-batch
                                            :propagation-sources [:rose/hybrid-tea-grafted]
                                            :declared-fidelity-checks #{})}}
          req {:op :log-propagation-batch :subject batch-id}
          prop {:cites [{:spec "ISO-12345"}] :value {:jurisdiction :jp/maff} :confidence 0.8}
          result (governor/check req {:actor-id "gov-1"} prop store)]
      (is (true? (:hard? result)))
      (is (some #(= (:rule %) :genetic-fidelity-risk) (:violations result))))))

;; ──────────────────────── Pest / Disease Violations ──────────────────────

(deftest pest-or-disease-violation-test
  (testing "batch with detected pest or pathogen triggers hard violation"
    (let [batch-id "batch-001"
          store {:batches {batch-id (assoc clean-batch :pest-or-disease-detected? true)}}
          req {:op :log-propagation-batch :subject batch-id}
          prop {:cites [{:spec "ISO-12345"}] :value {:jurisdiction :jp/maff} :confidence 0.8}
          result (governor/check req {:actor-id "gov-1"} prop store)]
      (is (true? (:hard? result)))
      (is (some #(= (:rule %) :pest-or-disease-detected) (:violations result))))))

;; ──────────────────────── Phytosanitary Inspection Violations ──────────────────────

(deftest phytosanitary-inspection-overdue-violation-test
  (testing "batch with overdue phytosanitary inspection triggers hard violation"
    (let [batch-id "batch-001"
          store {:batches {batch-id (assoc clean-batch :last-phytosanitary-inspection-date hundred-days-ago)}}
          req {:op :log-propagation-batch :subject batch-id}
          prop {:cites [{:spec "ISO-12345"}] :value {:jurisdiction :jp/maff} :confidence 0.8}
          result (governor/check req {:actor-id "gov-1"} prop store)]
      (is (true? (:hard? result)))
      (is (some #(= (:rule %) :phytosanitary-inspection-overdue) (:violations result))))))

;; ──────────────────────── Sanitation Score Violations ──────────────────────

(deftest sanitation-score-violation-test
  (testing "batch with insufficient sanitation score triggers hard violation"
    (let [batch-id "batch-001"
          store {:batches {batch-id (assoc clean-batch :sanitation-score 60)}}
          req {:op :log-propagation-batch :subject batch-id}
          prop {:cites [{:spec "ISO-12345"}] :value {:jurisdiction :jp/maff} :confidence 0.8}
          result (governor/check req {:actor-id "gov-1"} prop store)]
      (is (true? (:hard? result)))
      (is (some #(= (:rule %) :sanitation-score-insufficient) (:violations result))))))

;; ──────────────────────── Quarantine Flag Violations ──────────────────────

(deftest quarantine-flag-unresolved-violation-test
  (testing "batch with an unresolved quarantine flag triggers hard violation"
    (let [batch-id "batch-001"
          store {:batches {batch-id (assoc clean-batch
                                            :quarantine-flag-raised? true
                                            :quarantine-flag-resolved? false)}}
          req {:op :log-propagation-batch :subject batch-id}
          prop {:cites [{:spec "ISO-12345"}] :value {:jurisdiction :jp/maff} :confidence 0.8}
          result (governor/check req {:actor-id "gov-1"} prop store)]
      (is (true? (:hard? result)))
      (is (some #(= (:rule %) :quarantine-flag-unresolved) (:violations result)))))

  (testing "batch with a resolved quarantine flag does not trigger this rule"
    (let [batch-id "batch-002"
          store {:batches {batch-id (assoc clean-batch
                                            :quarantine-flag-raised? true
                                            :quarantine-flag-resolved? true)}}
          req {:op :log-propagation-batch :subject batch-id}
          prop {:cites [{:spec "ISO-12345"}] :value {:jurisdiction :jp/maff} :confidence 0.8}
          result (governor/check req {:actor-id "gov-1"} prop store)]
      (is (not (some #(= (:rule %) :quarantine-flag-unresolved) (:violations result)))))))

;; ──────────────────────── Escalation (Low Confidence) ──────────────────────

(deftest low-confidence-escalation-test
  (testing "low confidence proposal escalates even when hard checks pass"
    (let [batch-id "batch-001"
          store {:batches {batch-id clean-batch}}
          req {:op :schedule-maintenance :subject batch-id}
          prop {:cites [{:spec "ISO-12345"}] :value {:jurisdiction :jp/maff} :confidence 0.5}
          result (governor/check req {:actor-id "gov-1"} prop store)]
      (is (false? (:ok? result)))
      (is (true? (:escalate? result)))
      (is (false? (:hard? result))))))

;; ──────────────────────── High Stakes Escalation ──────────────────────

(deftest high-stakes-escalation-test
  (testing "log-propagation-batch escalates even when all checks pass"
    (let [batch-id "batch-001"
          store {:batches {batch-id clean-batch}}
          req {:op :log-propagation-batch :subject batch-id}
          prop {:cites [{:spec "ISO-12345"}] :value {:jurisdiction :jp/maff} :confidence 0.95}
          result (governor/check req {:actor-id "gov-1"} prop store)]
      (is (false? (:ok? result)))
      (is (true? (:escalate? result)))
      (is (false? (:hard? result))))))

;; ──────────────────────── Batch Not Registered Violation ──────────────────────

(deftest batch-not-registered-violation-test
  (testing "any op against an unregistered batch triggers hard violation (registration required before any action)"
    (let [store {:batches {}}
          req {:op :schedule-maintenance :subject "batch-999"}
          prop {:cites [{:spec "Equipment-Manual"}] :value {} :effect :propose :confidence 0.9}
          result (governor/check req {:actor-id "gov-1"} prop store)]
      (is (true? (:hard? result)))
      (is (some #(= (:rule %) :batch-not-registered) (:violations result))))))

;; ──────────────────────── Already Logged Violation ──────────────────────

(deftest already-logged-violation-test
  (testing "batch already logged triggers hard violation"
    (let [batch-id "batch-001"
          store {:batches {batch-id
                           {:propagation-method :cutting/softwood
                            :logged? true}}}
          req {:op :log-propagation-batch :subject batch-id}
          prop {:cites [{:spec "ISO-12345"}] :value {:jurisdiction :jp/maff} :confidence 0.8}
          result (governor/check req {:actor-id "gov-1"} prop store)]
      (is (true? (:hard? result)))
      (is (some #(= (:rule %) :already-logged) (:violations result))))))

;; ──────────────────────── Already Shipment Finalized Violation ──────────────────────

(deftest already-shipment-finalized-violation-test
  (testing "batch with an already-finalized shipment triggers hard violation"
    (let [batch-id "batch-001"
          store {:batches {batch-id (assoc clean-batch :shipment-finalized? true)}}
          req {:op :coordinate-shipment :subject batch-id}
          prop {:cites [{:spec "ISO-12345"}] :value {:jurisdiction :jp/maff} :confidence 0.9}
          result (governor/check req {:actor-id "gov-1"} prop store)]
      (is (true? (:hard? result)))
      (is (some #(= (:rule %) :already-shipment-finalized) (:violations result))))))
