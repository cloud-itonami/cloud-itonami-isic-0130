(ns propagation.operation-test
  (:require [clojure.test :refer [deftest is testing]]
            [propagation.operation :as operation]
            [propagation.governor :as governor]))

(def ^:private now-ms #?(:clj (System/currentTimeMillis) :cljs (.now js/Date)))
(def ^:private ten-days-ago (- now-ms (* 10 24 60 60 1000)))

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

(deftest run-operation-commit-test
  (testing "clean, non-actuation proposal commits with no hold facts"
    (let [store {:batches {"batch-001" clean-batch}}
          request {:op :schedule-maintenance :subject "batch-001"}
          proposal {:cites [{:spec "Equipment-Manual"}]
                    :value {:jurisdiction :jp/maff}
                    :effect :propose
                    :confidence 0.9}
          context {:actor-id "op-1" :hold-fact-fn governor/hold-fact}
          result (operation/run-operation request context proposal store governor/check)]
      (is (true? (:ok? result)))
      (is (= [] (:facts result))))))

(deftest run-operation-hold-test
  (testing "hard-violating proposal (already-logged batch) produces a hold fact"
    (let [store {:batches {"batch-002" {:logged? true}}}
          request {:op :log-propagation-batch :subject "batch-002"}
          proposal {:cites [{:spec "ISO-12345"}]
                    :value {:jurisdiction :jp/maff}
                    :effect :propose
                    :confidence 0.9}
          context {:actor-id "op-1" :hold-fact-fn governor/hold-fact}
          result (operation/run-operation request context proposal store governor/check)]
      (is (false? (:ok? result)))
      (is (= 1 (count (:facts result))))
      (is (= :governor-hold (:t (first (:facts result)))))
      (is (true? (:hard? (:verdict result)))))))

(deftest run-operation-escalate-test
  (testing "clean but high-stakes proposal is not auto-ok (escalation required)"
    (let [store {:batches {"batch-003" clean-batch}}
          request {:op :log-propagation-batch :subject "batch-003"}
          proposal {:cites [{:spec "ISO-12345"}]
                    :value {:jurisdiction :jp/maff}
                    :effect :propose
                    :confidence 0.95}
          context {:actor-id "op-1" :hold-fact-fn governor/hold-fact}
          result (operation/run-operation request context proposal store governor/check)]
      (is (false? (:ok? result)))
      (is (false? (:hard? (:verdict result))))
      (is (true? (:escalate? (:verdict result))))
      ;; operation.cljc has a single :ok?/not-ok? gate today; both hard-hold
      ;; and escalate-only verdicts route through the same hold-fact-fn.
      ;; Callers distinguish the two by inspecting `(:verdict result)`.
      (is (= 1 (count (:facts result)))))))

(deftest run-operation-quality-concern-always-escalates-test
  (testing "a clean flag-quality-concern proposal is never auto-ok"
    (let [store {:batches {"batch-004" clean-batch}}
          request {:op :flag-quality-concern :subject "batch-004"}
          proposal {:cites [{:spec "Facility-QA-Plan"}]
                    :value {:jurisdiction :jp/maff}
                    :effect :propose
                    :confidence 0.99}
          context {:actor-id "op-1" :hold-fact-fn governor/hold-fact}
          result (operation/run-operation request context proposal store governor/check)]
      (is (false? (:ok? result)))
      (is (false? (:hard? (:verdict result))))
      (is (true? (:escalate? (:verdict result)))))))

(deftest run-operation-op-not-allowed-test
  (testing "an out-of-allowlist op (e.g. direct greenhouse/propagation-equipment control) is a hard, permanent block"
    (let [store {:batches {"batch-005" clean-batch}}
          request {:op :control-greenhouse-climate :subject "batch-005"}
          proposal {:cites [{:spec "Climate-Control-Manual"}]
                    :value {:jurisdiction :jp/maff}
                    :effect :propose
                    :confidence 0.99}
          context {:actor-id "op-1" :hold-fact-fn governor/hold-fact}
          result (operation/run-operation request context proposal store governor/check)]
      (is (false? (:ok? result)))
      (is (true? (:hard? (:verdict result))))
      (is (some #(= (:rule %) :op-not-allowed) (:violations (:verdict result)))))))

(deftest run-operation-effect-not-propose-test
  (testing "a proposal asserting a non-:propose effect is a hard, permanent block"
    (let [store {:batches {"batch-006" clean-batch}}
          request {:op :schedule-maintenance :subject "batch-006"}
          proposal {:cites [{:spec "Equipment-Manual"}]
                    :value {:jurisdiction :jp/maff}
                    :effect :commit
                    :confidence 0.9}
          context {:actor-id "op-1" :hold-fact-fn governor/hold-fact}
          result (operation/run-operation request context proposal store governor/check)]
      (is (false? (:ok? result)))
      (is (true? (:hard? (:verdict result))))
      (is (some #(= (:rule %) :effect-not-propose) (:violations (:verdict result)))))))

(deftest run-operation-batch-not-registered-test
  (testing "coordinating shipment for a never-registered batch is a hard block"
    (let [store {:batches {}}
          request {:op :coordinate-shipment :subject "batch-999"}
          proposal {:cites [{:spec "Shipment-Manual"}]
                    :value {:jurisdiction :jp/maff}
                    :effect :propose
                    :confidence 0.9}
          context {:actor-id "op-1" :hold-fact-fn governor/hold-fact}
          result (operation/run-operation request context proposal store governor/check)]
      (is (false? (:ok? result)))
      (is (true? (:hard? (:verdict result))))
      (is (some #(= (:rule %) :batch-not-registered) (:violations (:verdict result)))))))
