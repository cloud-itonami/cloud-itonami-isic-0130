(ns propagation.governor
  "NurseryOperationsGovernor -- the independent compliance layer that earns
  the PropagationAdvisor the right to commit. The LLM has no notion of:
    - Whether a batch's rooting/take/germination rate falls below the
      propagation method's minimum required rate
    - Whether the batch has completed its minimum hardening-off period
    - Whether a pest or pathogen was detected on the batch's own screening
    - Whether the batch's most recent phytosanitary inspection is current
    - Whether genetic-fidelity labeling (true-to-type / correct rootstock-
      scion identity, no undeclared somaclonal-variation risk) is complete
    - Whether facility sanitation/cross-contamination-control score is
      passed
    - Whether an open phytosanitary quarantine flag has been resolved
    - Whether the batch record is registered in the nursery system at all

  This MUST be a separate system able to *reject* a proposal and fall back
  to HOLD.

  Unlike direct greenhouse/irrigation/propagation-equipment control (NEVER
  done by this actor -- climate-control systems, misting/irrigation
  systems, and grafting/cutting tools remain exclusive to nursery staff),
  the Governor operates on batch metadata: provenance, propagation
  parameters, sanitation records, and quality flags. This is nursery-
  operations coordination, not equipment control, and it is not
  phytosanitary-certification authority: this actor coordinates the
  paperwork and workflow around certification, it does not itself certify
  a batch for sale or transplant.

  CRITICAL: Any proposal involving a quality concern (low rooting rate,
  pest/pathogen detection, genetic-fidelity mismatch) ALWAYS escalates to
  human nursery-operator sign-off. The LLM's confidence is never
  sufficient for propagule-viability or varietal-integrity decisions.

  Hard violations (always HOLD, no override):
    1. Operation outside the closed allowlist, including anything that
       would touch greenhouse/irrigation/propagation-equipment control
    2. Proposal asserting an `:effect` other than `:propose`
    3. Batch record not independently verified/registered before any
       action (`:batch-not-registered`)
    4. No jurisdiction citation (jurisdiction unknown -> can't verify reqs)
    5. Evidence incomplete (missing required-evidence per jurisdiction)
    6. Rooting/take/germination rate below the propagation method's
       minimum required rate
    7. Hardening-off period incomplete (shipped/logged too early)
    8. Genetic-fidelity risk undeclared (off-type / somaclonal-variation
       mislabeling risk)
    9. Pest or pathogen detected (phytosanitary screening)
   10. Phytosanitary inspection overdue
   11. Facility sanitation/cross-contamination-control score insufficient
   12. Quarantine flag unresolved (open concern, escalate required)
   13. Batch already logged / shipment already finalized (double-commit
       guards)

  Soft gates (always escalate for human):
    - Low confidence
    - Real actuation (`:log-propagation-batch`, `:coordinate-shipment`)
    - `:flag-quality-concern` (never auto-resolved by confidence alone)

  This design mirrors `seedops.governor` but specializes on plant-
  propagation-nursery-specific concerns: rooting/take/germination-rate
  viability, hardening-off readiness, genetic/varietal fidelity, and
  phytosanitary pest/pathogen hazard -- rather than seed-lot moisture/
  purity/other-crop-seed-contamination or food-safety concerns."
  (:require [propagation.facts :as facts]
            [propagation.registry :as registry]
            [propagation.store :as store]))

(def confidence-floor 0.6)

(def high-stakes
  "Stakes grave enough to always require a human, even when clean.
  Logging a batch into nursery records (`:log-propagation-batch`) and
  coordinating shipment of nursery stock (`:coordinate-shipment`) are the
  two real-world actuation events this actor performs. Both require
  nursery-operator sign-off."
  #{:log-propagation-batch :coordinate-shipment})

(def always-escalate-ops
  "Operations that always require human sign-off, even when the Governor's
  hard checks are clean and confidence is high: the two high-stakes
  actuation events (`high-stakes`) plus `:flag-quality-concern` -- a
  quality concern (low rooting rate, pest/pathogen detection, genetic-
  fidelity mismatch) is never auto-resolved by advisor confidence alone,
  it always needs a human look."
  (conj high-stakes :flag-quality-concern))

(def allowed-ops
  "Closed allowlist of proposal operations this actor may ever make. Any
  proposal for an operation outside this set -- most importantly direct
  greenhouse/irrigation/propagation-equipment control (climate-control
  systems, misting/irrigation systems, grafting/cutting tools) or
  phytosanitary-certification-authority decisions -- is a hard, permanent
  block: this actor coordinates nursery operations, it does not operate
  equipment and it does not certify stock for sale or transplant."
  #{:log-propagation-batch :schedule-maintenance :flag-quality-concern :coordinate-shipment})

;; ────────────────────────── Checks ──────────────────────────

(defn- op-not-allowed-violations
  "HARD, permanent block: any proposal outside the closed operation
  allowlist (e.g. direct greenhouse/irrigation/propagation-equipment
  control, or a phytosanitary-certification-authority action) is refused
  unconditionally -- this actor has no authority to make such a proposal
  at all, let alone commit it."
  [{:keys [op]} _proposal]
  (when-not (contains? allowed-ops op)
    [{:rule :op-not-allowed
      :detail (str op " はこのactorの許可された提案種別 (log-propagation-batch/"
                  "schedule-maintenance/flag-quality-concern/coordinate-shipment) "
                  "に含まれない -- 温室/灌漑/繁殖設備の直接制御や検疫証明認証権限はこのactorに無い")}]))

(defn- effect-not-propose-violations
  "HARD invariant: this actor's proposals are always `:effect :propose` --
  it never claims direct write/actuation authority for itself. A proposal
  asserting any other effect is refused unconditionally."
  [_request proposal]
  (when-let [effect (:effect proposal)]
    (when (not= effect :propose)
      [{:rule :effect-not-propose
        :detail (str "この actor の提案は :propose 以外の :effect を持てない (got " effect ")")}])))

(defn- batch-not-registered-violations
  "HARD invariant: the nursery/batch record must be verified/registered in
  the store BEFORE any action -- proposing against a batch this nursery
  never checked in is out of scope for this actor, for every op in the
  allowlist (registration is a precondition, not an outcome, of
  coordinating that batch's workflow)."
  [{:keys [op subject]} st]
  (when (contains? allowed-ops op)
    (when-not (store/nursery-batch st subject)
      [{:rule :batch-not-registered
        :detail (str subject " は施設に登録されたバッチ記録が無い -- 提案は進められない")}])))

(defn- spec-basis-violations
  "A proposal with no jurisdiction citation is a HARD violation -- never
  invent a jurisdiction's phytosanitary/propagation requirements."
  [{:keys [op]} proposal]
  (when (contains?
         #{:log-propagation-batch :coordinate-shipment :flag-quality-concern}
         op)
    (let [value (:value proposal)]
      (when (or (empty? (:cites proposal))
                (and (contains? value :jurisdiction) (nil? (:jurisdiction value))))
        [{:rule :no-spec-basis
          :detail "公式仕様の引用が無い提案は法域要件として扱えない"}]))))

(defn- evidence-incomplete-violations
  "For `:log-propagation-batch`, verify the batch's evidence checklist is
  complete per jurisdiction requirements."
  [{:keys [op subject]} st]
  (when (= op :log-propagation-batch)
    (let [b (store/nursery-batch st subject)]
      (when-not (and b
                     (facts/required-evidence-satisfied?
                      (:jurisdiction b)
                      (:evidence-checklist b)))
        [{:rule :evidence-incomplete
          :detail "法域の必要書類(batch-intake-record/propagation-method-log/rooting-rate-test等)が充足していない状態での提案"}]))))

(defn- rooting-rate-below-minimum-violations
  "For `:log-propagation-batch`, INDEPENDENTLY verify that the batch's
  rooting/take/germination rate meets the propagation method's minimum
  required rate via `registry/rooting-rate-below-minimum?`. Evaluated
  UNCONDITIONALLY -- this is the single most serious viability hazard
  specific to plant propagation."
  [{:keys [op subject]} st]
  (when (= op :log-propagation-batch)
    (let [b (store/nursery-batch st subject)
          p (when b (facts/propagation-method-by-id (:propagation-method b)))]
      (when (and b p (:rooting-percent b)
                 (registry/rooting-rate-below-minimum?
                  (:rooting-percent b)
                  (:rooting-min-percent p)))
        [{:rule :rooting-rate-below-minimum
          :detail (str subject " の発根/活着率(" (:rooting-percent b)
                      "%)が最低要件(" (:rooting-min-percent p)
                      "%)を下回る -- バッチ登録提案は進められない")}]))))

(defn- hardening-period-incomplete-violations
  "For `:log-propagation-batch`, INDEPENDENTLY verify that the batch has
  completed the propagation method's minimum required hardening-off
  period via `registry/hardening-period-incomplete?`."
  [{:keys [op subject]} st]
  (when (= op :log-propagation-batch)
    (let [b (store/nursery-batch st subject)
          p (when b (facts/propagation-method-by-id (:propagation-method b)))]
      (when (and b p (:hardening-days b)
                 (registry/hardening-period-incomplete?
                  (:hardening-days b)
                  (:hardening-min-days p)))
        [{:rule :hardening-period-incomplete
          :detail (str subject " の順化(ハードニング)期間(" (:hardening-days b)
                      "日)が最低要件(" (:hardening-min-days p)
                      "日)を下回る -- バッチ登録提案は進められない")}]))))

(defn- genetic-fidelity-risk-violations
  "For `:log-propagation-batch`, INDEPENDENTLY verify genetic-fidelity
  declaration completeness via `registry/genetic-fidelity-risk?`."
  [{:keys [op subject]} st]
  (when (= op :log-propagation-batch)
    (let [b (store/nursery-batch st subject)
          formula-risks (facts/propagation-source-risk-set (:propagation-sources b))]
      (when (and b formula-risks (:declared-fidelity-checks b)
                 (registry/genetic-fidelity-risk? formula-risks (:declared-fidelity-checks b)))
        [{:rule :genetic-fidelity-risk
          :detail (str subject " の遺伝的同一性(真正性)表示が不完全 -- バッチ登録提案は進められない")}]))))

(defn- pest-or-disease-detected-violations
  "For `:log-propagation-batch`, INDEPENDENTLY verify the batch's own
  pest/pathogen-screening result via `registry/pest-or-disease-detected?`.
  A detection on THIS batch's own testing is a hard, phytosanitary hazard
  block -- distinct from `quarantine-flag-unresolved-violations` below,
  which covers a separately-raised, not-yet-resolved concern."
  [{:keys [op subject]} st]
  (when (= op :log-propagation-batch)
    (let [b (store/nursery-batch st subject)]
      (when (and b (registry/pest-or-disease-detected? (:pest-or-disease-detected? b)))
        [{:rule :pest-or-disease-detected
          :detail (str subject " で病害虫が検出された -- バッチ登録提案は進められない")}]))))

(defn- now-epoch-ms
  "Current time in epoch milliseconds, portable across Clojure/
  ClojureScript. Isolated to this single call site so the rest of the
  namespace (and all of `propagation.registry`) stays free of host-clock
  calls."
  []
  #?(:clj (System/currentTimeMillis)
     :cljs (js/Date.now)))

(defn- phytosanitary-inspection-overdue-violations
  "For `:log-propagation-batch`, INDEPENDENTLY verify that the batch's
  most recent phytosanitary inspection is current (re-inspection required
  every 90 days)."
  [{:keys [op subject]} st]
  (when (= op :log-propagation-batch)
    (let [b (store/nursery-batch st subject)]
      (when (and b (:last-phytosanitary-inspection-date b)
                 (registry/phytosanitary-inspection-overdue? (:last-phytosanitary-inspection-date b) (now-epoch-ms)))
        [{:rule :phytosanitary-inspection-overdue
          :detail (str subject " の検疫(植物防疫)検査が期限切れ -- バッチ登録提案は進められない")}]))))

(defn- sanitation-score-insufficient-violations
  "For `:log-propagation-batch`, INDEPENDENTLY verify that the facility's
  sanitation/cross-contamination-control score meets minimum
  requirements."
  [{:keys [op subject]} st]
  (when (= op :log-propagation-batch)
    (let [b (store/nursery-batch st subject)]
      (when (and b (:sanitation-score b)
                 (registry/sanitation-score-insufficient? (:sanitation-score b) 75))
        [{:rule :sanitation-score-insufficient
          :detail (str subject " の施設衛生/交差汚染防止スコア(" (:sanitation-score b)
                      ")が最低要件(75)を下回る -- バッチ登録提案は進められない")}]))))

(defn- quarantine-flag-unresolved-violations
  "An unresolved quarantine flag is a HARD, un-overridable hold.
  Phytosanitary concerns (suspected pest/pathogen, low rooting rate,
  genetic-fidelity mismatch) raised during propagation or inspection MUST
  be resolved before the batch can be logged. Evaluated UNCONDITIONALLY
  at `:log-propagation-batch`."
  [{:keys [op subject]} st]
  (when (= op :log-propagation-batch)
    (let [b (store/nursery-batch st subject)]
      (when (and (true? (:quarantine-flag-raised? b))
                 (not (true? (:quarantine-flag-resolved? b))))
        [{:rule :quarantine-flag-unresolved
          :detail (str subject " は未解決の検疫フラグがある -- バッチ登録提案は進められない")}]))))

(defn- already-logged-violations
  "For `:log-propagation-batch`, refuse to log the SAME batch twice, off
  a dedicated `:logged?` fact (never a `:status` value)."
  [{:keys [op subject]} st]
  (when (= op :log-propagation-batch)
    (when (store/batch-already-logged? st subject)
      [{:rule :already-logged
        :detail (str subject " は既に登録済み")}])))

(defn- already-shipment-finalized-violations
  "For `:coordinate-shipment`, refuse to finalize the SAME batch's
  shipment twice, off a dedicated `:shipment-finalized?` fact."
  [{:keys [op subject]} st]
  (when (= op :coordinate-shipment)
    (when (store/batch-shipment-finalized? st subject)
      [{:rule :already-shipment-finalized
        :detail (str subject " は既に出荷確定済み")}])))

(defn check
  "Censors a PropagationAdvisor proposal against the Governor rules.
  Returns {:ok? bool :violations [..] :confidence c :escalate? bool
  :high-stakes? bool :hard? bool}.

  Stakes (high-stakes actuation vs. always-escalate) are read off the
  REQUEST's `:op` -- not off the proposal -- since the operation being
  proposed (not the advisor's self-reported stake) is what determines
  whether a human must sign off."
  [request _context proposal st]
  (let [hard (into []
                   (concat (op-not-allowed-violations request proposal)
                           (effect-not-propose-violations request proposal)
                           (batch-not-registered-violations request st)
                           (spec-basis-violations request proposal)
                           (evidence-incomplete-violations request st)
                           (rooting-rate-below-minimum-violations request st)
                           (hardening-period-incomplete-violations request st)
                           (genetic-fidelity-risk-violations request st)
                           (pest-or-disease-detected-violations request st)
                           (phytosanitary-inspection-overdue-violations request st)
                           (sanitation-score-insufficient-violations request st)
                           (quarantine-flag-unresolved-violations request st)
                           (already-logged-violations request st)
                           (already-shipment-finalized-violations request st)))
        conf (:confidence proposal 0.0)
        low? (< conf confidence-floor)
        actuation? (boolean (high-stakes (:op request)))
        escalate-op? (boolean (always-escalate-ops (:op request)))
        hard? (boolean (seq hard))]
    {:ok?          (and (not hard?) (not low?) (not escalate-op?))
     :violations   hard
     :confidence   conf
     :hard?        hard?
     :escalate?    (and (not hard?) (or low? escalate-op?))
     :high-stakes? actuation?}))

(defn hold-fact
  "The audit fact written when a proposal is rejected (HOLD)."
  [request context verdict]
  {:t          :governor-hold
   :op         (:op request)
   :actor      (:actor-id context)
   :subject    (:subject request)
   :disposition :hold
   :basis      (mapv :rule (:violations verdict))
   :violations (:violations verdict)
   :confidence (:confidence verdict)})
