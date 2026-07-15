(ns propagation.sim
  "Simulation driver for testing the plant-propagation-nursery operations
  actor end-to-end.

  For CLI: clojure -M:dev:run

  Example flow:
    1. Start with empty store
    2. Register a batch in :intake phase
    3. Propose a batch -> :inspect transition with rooting-rate/hardening
       parameters
    4. Governor validates parameters against facts
    5. If valid, audit fact is committed
    6. CLI prints audit trail")

(defn -main [& _args]
  (println "PropagationOps simulation: not yet implemented.")
  (println "TODO: integrate langgraph-clj StateGraph when available."))
