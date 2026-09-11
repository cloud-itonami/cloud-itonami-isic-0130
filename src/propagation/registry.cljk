(ns propagation.registry
  "Pure validation functions for plant-propagation-nursery parameters.
  These are called by the Governor to independently verify physical/
  operational constraints -- the advisor's confidence is NOT sufficient
  to override these checks.

  All functions here are pure arithmetic/set/boolean predicates with no
  host-clock or I/O calls, so this namespace stays trivially portable
  across Clojure/ClojureScript. Callers that need the current time (see
  `phytosanitary-inspection-overdue?`) obtain it themselves via a
  `:clj`/`:cljs` reader-conditional at the call site (see
  `propagation.governor`)."
  (:require [clojure.set :as set]))

(defn rooting-rate-below-minimum?
  "Independently verify that the batch's actual rooting/take/germination
  rate (%) does not fall below the propagation method's minimum required
  rate. Rooting rate is the single most serious viability hazard specific
  to plant propagation -- a batch below the floor cannot be sold or
  transplanted as nursery stock regardless of how well it was otherwise
  handled, a hard, un-overridable stop."
  [actual-percent min-percent]
  (< actual-percent min-percent))

(defn hardening-period-incomplete?
  "Independently verify that the batch has spent at least the
  propagation method's minimum required number of days in the
  hardening-off phase. Shipping before the minimum hardening period
  risks transplant shock and field failure."
  [actual-days min-days]
  (< actual-days min-days))

(defn genetic-fidelity-risk?
  "True when the batch's propagation-source formulation contains a
  genetic-fidelity risk (off-type / somaclonal variation) NOT present in
  the declared-fidelity-checks set (mislabeling / off-type risk -- a
  genuine varietal-integrity hazard for growers relying on the label,
  especially for grafted and tissue-cultured stock). Declaring MORE
  fidelity checks than the batch actually requires is conservative and
  never a risk."
  [formula-risks declared-checks]
  (not (set/subset? (set formula-risks) (set declared-checks))))

(defn pest-or-disease-detected?
  "Independently verify a batch's own pest/pathogen-screening result.
  Any detection is a genuine phytosanitary hazard -- nursery stock is a
  primary pathway for invasive pest and pathogen spread, which is why
  this predicate simply coerces the raw fact to a boolean so the
  Governor's check functions stay uniform in shape with every other
  independently-verified physical constraint in this namespace."
  [actual-detected?]
  (boolean actual-detected?))

(defn phytosanitary-inspection-overdue?
  "Independently verify that the batch's most recent phytosanitary
  inspection was performed within the last 90 days.
  `last-inspection-epoch-ms` and `now-epoch-ms` are both epoch
  milliseconds -- callers obtain `now` via a `:clj`/`:cljs`
  reader-conditional, keeping this namespace free of any host-clock
  call. An out-of-date inspection silently invalidates the very
  phytosanitary status this actor's Governor relies on."
  [last-inspection-epoch-ms now-epoch-ms]
  (> (- now-epoch-ms last-inspection-epoch-ms)
     (* 90 24 60 60 1000)))

(defn sanitation-score-insufficient?
  "Independently verify that the facility's sanitation/cross-
  contamination-control score meets the minimum required. Score is
  0-100, assessed by a third-party auditor against nursery sanitation
  standards -- a significant concern specific to preventing batch-to-
  batch pest/pathogen spread in propagation, rooting, and hardening
  areas."
  [actual-score min-score-required]
  (< actual-score min-score-required))
