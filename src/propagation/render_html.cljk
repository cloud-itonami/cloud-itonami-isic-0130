(ns propagation.render-html
  "Build-time HTML renderer for `docs/samples/operator-console.html`.

  Closes flagship checklist item 2 for this repo: before this namespace
  existed there was no demo page and no generator at all -- and, unlike
  most sibling cloud-itonami actors, `propagation.sim` here is still a
  stub (`clojure -M:dev:run` prints \"not yet implemented\"), so there
  was no seeded store to reuse either. This namespace therefore carries
  its own scenario fixture and drives the REAL actor stack over it:

      propagation.operation/run-operation
        -> propagation.governor/check        (independent compliance layer)
        -> propagation.registry/*            (pure physical verification)
        -> propagation.facts/*               (propagation-method + jurisdiction reference data)
        -> propagation.store/*               (batches + append-only audit ledger)

  What is REAL runtime output (executed, never hand-typed):
    - every Governor verdict (`:ok?` / `:hard?` / `:escalate?` / `:confidence`)
    - every `:governor-hold` fact and its `:basis` rule vector and Japanese
      `:detail` strings -- these come out of `propagation.governor/hold-fact`
    - every rooting-rate / hardening-period / sanitation / phytosanitary /
      genetic-fidelity comparison shown in the batch table, which is
      recomputed here through the same `propagation.facts` lookups the
      Governor itself uses
    - the double-commit holds (`:already-logged`,
      `:already-shipment-finalized`), which can only fire because the
      earlier approved commits really mutated the store via
      `store/log-batch` / `store/finalize-shipment`
    - the action-gate table, derived from the live `governor/allowed-ops`,
      `governor/always-escalate-ops`, `governor/high-stakes` and
      `governor/confidence-floor` vars

  What is STATIC (honest description of a fixed contract, not telemetry):
    - the scenario fixture itself (`demo-batches` / `scenario`) -- these
      are the INPUTS; nothing about a Governor decision is encoded in them
    - the one-line prose description of what each allowlisted op means,
      in the right-hand column of the action-gate table
    - section headings and explanatory paragraphs

  Determinism: the page contains no timestamp, no random value and no
  wall-clock reading. Batch inspection dates are stored relative to `now`
  (fresh = 12 days ago, overdue = 100 days ago) purely so that the
  90-day `registry/phytosanitary-inspection-overdue?` verdict is stable
  over time; the raw epoch values are never rendered, only the derived
  current/overdue status. Two consecutive runs are byte-identical.

  Usage: `clojure -M:dev:render-html [out-file]`
  (default `docs/samples/operator-console.html`)."
  (:require [clojure.java.io :as io]
            [kotoba.lang.text :as str]
            [jp-go-dds.skin]
            [propagation.facts :as facts]
            [propagation.governor :as governor]
            [propagation.operation :as operation]
            [propagation.store :as store]))

;; ─────────────────────────── scenario fixture ───────────────────────────

(def ^:private day-ms (* 24 60 60 1000))

(defn- days-ago
  "Epoch-ms `n` days before now. Used ONLY to seed batch inspection dates
  so that the Governor's 90-day phytosanitary re-inspection window yields
  the same verdict whenever this generator runs. The value itself never
  reaches the page."
  [n]
  (- (System/currentTimeMillis) (* n day-ms)))

(def ^:private full-evidence
  "The evidence checklist every jurisdiction in `propagation.facts`
  currently requires, in full. Batches that should clear
  `:evidence-incomplete` carry this; `PB-2405` deliberately omits one
  item so the Governor can catch it."
  [:batch-intake-record :propagation-method-log :rooting-rate-test
   :genetic-fidelity-check :phytosanitary-inspection :hardening-log])

(def ^:private demo-batches
  "Scenario INPUT: six nursery propagation batches, ordered. These are
  fixture records of the kind a nursery system would hold -- provenance,
  propagation parameters, screening results. No verdict, disposition or
  ledger row is encoded here; every one of those is produced by running
  `propagation.governor` over these records.

  `:label` is display-only nursery-record metadata (the batch's plant
  lineage in Japanese/Latin); every other key is read by the Governor."
  [["PB-2401"
    {:label "バラ 'ピース' 緑枝挿し / Rosa 'Peace' softwood cuttings"
     :propagation-method :cutting/softwood
     :jurisdiction :jp/maff
     :rooting-percent 74.0
     :hardening-days 21
     :pest-or-disease-detected? false
     :last-phytosanitary-inspection-date (days-ago 12)
     :sanitation-score 88
     :propagation-sources [:rose/own-root-cutting]
     :declared-fidelity-checks #{}
     :evidence-checklist full-evidence}]

   ["PB-2402"
    {:label "コチョウラン 組織培養クローン / Phalaenopsis micropropagation line"
     :propagation-method :tissue-culture/micropropagation
     :jurisdiction :us/usda-aphis
     :rooting-percent 78.5
     :hardening-days 40
     :pest-or-disease-detected? false
     :last-phytosanitary-inspection-date (days-ago 20)
     :sanitation-score 90
     :propagation-sources [:orchid/tissue-culture-clone]
     :declared-fidelity-checks #{:somaclonal-variation-risk}
     :evidence-checklist full-evidence}]

   ["PB-2403"
    {:label "リンゴ わい性台木 芽接ぎ / Apple dwarfing-rootstock bud graft"
     :propagation-method :graft/bud
     :jurisdiction :eu/eppo
     :rooting-percent 82.0
     :hardening-days 24
     :pest-or-disease-detected? false
     :last-phytosanitary-inspection-date (days-ago 30)
     :sanitation-score 82
     :propagation-sources [:apple/dwarfing-rootstock-graft]
     :declared-fidelity-checks #{}
     :evidence-checklist full-evidence}]

   ["PB-2404"
    {:label "シダ 組織培養ライン / Fern tissue-culture line"
     :propagation-method :tissue-culture/micropropagation
     :jurisdiction :jp/maff
     :rooting-percent 88.0
     :hardening-days 38
     :pest-or-disease-detected? true
     :quarantine-flag-raised? true
     :quarantine-flag-resolved? false
     :last-phytosanitary-inspection-date (days-ago 15)
     :sanitation-score 80
     :propagation-sources [:fern/tissue-culture-line]
     :declared-fidelity-checks #{:somaclonal-variation-risk}
     :evidence-checklist full-evidence}]

   ["PB-2405"
    {:label "バジル 在来種 挿し木 / Basil heirloom cuttings"
     :propagation-method :cutting/softwood
     :jurisdiction :jp/maff
     :rooting-percent 66.0
     :hardening-days 9
     :pest-or-disease-detected? false
     :last-phytosanitary-inspection-date (days-ago 8)
     :sanitation-score 68
     :propagation-sources [:basil/heirloom-cutting]
     :declared-fidelity-checks #{}
     ;; :hardening-log deliberately missing
     :evidence-checklist [:batch-intake-record :propagation-method-log
                          :rooting-rate-test :genetic-fidelity-check
                          :phytosanitary-inspection]}]

   ["PB-2406"
    {:label "ハイブリッドティーローズ 呼び接ぎ / Hybrid tea rose whip-and-tongue graft"
     :propagation-method :graft/whip-and-tongue
     :jurisdiction :us/usda-aphis
     :rooting-percent 76.0
     :hardening-days 30
     :pest-or-disease-detected? false
     :last-phytosanitary-inspection-date (days-ago 100)
     :sanitation-score 86
     :propagation-sources [:rose/hybrid-tea-grafted]
     :declared-fidelity-checks #{:off-type-risk}
     :evidence-checklist full-evidence}]])

(def ^:private batch-order
  "Explicit render order. `propagation.store` keeps `:batches` in a plain
  map and exposes no `all-batches`, so the order is pinned here rather
  than left to map iteration -- required for byte-identical reruns."
  (mapv first demo-batches))

(def ^:private context
  "Actor context passed to `operation/run-operation`. `:hold-fact-fn` is
  the real `governor/hold-fact` -- the OperationActor calls it to mint the
  audit fact for any proposal the Governor refuses."
  {:actor-id "nursery-op-1"
   :actor-role :nursery-operations-coordinator
   :hold-fact-fn governor/hold-fact})

(def ^:private cite-maff
  [{:spec "植物防疫法施行規則" :clause "第10条" :jurisdiction :jp/maff}])
(def ^:private cite-aphis
  [{:spec "7 CFR 319 (USDA-APHIS PPQ)" :clause "319.37-4" :jurisdiction :us/usda-aphis}])
(def ^:private cite-eppo
  [{:spec "EU Plant Health Regulation 2016/2031" :clause "Art.79 Plant Passport"
    :jurisdiction :eu/eppo}])

(def ^:private scenario
  "Scenario INPUT: the ordered proposals fed to the actor. Each step is a
  request + an advisor proposal, plus an optional `:human-signoff` naming
  the nursery operator asked to sign off.

  `:human-signoff` is a REQUEST for a human decision, not a decision. The
  driver honours it only when the Governor's verdict is escalate-only; on
  a hard verdict the driver refuses it, which is how the page demonstrates
  that a HARD hold is un-overridable rather than merely asserting it."
  [{:label "Routine bench/misting maintenance on a clean batch"
    :request {:op :schedule-maintenance :subject "PB-2401"}
    :proposal {:cites cite-maff :value {:jurisdiction :jp/maff}
               :effect :propose :confidence 0.93}}

   {:label "Log the batch into nursery records (real actuation)"
    :request {:op :log-propagation-batch :subject "PB-2401"}
    :proposal {:cites cite-maff :value {:jurisdiction :jp/maff}
               :effect :propose :confidence 0.91}
    :human-signoff "nursery-op-2"}

   {:label "Log the SAME batch a second time (double-commit guard)"
    :request {:op :log-propagation-batch :subject "PB-2401"}
    :proposal {:cites cite-maff :value {:jurisdiction :jp/maff}
               :effect :propose :confidence 0.91}}

   {:label "Coordinate nursery-stock shipment (real actuation)"
    :request {:op :coordinate-shipment :subject "PB-2401"}
    :proposal {:cites cite-maff :value {:jurisdiction :jp/maff}
               :effect :propose :confidence 0.89}
    :human-signoff "nursery-op-2"}

   {:label "Finalize the SAME shipment again (double-commit guard)"
    :request {:op :coordinate-shipment :subject "PB-2401"}
    :proposal {:cites cite-maff :value {:jurisdiction :jp/maff}
               :effect :propose :confidence 0.89}}

   {:label "Advisor tries to drive greenhouse climate control directly"
    :request {:op :control-greenhouse-climate :subject "PB-2401"}
    :proposal {:cites cite-maff :value {:jurisdiction :jp/maff}
               :effect :propose :confidence 0.97}}

   {:label "Advisor claims direct write authority (:effect :commit)"
    :request {:op :schedule-maintenance :subject "PB-2401"}
    :proposal {:cites cite-maff :value {:jurisdiction :jp/maff}
               :effect :commit :confidence 0.95}}

   {:label "Log a micropropagation batch below its rooting floor — human sign-off is attempted anyway"
    :request {:op :log-propagation-batch :subject "PB-2402"}
    :proposal {:cites cite-aphis :value {:jurisdiction :us/usda-aphis}
               :effect :propose :confidence 0.88}
    :human-signoff "nursery-op-2"}

   {:label "Log a grafted batch with an undeclared off-type risk"
    :request {:op :log-propagation-batch :subject "PB-2403"}
    :proposal {:cites cite-eppo :value {:jurisdiction :eu/eppo}
               :effect :propose :confidence 0.9}}

   {:label "Log a batch whose own screening found a pest, quarantine flag still open"
    :request {:op :log-propagation-batch :subject "PB-2404"}
    :proposal {:cites cite-maff :value {:jurisdiction :jp/maff}
               :effect :propose :confidence 0.86}}

   {:label "Flag the quality concern on that batch (never auto-resolved)"
    :request {:op :flag-quality-concern :subject "PB-2404"}
    :proposal {:cites cite-maff :value {:jurisdiction :jp/maff}
               :effect :propose :confidence 0.94}
    :human-signoff "nursery-op-3"}

   {:label "Log an under-hardened batch from an under-sanitized facility"
    :request {:op :log-propagation-batch :subject "PB-2405"}
    :proposal {:cites cite-maff :value {:jurisdiction :jp/maff}
               :effect :propose :confidence 0.84}}

   {:label "Flag a quality concern with no jurisdiction citation"
    :request {:op :flag-quality-concern :subject "PB-2405"}
    :proposal {:cites [] :value {:jurisdiction nil}
               :effect :propose :confidence 0.92}}

   {:label "Log a batch whose phytosanitary inspection has lapsed"
    :request {:op :log-propagation-batch :subject "PB-2406"}
    :proposal {:cites cite-aphis :value {:jurisdiction :us/usda-aphis}
               :effect :propose :confidence 0.9}}

   {:label "Routine maintenance proposed with low advisor confidence"
    :request {:op :schedule-maintenance :subject "PB-2406"}
    :proposal {:cites cite-aphis :value {:jurisdiction :us/usda-aphis}
               :effect :propose :confidence 0.42}
    :human-signoff "nursery-op-3"}

   {:label "Coordinate shipment for a batch this nursery never registered"
    :request {:op :coordinate-shipment :subject "PB-9999"}
    :proposal {:cites cite-maff :value {:jurisdiction :jp/maff}
               :effect :propose :confidence 0.9}}])

;; ─────────────────────────── the actual run ───────────────────────────

(defn seed-store
  "Fresh store value: `demo-batches` registered, empty audit ledger.
  Matches the `{:batches {..} :facts [..]}` shape `propagation.store`
  operates on."
  []
  {:batches (into {} demo-batches) :facts []})

(defn- apply-commit-effect
  "Apply a committed proposal's real store effect through
  `propagation.store`. Only the two high-stakes actuation ops mutate the
  store; `:schedule-maintenance` and `:flag-quality-concern` are
  coordination outputs with no store-level flag in this actor."
  [st {:keys [op subject]}]
  (case op
    :log-propagation-batch (store/log-batch st subject (store/nursery-batch st subject))
    :coordinate-shipment (store/finalize-shipment st subject)
    st))

(defn- escalation-cause
  "Why an escalate-only verdict escalated, read off the REAL verdict and
  the Governor's own published thresholds (`governor/always-escalate-ops`,
  `governor/confidence-floor`) -- not asserted by the scenario."
  [verdict op]
  (cond-> []
    (contains? governor/always-escalate-ops op) (conj :always-escalate-op)
    (< (:confidence verdict) governor/confidence-floor) (conj :low-confidence)))

(defn- run-step
  "Drive ONE proposal through the real OperationActor and fold its real
  output into the store. Returns `[store' trace-entry]`.

  `operation/run-operation` returns `:verdict` only on its not-ok branch;
  `governor/check` is a pure function of exactly the same four inputs, so
  re-running it on the ok branch yields the identical verdict and is used
  purely to surface `:confidence` / `:high-stakes?` for display.

  The caller-side hard-vs-escalate split is the one `propagation.operation`
  documents: today both route through `:hold-fact-fn`, and callers
  distinguish them by inspecting `(:verdict result)`."
  [st {:keys [label request proposal human-signoff]}]
  (let [result (operation/run-operation request context proposal st governor/check)
        verdict (or (:verdict result) (governor/check request context proposal st))
        st (reduce store/append-fact st (:facts result))
        op (:op request)
        subject (:subject request)
        base {:op op :subject subject :actor (:actor-id context)
              :confidence (:confidence verdict)}
        entry {:label label :op op :subject subject
               :confidence (:confidence verdict)
               :high-stakes? (:high-stakes? verdict)}]
    (cond
      (:ok? result)
      [(-> st
           (store/append-fact (assoc base :t :committed :disposition :commit :basis []))
           (apply-commit-effect request))
       (assoc entry :outcome :auto-commit :basis []
              :violations (:violations verdict))]

      (:hard? verdict)
      (let [basis (mapv :rule (:violations verdict))]
        [(if human-signoff
           ;; A human WAS asked. The Governor's hard verdict forbids it, so
           ;; the refusal itself is recorded -- this is the un-overridability
           ;; of a hard hold, executed rather than claimed.
           (store/append-fact st (assoc base :t :approval-refused
                                        :disposition :hard-hold
                                        :approver human-signoff
                                        :basis basis))
           st)
         (assoc entry :outcome (if human-signoff :hard-hold-signoff-refused :hard-hold)
                :basis basis :approver human-signoff
                :violations (:violations verdict))])

      human-signoff
      [(-> st
           (store/append-fact (assoc base :t :approval-granted
                                     :disposition :commit
                                     :approver human-signoff
                                     :basis (escalation-cause verdict op)))
           (apply-commit-effect request))
       (assoc entry :outcome :escalated-approved
              :basis (escalation-cause verdict op)
              :approver human-signoff
              :violations [])]

      :else
      [st (assoc entry :outcome :awaiting-approval
                 :basis (escalation-cause verdict op)
                 :violations [])])))

(defn run-demo!
  "Run the whole scenario through the real actor stack over a fresh seeded
  store. Returns `{:store <store value> :trace [<per-step entry> ..]}`.
  Everything in both is produced by executing `propagation.operation` /
  `propagation.governor` / `propagation.store`."
  []
  (reduce (fn [{:keys [store trace]} step]
            (let [[st' entry] (run-step store step)]
              {:store st' :trace (conj trace (assoc entry :step (inc (count trace))))}))
          {:store (seed-store) :trace []}
          scenario))

;; ───────────────────────────── rendering ─────────────────────────────

(defn- esc [v]
  (-> (str v)
      (str/replace "&" "&amp;")
      (str/replace "<" "&lt;")
      (str/replace ">" "&gt;")
      (str/replace "\"" "&quot;")))

(defn- kw-name [k] (if (keyword? k) (subs (str k) 1) (str k)))

(defn- rules-cell [basis]
  (if (seq basis)
    (str/join "<br>" (map #(str "<code>" (esc (kw-name %)) "</code>") basis))
    "<span class=\"muted\">—</span>"))

(defn- num-cell [v] (str "<span class=\"num\">" (esc v) "</span>"))

;; --- batch table -------------------------------------------------------

(defn- lifecycle-cell [b]
  (cond
    (:shipment-finalized? b) "<span class=\"ok\">logged &amp; shipment finalized</span>"
    (:logged? b) "<span class=\"ok\">logged, shipment open</span>"
    :else "<span class=\"muted\">not logged</span>"))

(defn- rooting-cell
  "Rooting/take rate against the propagation method's floor, recomputed
  here through the same `propagation.facts` lookup the Governor uses."
  [b]
  (let [m (facts/propagation-method-by-id (:propagation-method b))
        floor (:rooting-min-percent m)
        ok? (facts/rooting-in-range? (:rooting-percent b) m)]
    (str "<span class=\"" (if ok? "ok" "critical") "\">"
         (num-cell (:rooting-percent b)) "%</span> "
         "<span class=\"muted\">/ min " (esc floor) "%</span>")))

(defn- hardening-cell [b]
  (let [m (facts/propagation-method-by-id (:propagation-method b))
        floor (:hardening-min-days m)
        ok? (facts/hardening-in-range? (:hardening-days b) m)]
    (str "<span class=\"" (if ok? "ok" "critical") "\">"
         (num-cell (:hardening-days b)) "d</span> "
         "<span class=\"muted\">/ min " (esc floor) "d</span>")))

(defn- phyto-cell
  "Pest screening + quarantine flag + evidence completeness, each read
  back through the same predicates the Governor applied."
  [b]
  (let [bits (cond-> []
               (:pest-or-disease-detected? b)
               (conj "<span class=\"critical\">pest/pathogen detected</span>")

               (and (true? (:quarantine-flag-raised? b))
                    (not (true? (:quarantine-flag-resolved? b))))
               (conj "<span class=\"critical\">quarantine flag open</span>")

               (not (facts/required-evidence-satisfied?
                     (:jurisdiction b) (:evidence-checklist b)))
               (conj "<span class=\"critical\">evidence incomplete</span>")

               (not (facts/genetic-fidelity-declaration-complete?
                     (:jurisdiction b) (:propagation-sources b)
                     (:declared-fidelity-checks b)))
               (conj "<span class=\"critical\">fidelity undeclared</span>"))]
    (if (seq bits)
      (str/join "<br>" bits)
      "<span class=\"ok\">clear</span>")))

(defn- sanitation-cell [b]
  (let [score (:sanitation-score b)]
    (str "<span class=\"" (if (< score 75) "critical" "ok") "\">"
         (num-cell score) "</span> <span class=\"muted\">/ min 75</span>")))

(defn- batch-row [st batch-id]
  (let [b (store/nursery-batch st batch-id)
        m (facts/propagation-method-by-id (:propagation-method b))
        j (facts/jurisdiction-by-id (:jurisdiction b))]
    (str "        <tr><td><code>" (esc batch-id) "</code><br><span class=\"muted\">"
         (esc (:label b)) "</span></td>"
         "<td>" (esc (:name m)) "</td>"
         "<td>" (esc (:name j)) "</td>"
         "<td>" (rooting-cell b) "</td>"
         "<td>" (hardening-cell b) "</td>"
         "<td>" (sanitation-cell b) "</td>"
         "<td>" (phyto-cell b) "</td>"
         "<td>" (lifecycle-cell b) "</td></tr>")))

;; --- run trace ---------------------------------------------------------

(def ^:private outcome-cell
  {:auto-commit "<span class=\"ok\">auto-commit</span>"
   :escalated-approved "<span class=\"ok\">escalated &rarr; human approved &rarr; committed</span>"
   :awaiting-approval "<span class=\"warn\">escalated &middot; awaiting human</span>"
   :hard-hold "<span class=\"critical\">HARD hold</span>"
   :hard-hold-signoff-refused
   "<span class=\"critical\">HARD hold &middot; human sign-off REFUSED</span>"})

(defn- trace-row [{:keys [step label op subject confidence outcome basis approver]}]
  (str "        <tr><td>" (esc step) "</td>"
       "<td><code>" (esc (kw-name op)) "</code><br><span class=\"muted\">" (esc label) "</span></td>"
       "<td><code>" (esc subject) "</code></td>"
       "<td>" (num-cell confidence) "</td>"
       "<td>" (get outcome-cell outcome "<span class=\"muted\">—</span>") "</td>"
       "<td>" (rules-cell basis) "</td>"
       "<td>" (if approver (str "<code>" (esc approver) "</code>") "<span class=\"muted\">—</span>") "</td></tr>"))

;; --- action gate (derived from the live Governor vars) ------------------

(def ^:private op-blurbs
  "STATIC one-line description of what each allowlisted op means. This
  column is documentation of a fixed contract; the gate column beside it
  is derived from the Governor's own vars at render time."
  {:log-propagation-batch "Record a cutting/graft/tissue-culture/seed batch's provenance, method parameters and rooting results into nursery records"
   :schedule-maintenance "Propose greenhouse / irrigation / propagation-bench maintenance"
   :flag-quality-concern "Surface a quality concern (low rooting rate, genetic-fidelity mismatch, pest/pathogen detection)"
   :coordinate-shipment "Finalize shipment of nursery stock"})

(defn- gate-cell [op]
  (cond
    (contains? governor/high-stakes op)
    "<span class=\"warn\">ALWAYS human sign-off &middot; real actuation, never auto at any confidence</span>"
    (contains? governor/always-escalate-ops op)
    "<span class=\"warn\">ALWAYS human sign-off &middot; never auto-resolved by advisor confidence</span>"
    :else
    (str "<span class=\"ok\">auto-commit when the Governor is clean and confidence &ge; "
         (esc governor/confidence-floor) "</span>")))

(defn- action-gate-rows []
  (for [op (sort-by kw-name governor/allowed-ops)]
    (str "        <tr><td><code>" (esc (kw-name op)) "</code></td>"
         "<td>" (gate-cell op) "</td>"
         "<td class=\"muted\">" (esc (get op-blurbs op "")) "</td></tr>")))

;; --- hard-rule catalogue (derived from the run's own ledger) ------------

(defn- hard-rule-rows [ledger]
  (let [violations (->> ledger
                        (filter #(= :governor-hold (:t %)))
                        (mapcat :violations))
        by-rule (group-by :rule violations)]
    (for [[rule vs] (sort-by (comp kw-name key) by-rule)]
      (str "        <tr><td><code>" (esc (kw-name rule)) "</code></td>"
           "<td>" (num-cell (count vs)) "</td>"
           "<td class=\"muted\">" (esc (:detail (first vs))) "</td></tr>"))))

;; --- ledger ------------------------------------------------------------

(def ^:private fact-cell
  {:governor-hold "<span class=\"critical\">governor-hold</span>"
   :committed "<span class=\"ok\">committed</span>"
   :approval-granted "<span class=\"ok\">approval-granted</span>"
   :approval-refused "<span class=\"critical\">approval-refused</span>"})

(def ^:private approval-fact-types
  "The fact types this actor writes that carry a human decision, and are
  therefore the ones on which an approver identity is meaningful."
  #{:approval-granted :approval-refused})

(defn- approver-retention
  "MEASURED at render time, never asserted.

  Several sibling cloud-itonami actors have a store whose commit path
  destructures a narrower key than the caller wrote and so silently
  DROPS the approver identity. When that happens a reader of the console
  cannot distinguish `nobody approved this` from `the store dropped who
  approved it` -- so the console must not simply omit an absent approver,
  it has to say which of the two it is.

  Rather than hardcode a claim about this repo (a hardcoded claim becomes
  a lie the moment somebody changes `propagation.store`), this probes the
  REAL ledger that was just read back out of the store, and the
  disclosure rendered from it flips automatically if the store's
  behaviour ever changes in either direction.

  Returns:
    :approval-facts  how many human-decision facts the run produced
    :with-approver   how many of those still carry `:approver` on readback
    :ledger-retains? approver survives the ledger round-trip
    :record-retains? the BATCH record itself (not just the audit fact)
                     carries the approver -- a strictly stronger property,
                     and a separate question from the ledger one."
  [ledger batches]
  (let [approval-facts (filter #(approval-fact-types (:t %)) ledger)
        named (filter #(some? (:approver %)) approval-facts)
        approved-subjects (into #{} (map :subject) approval-facts)
        record-named (filter (fn [[id b]]
                               (and (approved-subjects id) (some? (:approver b))))
                             batches)]
    {:approval-facts  (count approval-facts)
     :with-approver   (count named)
     :ledger-retains? (and (seq approval-facts)
                           (= (count named) (count approval-facts)))
     :record-retains? (and (seq approved-subjects)
                           (= (count record-named) (count approved-subjects)))}))

(defn- approver-disclosure
  "Prose rendered from the MEASURED `approver-retention` result above."
  [{:keys [approval-facts with-approver ledger-retains? record-retains?]}]
  (str
   "<p class=\"muted\"><strong>Approver attribution (measured on this run, not asserted):</strong> "
   "this scenario produced " (esc approval-facts)
   " human-decision fact(s); " (esc with-approver)
   " of them still carried an <code>:approver</code> when read back out of the store. "
   (if ledger-retains?
     (str "<span class=\"ok\">The audit ledger retains the approver</span> — "
          "<code>propagation.store/append-fact</code> appends the whole fact map, "
          "so the identity survives the round-trip and the <em>Approver</em> column below "
          "is read from the store, not from the scenario input. ")
     (str "<span class=\"critical\">The audit ledger DROPS the approver</span> — "
          "the identity shown in the <em>Approver</em> column is therefore "
          "<em>audit only — not retained in record</em>, joined from the run trace "
          "rather than read back from the store. "))
   (if record-retains?
     "The batch record itself also carries the approver."
     (str "<span class=\"warn\">The batch record itself carries no approver field</span> — "
          "<code>store/log-batch</code> and <code>store/finalize-shipment</code> set only the "
          "<code>:logged?</code> / <code>:shipment-finalized?</code> flags, so "
          "<em>who</em> signed a batch off is answerable only from the ledger, never from the "
          "batch row. That is a real limitation of this store, stated here because it was "
          "observed, and this sentence will change on its own if the store starts recording it."))
   "</p>\n"))

(defn- approver-attribution-cell
  "The Approver column. An approval-bearing fact that came back WITHOUT an
  approver is labelled explicitly rather than blanked, so `nobody
  approved` and `the store dropped it` never render the same way."
  [{:keys [t approver]}]
  (cond
    (some? approver) (str "<code>" (esc approver) "</code>")
    (approval-fact-types t)
    "<span class=\"critical\">(audit only — not retained in record)</span>"
    :else "<span class=\"muted\">—</span>"))

(defn- ledger-row [{:keys [t op subject actor confidence basis] :as fact}]
  (str "        <tr><td>" (get fact-cell t (esc (kw-name t))) "</td>"
       "<td><code>" (esc (kw-name op)) "</code></td>"
       "<td><code>" (esc subject) "</code></td>"
       "<td><code>" (esc actor) "</code></td>"
       "<td>" (approver-attribution-cell fact) "</td>"
       "<td>" (num-cell confidence) "</td>"
       "<td>" (rules-cell basis) "</td></tr>"))

;; --- propagation-method reference (real facts table) --------------------

(defn- method-rows []
  (for [[_ m] (sort-by (comp str key) facts/propagation-method-types)]
    (str "        <tr><td><code>" (esc (kw-name (:id m))) "</code></td>"
         "<td>" (esc (:name m)) "</td>"
         "<td>" (num-cell (:rooting-min-percent m)) "%</td>"
         "<td>" (num-cell (:hardening-min-days m)) "d</td>"
         "<td>" (if (:genetic-fidelity-verification-required m)
                  "<span class=\"warn\">required</span>"
                  "<span class=\"muted\">not required</span>") "</td></tr>")))

(defn render
  "Render the full operator-console document from the `{:store :trace}`
  value `run-demo!` returned. Every table below is built by reading that
  real run back; nothing is hand-typed except the section prose and the
  op-description column of the action-gate table."
  [{:keys [store trace]}]
  (let [ledger (vec (store/audit-trail store))
        holds (filterv #(= :governor-hold (:t %)) ledger)
        hard-holds (filterv #(seq (:basis %)) holds)]
    (str
     "<html><head><meta charset=\"utf-8\">"
     "<title>cloud-itonami-isic-0130 &middot; plant-propagation-nursery</title><style>"
     (jp-go-dds.skin/dds+skin)
     "</style></head><body>\n"
     "<header class=\"bar\">\n"
     "  <h1>Plant propagation nursery (ISIC 0130) — Operator Console</h1>\n"
     "  <span class=\"badge\">read-only sample · governor-gated · batch logging &amp; shipment always human-approved</span>\n"
     "</header>\n"
     "<main>\n"

     "  <section class=\"card\">\n"
     "    <h2>Propagation batches</h2>\n"
     "    <p class=\"muted\">Build-time snapshot, generated from <code>propagation.store</code> by <code>propagation.render-html</code> (<code>clojure -M:dev:render-html</code>). Every threshold comparison below is recomputed through <code>propagation.facts</code> / <code>propagation.registry</code> — the same lookups the Governor used when it ruled on these batches.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Batch</th><th>Propagation method</th><th>Jurisdiction</th><th>Rooting/take rate</th><th>Hardening-off</th><th>Sanitation</th><th>Phytosanitary &amp; fidelity</th><th>Lifecycle</th></tr></thead>\n"
     "      <tbody>\n"
     (str/join "\n" (map (partial batch-row store) batch-order)) "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"

     "  <section class=\"card\">\n"
     "    <h2>Run trace — every proposal this scenario made</h2>\n"
     "    <p class=\"muted\">Each row is one proposal driven through <code>propagation.operation/run-operation</code> against <code>propagation.governor/check</code>. A <strong>HARD hold</strong> is un-overridable: where a human sign-off was requested on one anyway (step 8), the driver refused it and wrote an <code>approval-refused</code> fact instead of committing.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>#</th><th>Op</th><th>Batch</th><th>Advisor confidence</th><th>Governor disposition</th><th>Basis</th><th>Human</th></tr></thead>\n"
     "      <tbody>\n"
     (str/join "\n" (map trace-row trace)) "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"

     "  <section class=\"card\">\n"
     "    <h2>Action gate (Nursery Operations Governor)</h2>\n"
     "    <p class=\"muted\">Gate column derived at render time from <code>governor/allowed-ops</code>, <code>governor/high-stakes</code>, <code>governor/always-escalate-ops</code> and <code>governor/confidence-floor</code>. Anything outside this closed allowlist — greenhouse/irrigation/propagation-equipment control, or phytosanitary-certification authority — is refused unconditionally as <code>op-not-allowed</code>, whatever the advisor's confidence.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Op</th><th>Gate</th><th>What it does</th></tr></thead>\n"
     "      <tbody>\n"
     (str/join "\n" (action-gate-rows)) "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"

     "  <section class=\"card\">\n"
     "    <h2>Hard holds raised by this run</h2>\n"
     "    <p class=\"muted\">Rules and Japanese operator detail text taken verbatim from the <code>:violations</code> the Governor emitted. None of these can be released by a human sign-off — the batch record has to change.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Rule</th><th>Times raised</th><th>Governor detail (first occurrence)</th></tr></thead>\n"
     "      <tbody>\n"
     (str/join "\n" (hard-rule-rows ledger)) "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"

     "  <section class=\"card\">\n"
     "    <h2>Propagation-method quality windows</h2>\n"
     "    <p class=\"muted\">Reference data read straight out of <code>propagation.facts/propagation-method-types</code> — these are the floors the Governor enforces independently of the advisor.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Method</th><th>Name</th><th>Min rooting/take rate</th><th>Min hardening-off</th><th>Genetic-fidelity verification</th></tr></thead>\n"
     "      <tbody>\n"
     (str/join "\n" (method-rows)) "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"

     "  <section class=\"card\">\n"
     "    <h2>Audit ledger (this run)</h2>\n"
     "    <p class=\"muted\">Append-only decision-fact log held in the store's <code>:facts</code> vector. Rows with an empty basis are escalate-only verdicts — <code>propagation.operation</code> routes both hard and escalate verdicts through <code>hold-fact-fn</code> today, and the caller distinguishes them by the verdict's <code>:hard?</code> flag.</p>\n"
     "    " (approver-disclosure (approver-retention ledger (:batches store)))
     "    <table>\n"
     "      <thead><tr><th>Fact</th><th>Op</th><th>Batch</th><th>Actor</th><th>Approver</th><th>Confidence</th><th>Basis</th></tr></thead>\n"
     "      <tbody>\n"
     (str/join "\n" (map ledger-row ledger)) "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"
     "</main>\n"
     "<footer>\n"
     "  <p>" (count trace) " proposals · " (count ledger) " audit facts · "
     (count hard-holds) " hard holds · " (count batch-order) " registered batches.\n"
     "  Regenerate with <code>clojure -M:dev:render-html docs/samples/operator-console.html</code>.</p>\n"
     "</footer>\n"
     "</body></html>\n")))

(defn- hard-hold-facts
  "The `:governor-hold` facts that carry at least one rule -- i.e. the ones
  the Governor refused on a HARD violation, as distinct from the
  escalate-only verdicts that reach the same fact type with an empty
  `:basis`."
  [ledger]
  (filter #(and (= :governor-hold (:t %)) (seq (:basis %))) ledger))

(defn- assert-hard-holds!
  "BUILD-TIME INVARIANT, not a comment.

  The whole point of this console is to show a Governor that actually
  refuses things, so a run that produced no HARD hold has not demonstrated
  anything and MUST NOT be allowed to write a plausible-looking page. This
  is the failure mode that silently turns a demo into theatre: the page
  still renders, the tables still fill, and nothing tells the reader that
  every gate happened to pass.

  Throws unless the REAL Governor output contains at least one
  `:governor-hold` fact with a non-empty `:basis`."
  [ledger]
  (let [hard (hard-hold-facts ledger)
        rules (into (sorted-set) (mapcat :basis) hard)]
    (when (empty? hard)
      (throw (ex-info
              (str "Refusing to write docs/samples/operator-console.html: the run "
                   "produced ZERO hard governor holds, so the page would assert a "
                   "governed pipeline it never actually exercised. Either the "
                   "scenario stopped triggering violations or propagation.governor "
                   "stopped raising them -- fix that before regenerating.")
              {:ledger-facts (count ledger)
               :governor-holds (count (filter #(= :governor-hold (:t %)) ledger))
               :hard-holds 0})))
    {:hard-holds (count hard) :hard-hold-rules rules}))

(defn -main [& args]
  (let [out (or (first args) "docs/samples/operator-console.html")
        run (run-demo!)
        ledger (store/audit-trail (:store run))
        {:keys [hard-holds hard-hold-rules]} (assert-hard-holds! ledger)
        hard hard-holds
        html (render run)]
    (io/make-parents out)
    (spit out html)
    (println "hard-hold rules exercised:" (count hard-hold-rules)
             (mapv kw-name hard-hold-rules))
    (println "wrote" out "(" (count (:trace run)) "proposals,"
             (count ledger) "ledger facts,"
             hard "hard holds,"
             (count (filter #(= :approval-granted (:t %)) ledger)) "human-approved commits,"
             (count (filter #(= :approval-refused (:t %)) ledger)) "sign-offs refused )")))
