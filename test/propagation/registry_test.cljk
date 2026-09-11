(ns propagation.registry-test
  (:require [clojure.test :refer [deftest is testing]]
            [propagation.registry :as registry]))

;; ──────────────────────── Rooting Rate ──────────────────────

(deftest rooting-rate-below-minimum-test
  (testing "rate above minimum returns false (no violation)"
    (is (false? (registry/rooting-rate-below-minimum? 75 60))))

  (testing "rate at minimum returns false"
    (is (false? (registry/rooting-rate-below-minimum? 60 60))))

  (testing "rate below minimum returns true (violation)"
    (is (true? (registry/rooting-rate-below-minimum? 45 60)))))

;; ──────────────────────── Hardening Period ──────────────────────

(deftest hardening-period-incomplete-test
  (testing "days above minimum returns false (no violation)"
    (is (false? (registry/hardening-period-incomplete? 21 14))))

  (testing "days at minimum returns false"
    (is (false? (registry/hardening-period-incomplete? 14 14))))

  (testing "days below minimum returns true (violation)"
    (is (true? (registry/hardening-period-incomplete? 7 14)))))

;; ──────────────────────── Genetic-Fidelity Risk ──────────────────────

(deftest genetic-fidelity-risk-test
  (testing "declared risks match formulation returns false (no risk)"
    (let [formula #{:off-type-risk :somaclonal-variation-risk}
          declared #{:off-type-risk :somaclonal-variation-risk}]
      (is (false? (registry/genetic-fidelity-risk? formula declared)))))

  (testing "declared risks exceed formulation returns false (conservative)"
    (let [formula #{:off-type-risk}
          declared #{:off-type-risk :somaclonal-variation-risk}]
      (is (false? (registry/genetic-fidelity-risk? formula declared)))))

  (testing "formulation risk undeclared returns true (risk)"
    (let [formula #{:off-type-risk :somaclonal-variation-risk}
          declared #{:off-type-risk}]
      (is (true? (registry/genetic-fidelity-risk? formula declared))))))

;; ──────────────────────── Pest / Disease Detection ──────────────────────

(deftest pest-or-disease-detected-test
  (testing "no detection returns false"
    (is (false? (registry/pest-or-disease-detected? false)))
    (is (false? (registry/pest-or-disease-detected? nil))))

  (testing "detection returns true"
    (is (true? (registry/pest-or-disease-detected? true)))))

;; ──────────────────────── Phytosanitary Inspection ──────────────────────

(deftest phytosanitary-inspection-overdue-test
  (testing "recent inspection returns false (no violation)"
    ;; Assume inspected 10 days ago
    (let [now #?(:clj (System/currentTimeMillis) :cljs (.now js/Date))
          ten-days-ago (- now (* 10 24 60 60 1000))]
      (is (false? (registry/phytosanitary-inspection-overdue? ten-days-ago now)))))

  (testing "overdue inspection returns true (violation)"
    (let [now #?(:clj (System/currentTimeMillis) :cljs (.now js/Date))
          hundred-days-ago (- now (* 100 24 60 60 1000))]
      (is (true? (registry/phytosanitary-inspection-overdue? hundred-days-ago now))))))

;; ──────────────────────── Sanitation Score ──────────────────────

(deftest sanitation-score-insufficient-test
  (testing "score at minimum returns false (no violation)"
    (is (false? (registry/sanitation-score-insufficient? 75 75))))

  (testing "score above minimum returns false"
    (is (false? (registry/sanitation-score-insufficient? 85 75))))

  (testing "score below minimum returns true (violation)"
    (is (true? (registry/sanitation-score-insufficient? 74 75)))))
