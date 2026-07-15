(ns propagation.facts
  "Reference facts for plant-propagation nursery facilities: propagation-
  method quality windows (minimum rooting/take/germination rate, minimum
  hardening-off period before a batch is ready for sale/transplant, and
  whether genetic-fidelity verification is required), jurisdiction
  phytosanitary-certificate and evidence-checklist requirements, and
  per-propagation-source (cultivar/rootstock/clone) genetic-fidelity risk
  data. This namespace contains pure lookup functions for regulatory/
  nursery-quality compliance checks -- the Governor calls these to
  independently validate proposals; the advisor's confidence is never
  sufficient on its own.

  A plant-propagation nursery produces NEW PLANTS FOR SALE OR TRANSPLANT
  (cuttings, tissue-culture plantlets, grafted stock, and seedlings raised
  specifically to be propagated onward) -- this is what distinguishes
  ISIC 0130 from growing-to-harvest of a specific commodity crop (e.g.
  ISIC 0111/0121). The quality bar is therefore PROPAGULE VIABILITY
  (rooting/take/germination rate) and GENETIC/VARIETAL FIDELITY (true-to-
  type, correct rootstock/scion identity, no somaclonal variation), and
  the dominant regulatory hazard is PHYTOSANITARY (pest/pathogen spread
  via nursery stock movement), not food-safety."
  (:require [clojure.set :as set]))

(def propagation-method-types
  "Valid propagation-method categories and their safe viability/readiness
  windows. `rooting-min-percent` is the minimum fraction of propagules in
  the batch expected to successfully root/take/germinate under standard
  nursery conditions -- this is the single most important propagation
  quality indicator, since a batch below this floor cannot be certified
  ready for sale or transplant regardless of how well it was otherwise
  handled. `hardening-min-days` is the minimum number of days a batch
  must spend in the hardening-off phase before it is ready for shipment
  -- shipping too early risks transplant shock and field failure.
  `genetic-fidelity-verification-required` is true for methods with a
  material off-type risk (grafting -- wrong rootstock/scion identity;
  tissue culture -- somaclonal variation) and false for methods where the
  propagule is directly derived from a single verified parent plant with
  no intermediate risk of genetic drift."
  {:cutting/softwood
   {:id :cutting/softwood
    :name "緑枝挿し(ソフトウッドカッティング)"
    :rooting-min-percent 60.0
    :hardening-min-days 14
    :genetic-fidelity-verification-required false}

   :cutting/hardwood
   {:id :cutting/hardwood
    :name "硬枝挿し(ハードウッドカッティング)"
    :rooting-min-percent 50.0
    :hardening-min-days 21
    :genetic-fidelity-verification-required false}

   :graft/whip-and-tongue
   {:id :graft/whip-and-tongue
    :name "呼び接ぎ(ホイップ&タング接ぎ)"
    :rooting-min-percent 70.0
    :hardening-min-days 28
    :genetic-fidelity-verification-required true}

   :graft/bud
   {:id :graft/bud
    :name "芽接ぎ(バドグラフト)"
    :rooting-min-percent 75.0
    :hardening-min-days 21
    :genetic-fidelity-verification-required true}

   :tissue-culture/micropropagation
   {:id :tissue-culture/micropropagation
    :name "組織培養(微小繁殖)"
    :rooting-min-percent 85.0
    :hardening-min-days 35
    :genetic-fidelity-verification-required true}

   :seed-sown/direct
   {:id :seed-sown/direct
    :name "実生(直播種子繁殖)"
    :rooting-min-percent 65.0
    :hardening-min-days 14
    :genetic-fidelity-verification-required false}})

(defn propagation-method-by-id [id]
  (get propagation-method-types id))

