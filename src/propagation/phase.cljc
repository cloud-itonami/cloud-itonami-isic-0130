(ns propagation.phase
  "Phase machine: the states a plant-propagation-nursery batch transits
  through.

  State machine:
    :intake -> :propagate -> :root -> :harden -> :inspect -> :audit -> :archived

  `:intake` is propagation-source receiving and batch registration;
  `:propagate` is the propagation event itself (taking cuttings,
  grafting, initiating tissue culture, or sowing seed); `:root` is the
  rooting/establishment period; `:harden` is the hardening-off phase
  (acclimatizing propagules to ambient conditions before sale/
  transplant); `:inspect` is rooting-rate/genetic-fidelity/phytosanitary
  testing; `:audit` is compliance audit; `:archived` is the terminal
  state.

  Each transition can accept a proposal and yield an audit fact.")

(def all-phases
  "All valid phases in the plant-propagation-nursery workflow."
  [:intake :propagate :root :harden :inspect :audit :archived])

(def phase-sequence
  "Ordered phases representing normal batch progression."
  [:intake :propagate :root :harden :inspect :audit :archived])

(defn valid-phase?
  "Check if a phase is valid."
  [phase]
  (contains? (set all-phases) phase))

(defn- index-of
  "Portable (Clojure/ClojureScript) index lookup -- `.indexOf` is a
  JVM-only `java.util.List` method that ClojureScript's PersistentVector
  does not implement, so it is avoided here even though `phase-sequence`
  is a plain vector. Returns -1 when `x` is not found, matching
  `java.util.List/indexOf`'s contract."
  [coll x]
  (or (first (keep-indexed (fn [i v] (when (= v x) i)) coll)) -1))

(defn can-transition?
  "Check if a transition from one phase to another is valid
  (must be forward-only in the sequence, no backtracking). Always returns a
  boolean (never nil), including when either phase is invalid."
  [from-phase to-phase]
  (boolean
   (and (valid-phase? from-phase) (valid-phase? to-phase)
        (let [from-idx (index-of phase-sequence from-phase)
              to-idx (index-of phase-sequence to-phase)]
          (and (>= from-idx 0) (>= to-idx 0) (< from-idx to-idx))))))
