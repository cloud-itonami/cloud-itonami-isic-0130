(ns propagation.facts-test
  (:require [clojure.test :refer [deftest is testing]]
            [propagation.facts :as facts]))

;; ──────────────────────── Propagation Method Lookups ──────────────────────

(deftest propagation-method-by-id-test
  (testing "softwood cutting propagation method exists"
    (let [p (facts/propagation-method-by-id :cutting/softwood)]
      (is (some? p))
      (is (= (:id p) :cutting/softwood))
      (is (= (:rooting-min-percent p) 60.0))
      (is (= (:hardening-min-days p) 14))))

  (testing "tissue-culture micropropagation method exists"
    (let [p (facts/propagation-method-by-id :tissue-culture/micropropagation)]
      (is (some? p))
      (is (= (:rooting-min-percent p) 85.0))
      (is (true? (:genetic-fidelity-verification-required p)))))

  (testing "nonexistent propagation method returns nil"
    (is (nil? (facts/propagation-method-by-id :cutting/nonexistent)))))

;; ──────────────────────── Jurisdiction Lookups ──────────────────────

(deftest jurisdiction-by-id-test
  (testing "JP MAFF jurisdiction exists"
    (let [j (facts/jurisdiction-by-id :jp/maff)]
      (is (some? j))
      (is (true? (:phytosanitary-cert-required j)))
      (is (contains? (:regulated-pests j) :quarantine-pest-a))))

  (testing "US USDA-APHIS jurisdiction exists"
    (let [j (facts/jurisdiction-by-id :us/usda-aphis)]
      (is (some? j))
      (is (contains? (:regulated-pests j) :quarantine-pest-c))))

  (testing "EU EPPO jurisdiction includes quarantine-pest-b as a regulated pest"
    (let [j (facts/jurisdiction-by-id :eu/eppo)]
      (is (some? j))
      (is (contains? (:regulated-pests j) :quarantine-pest-b))))

  (testing "nonexistent jurisdiction returns nil"
    (is (nil? (facts/jurisdiction-by-id :xx/unknown)))))

;; ──────────────────────── Genetic-Fidelity Risk Lookups ──────────────────────

(deftest propagation-source-risk-test
  (testing "grafted hybrid-tea rose has off-type-risk"
    (let [a (facts/propagation-source-risk :rose/hybrid-tea-grafted)]
      (is (= (:primary-risk a) :off-type-risk))))

  (testing "tissue-culture fern line has somaclonal-variation-risk"
    (let [a (facts/propagation-source-risk :fern/tissue-culture-line)]
      (is (= (:primary-risk a) :somaclonal-variation-risk))))

  (testing "own-root cutting has no primary risk"
    (let [a (facts/propagation-source-risk :rose/own-root-cutting)]
      (is (nil? (:primary-risk a)))))

  (testing "nonexistent propagation source returns nil"
    (is (nil? (facts/propagation-source-risk :unknown/lineage)))))

;; ──────────────────────── Propagation-Nursery Safety Predicates ──────────

(deftest rooting-in-range-test
  (testing "rooting rate at or above minimum passes"
    (let [p (facts/propagation-method-by-id :cutting/softwood)]
      (is (true? (facts/rooting-in-range? 60.0 p)))
      (is (true? (facts/rooting-in-range? 75.0 p)))))

  (testing "rooting rate below minimum fails"
    (let [p (facts/propagation-method-by-id :cutting/softwood)]
      (is (false? (facts/rooting-in-range? 50.0 p))))))

(deftest hardening-in-range-test
  (testing "hardening days at or above minimum passes"
    (let [p (facts/propagation-method-by-id :cutting/softwood)]
      (is (true? (facts/hardening-in-range? 14 p)))
      (is (true? (facts/hardening-in-range? 20 p)))))

  (testing "hardening days below minimum fails"
    (let [p (facts/propagation-method-by-id :cutting/softwood)]
      (is (false? (facts/hardening-in-range? 7 p))))))

;; ──────────────────────── Genetic-Fidelity Traceability ──────────────────────

(deftest propagation-source-risk-set-test
  (testing "grafted-only formulation collects off-type-risk"
    (let [propagation-sources [:rose/hybrid-tea-grafted]
          risks (facts/propagation-source-risk-set propagation-sources)]
      (is (contains? risks :off-type-risk))))

  (testing "blended formulation includes multiple risks"
    (let [propagation-sources [:rose/hybrid-tea-grafted :fern/tissue-culture-line]
          risks (facts/propagation-source-risk-set propagation-sources)]
      (is (contains? risks :off-type-risk))
      (is (contains? risks :somaclonal-variation-risk))))

  (testing "risk-free propagation sources produce empty set"
    (let [propagation-sources [:rose/own-root-cutting :basil/heirloom-cutting]
          risks (facts/propagation-source-risk-set propagation-sources)]
      (is (empty? risks)))))

(deftest genetic-fidelity-declaration-complete-test
  (testing "declaration matches formulation for jurisdiction"
    (let [j (facts/jurisdiction-by-id :jp/maff)
          propagation-sources [:rose/hybrid-tea-grafted]
          declared #{:off-type-risk}]
      (is (true? (facts/genetic-fidelity-declaration-complete? j propagation-sources declared)))))

  (testing "incomplete declaration fails"
    (let [j (facts/jurisdiction-by-id :jp/maff)
          propagation-sources [:rose/hybrid-tea-grafted :fern/tissue-culture-line]
          declared #{:off-type-risk}]
      (is (false? (facts/genetic-fidelity-declaration-complete? j propagation-sources declared)))))

  (testing "extra declarations pass (conservative)"
    (let [j (facts/jurisdiction-by-id :jp/maff)
          propagation-sources [:rose/hybrid-tea-grafted]
          declared #{:off-type-risk :somaclonal-variation-risk}]
      (is (true? (facts/genetic-fidelity-declaration-complete? j propagation-sources declared))))))

;; ──────────────────────── Evidence Completeness ──────────────────────

(deftest required-evidence-satisfied-test
  (testing "complete evidence checklist passes"
    (let [j (facts/jurisdiction-by-id :jp/maff)
          evidence [:batch-intake-record :propagation-method-log :rooting-rate-test
                    :genetic-fidelity-check :phytosanitary-inspection :hardening-log]]
      (is (true? (facts/required-evidence-satisfied? j evidence)))))

  (testing "incomplete evidence fails"
    (let [j (facts/jurisdiction-by-id :jp/maff)
          evidence [:batch-intake-record :propagation-method-log]]
      (is (false? (facts/required-evidence-satisfied? j evidence))))))