(def jurisdictions
  "Plant-propagation-nursery jurisdictions and their phytosanitary-
  certificate and evidence-checklist requirements."
  {:jp/maff
   {:id :jp/maff
    :name "日本 (植物防疫法・種苗法・農林水産省)"
    :phytosanitary-cert-required true
    :regulated-pests #{:quarantine-pest-a :quarantine-pest-b}
    :required-evidence
    [:batch-intake-record
     :propagation-method-log
     :rooting-rate-test
     :genetic-fidelity-check
     :phytosanitary-inspection
     :hardening-log]}

   :us/usda-aphis
   {:id :us/usda-aphis
    :name "United States (USDA-APHIS Plant Protection & Quarantine)"
    :phytosanitary-cert-required true
    :regulated-pests #{:quarantine-pest-a :quarantine-pest-c}
    :required-evidence
    [:batch-intake-record
     :propagation-method-log
     :rooting-rate-test
     :genetic-fidelity-check
     :phytosanitary-inspection
     :hardening-log]}

   :eu/eppo
   {:id :eu/eppo
    :name "European Union (EPPO Plant Health Regime / Plant Passport)"
    :phytosanitary-cert-required true
    :regulated-pests #{:quarantine-pest-b :quarantine-pest-c}
    :required-evidence
    [:batch-intake-record
     :propagation-method-log
     :rooting-rate-test
     :genetic-fidelity-check
     :phytosanitary-inspection
     :hardening-log]}})

(defn jurisdiction-by-id [id]
  (get jurisdictions id))

(def propagation-source-risk-table
  "Per-propagation-source (cultivar/rootstock/clone lineage) primary
  genetic-fidelity risk, used to derive a batch's required-verification
  set for off-type / mislabeling checks. `:rose/own-root-cutting` and
  `:basil/heirloom-cutting` carry no primary risk of their own because
  the propagule is a direct clone of a single verified parent; grafted
  and tissue-cultured sources carry a real off-type risk (wrong
  rootstock/scion pairing, or somaclonal variation from repeated
  subculture) -- exactly why a true-to-type / correct-rootstock label
  claim requires verified genetic-fidelity checks. Sources with no
  fidelity-risk relevance map to nil."
  {:rose/hybrid-tea-grafted     {:primary-risk :off-type-risk :cross-contact-risk #{}}
   :rose/own-root-cutting       {:primary-risk nil :cross-contact-risk #{}}
   :apple/dwarfing-rootstock-graft {:primary-risk :off-type-risk :cross-contact-risk #{}}
   :fern/tissue-culture-line    {:primary-risk :somaclonal-variation-risk :cross-contact-risk #{}}
   :basil/heirloom-cutting      {:primary-risk nil :cross-contact-risk #{}}
   :orchid/tissue-culture-clone {:primary-risk :somaclonal-variation-risk :cross-contact-risk #{}}})

(defn propagation-source-risk [id]
  (get propagation-source-risk-table id))

(defn propagation-source-risk-set
  "Given a batch's propagation-source-id list, return the set of primary
  genetic-fidelity risks actually present. Non-risk-bearing / unknown
  propagation-source ids contribute nothing."
  [propagation-sources]
  (into #{}
        (keep (fn [id] (:primary-risk (propagation-source-risk id))))
        propagation-sources))

(defn genetic-fidelity-declaration-complete?
  "Verify that `declared` fidelity-checks are a superset of the batch's
  actual risks for `propagation-sources`. Extra (conservative)
  declarations pass; omissions fail. `jurisdiction` is accepted for
  call-site symmetry with other facts lookups."
  [_jurisdiction propagation-sources declared]
  (set/subset? (propagation-source-risk-set propagation-sources) (set declared)))

(defn required-evidence-satisfied?
  "Verify that every item in the jurisdiction's `:required-evidence` list
  is present in `evidence`. `jurisdiction` may be a resolved jurisdiction
  map (as returned by `jurisdiction-by-id`) or a raw jurisdiction id --
  both call conventions are in use (tests pass a resolved map; the
  Governor passes the raw id straight off batch metadata)."
  [jurisdiction evidence]
  (let [j (if (map? jurisdiction) jurisdiction (jurisdiction-by-id jurisdiction))]
    (if-not j
      false
      (set/subset? (set (:required-evidence j)) (set evidence)))))

(defn rooting-in-range?
  "Positive-sense convenience predicate: is `percent` at or above
  `propagation-method`'s minimum required rooting/take/germination rate?"
  [percent propagation-method]
  (boolean
   (and (some? propagation-method)
        (>= percent (:rooting-min-percent propagation-method)))))

(defn hardening-in-range?
  "Positive-sense convenience predicate: has `days` reached or exceeded
  `propagation-method`'s minimum required hardening-off period?"
  [days propagation-method]
  (boolean
   (and (some? propagation-method)
        (>= days (:hardening-min-days propagation-method)))))
