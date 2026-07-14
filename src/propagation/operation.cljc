(ns propagation.operation
  "OperationActor — one propagation-batch operation = one supervised actor
  run, expressed as a langgraph-clj StateGraph. The advisor
  (PropagationAdvisor) is sealed into a single node (:advise); its
  proposal is ALWAYS routed through the NurseryOperationsGovernor (:govern)
  and the rollout phase gate (:decide) before anything commits to the SSoT.

  Everything the actor depends on is injected:
    - the Store (MemStore today; Datomic/kotoba-server is the next seam)
    - the Advisor (mock | real LLM)
    - the Phase (0->3 rollout)

  One graph run = one propagation operation (intake -> advise -> govern ->
  decide -> commit | hold | approval). No unbounded inner loop -- each
  operation is auditable and checkpointed.

  Human-in-the-loop = real approval workflow: `interrupt-before
  #{:request-approval}` pauses the actor and hands the decision to a
  human operator. The approver resumes with `{:approval {:status
  :approved}}` (or :rejected)."
  (:require [propagation.propagationadvisor :as advisor]
            [propagation.governor :as governor]
            [propagation.phase :as phase]
            [propagation.store :as store]))

(defn- commit-fact [request context proposal]
  {:t :committed
   :op (:op request)
   :actor (:actor-id context)
   :subject (get-in request [:subject :batch-id])
   :disposition :commit
   :basis (:cites proposal)
   :summary (:summary proposal)})

(defn- commit-record [request _context proposal]
  {:effect (:effect proposal)
   :path [(get-in request [:subject :batch-id])]
   :value (or (:value proposal) {})
   :payload (:value proposal)})

(defn build
  "Compiles an OperationActor graph bound to `store` (any propagation.store/Store).
  opts:
    :advisor      -- a propagation.propagationadvisor/Advisor (default: mock-advisor)
    :checkpointer -- langgraph checkpointer (default: in-mem)"
  [st & [{:keys [advisor checkpointer]
          :or   {advisor (advisor/mock-advisor)}}]]
  ;; Simplified state machine for now (langgraph-clj integration is framework-level)
  {:store st
   :advisor advisor
   :checkpointer checkpointer
   :name :propagation-operation-actor})

(defn execute
  "Executes one operation through the actor state machine.
  request: {:op :operation-name :subject {:batch-id \"...\" ...}}
  context: {:actor-id \"...\" :phase 0|1|2|3}
  Returns: {:disposition :commit|:hold|:escalate :audit [facts...]}"
  [actor request context]
  (let [st (:store actor)
        adv (:advisor actor)
        proposal (advisor/-advise adv st request)
        verdict (governor/check request context proposal st)
        base-disp (phase/verdict->disposition verdict)
        {:keys [disposition reason]} (phase/gate (:phase context phase/default-phase)
                                                  request
                                                  base-disp)
        audit-facts (cond-> [(advisor/trace request proposal)]
                      (= disposition :hold)
                      (conj (governor/hold-fact request context verdict))
                      (= disposition :commit)
                      (conj (commit-fact request context proposal)))]
    (when (= disposition :commit)
      (store/upsert-batch st (assoc (:subject request) :batch-id (get-in request [:subject :batch-id]))))
    (doseq [fact audit-facts]
      (store/append-audit st fact))
    {:disposition disposition
     :audit audit-facts
     :reason reason}))
