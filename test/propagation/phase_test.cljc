(ns propagation.phase-test
  (:require [clojure.test :refer [deftest is testing]]
            [propagation.phase :as phase]))

;; ──────────────────────── Phase Validity ──────────────────────

(deftest valid-phase-test
  (testing "intake is valid"
    (is (true? (phase/valid-phase? :intake))))

  (testing "root is valid"
    (is (true? (phase/valid-phase? :root))))

  (testing "archived is valid"
    (is (true? (phase/valid-phase? :archived))))

  (testing "invalid phase returns false"
    (is (false? (phase/valid-phase? :invalid)))))

;; ──────────────────────── Phase Transitions ──────────────────────

(deftest can-transition-test
  (testing "intake -> propagate is valid (forward progression)"
    (is (true? (phase/can-transition? :intake :propagate))))

  (testing "intake -> root is valid (skip propagate)"
    (is (true? (phase/can-transition? :intake :root))))

  (testing "propagate -> intake is invalid (backward)"
    (is (false? (phase/can-transition? :propagate :intake))))

  (testing "root -> archived is valid (forward to end)"
    (is (true? (phase/can-transition? :root :archived))))

  (testing "archived -> intake is invalid (backward from end)"
    (is (false? (phase/can-transition? :archived :intake))))

  (testing "same phase is invalid"
    (is (false? (phase/can-transition? :root :root))))

  (testing "invalid phases return false"
    (is (false? (phase/can-transition? :invalid :root)))
    (is (false? (phase/can-transition? :root :invalid)))))
