(ns textiletrade.render-html
  "Build-time HTML renderer for `docs/samples/operator-console.html`.

  Drives the REAL TextileTrade OperationActor (`textiletrade.operation/
  build` -> a compiled langgraph-clj StateGraph) over the REAL seeded
  store (`textiletrade.store/seed-db`), through the REAL Textile Trading
  Governor (`textiletrade.governor/check`) and the REAL rollout phase
  gate (`textiletrade.phase/gate`), and renders whatever those actually
  produced. Nothing on the page is a hand-typed copy of a run:

    - every textile-order row is read back out of the store AFTER the
      run (`store/all-textile-orders`), so `:dispatched?` /
      `:invoiced?` / `:dispatch-number` / `:invoice-number` are the
      post-run ground truth, not a prediction,
    - every HARD-hold rule name and every violation detail string is
      the governor's own `:violations` entry off the append-only ledger
      -- there is no rule text literal in this namespace,
    - the audit-ledger table is `store/ledger` verbatim, in order,
    - the dispatch/invoice draft tables are `store/dispatch-history` /
      `store/invoice-history` (the `textiletrade.registry` drafts),
    - the phase table is derived from `textiletrade.phase/phases`, the
      high-stakes set from `textiletrade.governor/high-stakes`, the
      confidence floor from `textiletrade.governor/confidence-floor`,
      and both jurisdiction catalogs from `textiletrade.facts` public
      vars.

  Subject provenance (the demo may not invent subjects): every subject
  driven below is one of the eight textile-orders actually seeded by
  `textiletrade.store/demo-data` -- `to-1` .. `to-8`. Verified against
  the seed before this file was written, and re-verified at run time by
  `assert-seeded-subjects!`.

  Deterministic: no clock, no randomness, no network, no timestamps in
  the page content. Sets (which have no order) are sorted before
  rendering. Re-running writes a byte-identical file.

  Run: `clojure -M:dev:render-html [out-file]`
  (default out-file `docs/samples/operator-console.html`)."
  (:require [clojure.string :as str]
            [jp-go-dds.skin]
            [langgraph.graph :as g]
            [textiletrade.facts :as facts]
            [textiletrade.governor :as governor]
            [textiletrade.operation :as op]
            [textiletrade.phase :as phase]
            [textiletrade.store :as store]))

;; ----------------------------- the run -----------------------------

(def ^:private operator
  {:actor-id "op-1" :actor-role :trading-supervisor :phase phase/default-phase})

(def ^:private scenarios
  "One entry = one textile-wholesale operation driven through the real
  compiled actor. `:approval`, when present, is the human decision
  handed back to the graph while it is paused at `:request-approval`
  (`interrupt-before`). Adapted from this repo's own
  `textiletrade.sim` demo driver (`clojure -M:dev:run`, run and read
  BEFORE this file was written), extended with an `:evidence-incomplete`
  scenario and a human VETO so every one of the governor's eight HARD
  checks and all three dispositions appear on one page."
  [{:tid "t01"
    :exercises "Intake of the clean apparel order. Governor-clean at confidence 0.97 and :order/intake is the ONE op in phase 3's :auto set -> auto-commit, no human."
    :request {:op :order/intake :subject "to-1"
              :patch {:id "to-1" :counterparty "Meridian Apparel Wholesale LLC"}}}

   {:tid "t02"
    :exercises "Per-jurisdiction counterparty-diligence checklist for USA (Tariff Act / OFAC / TFPIA / FFA) plus the UFLPA forced-labor citation. Governor-clean but not auto-eligible at any phase -> escalates; the human approves."
    :request {:op :supply-chain/verify :subject "to-1"}
    :approval {:status :approved :by "op-1"}}

   {:tid "t03"
    :exercises "Dispatch of real goods from the wholesale warehouse. Governor-clean, evidence complete, yet :delivery/dispatch is ABSENT from every phase's :auto set -> always escalates; the human trading supervisor approves and the dispatch draft is registered."
    :request {:op :delivery/dispatch :subject "to-1"}
    :approval {:status :approved :by "op-1"}}

   {:tid "t04"
    :exercises "Invoice settlement -- real money between counterparty and trader. Same permanent posture as dispatch -> always escalates; the human approves."
    :request {:op :invoice/settle :subject "to-1"}
    :approval {:status :approved :by "op-1"}}

   {:tid "t05"
    :exercises "Verification for a jurisdiction (ATL) with NO official spec-basis in textiletrade.facts. The advisor refuses to invent requirements and cites nothing; the governor HARD-holds. Never reaches a human."
    :request {:op :supply-chain/verify :subject "to-2"}}

   {:tid "t06"
    :exercises "Dispatch of that same never-verified order. No committed supply-chain assessment exists, so the jurisdiction's required evidence checklist is unsatisfied. HARD hold."
    :request {:op :delivery/dispatch :subject "to-2"}
    :approval {:status :approved :by "op-1"}}

   {:tid "t07"
    :exercises "Verification for the footwear counterparty -- clean, approved. Sets up t08 so its hold isolates exactly ONE failure mode."
    :request {:op :supply-chain/verify :subject "to-3"}
    :approval {:status :approved :by "op-1"}}

   {:tid "t08"
    :exercises "Dispatch to a counterparty whose credit has NOT been cleared. Read off a dedicated :credit-cleared? fact, evaluated at the warehouse ahead of any physical pick. HARD hold."
    :request {:op :delivery/dispatch :subject "to-3"}}

   {:tid "t09"
    :exercises "Verification for the clothing counterparty -- clean, approved. Sets up t10."
    :request {:op :supply-chain/verify :subject "to-4"}
    :approval {:status :approved :by "op-1"}}

   {:tid "t10"
    :exercises "Dispatch of an order with no contract-terms on file. HARD hold."
    :request {:op :delivery/dispatch :subject "to-4"}}

   {:tid "t11"
    :exercises "Verification for the sanctions-unscreened counterparty -- the jurisdiction checklist itself is clean, so the hold in t12 is isolated. Approved."
    :request {:op :supply-chain/verify :subject "to-5"}
    :approval {:status :approved :by "op-1"}}

   {:tid "t12"
    :exercises "Dispatch to a counterparty that has NOT passed OFAC / equivalent sanctions screening. Evaluated unconditionally at BOTH actuation ops. HARD hold."
    :request {:op :delivery/dispatch :subject "to-5"}}

   {:tid "t13"
    :exercises "Verification for the Xinjiang-origin fabric order (USA jurisdiction) -- the checklist draft carries the UFLPA citation. Approved; sets up the domain-defining hold in t14."
    :request {:op :supply-chain/verify :subject "to-6"}
    :approval {:status :approved :by "op-1"}}

   {:tid "t14"
    :exercises "THE DOMAIN-DEFINING CHECK. Dispatch of Xinjiang-origin fabric into the United States with NEITHER a documented supply-chain trace NOR a clear-and-convincing rebuttal dossier. The UFLPA rebuttable presumption stands unrebutted. HARD hold, un-overridable, never reaches a human."
    :request {:op :delivery/dispatch :subject "to-6"}
    :approval {:status :approved :by "op-1"}}

   {:tid "t15"
    :exercises "Verification for the SAME Xinjiang origin, this time with both rebuttal facts documented. Approved."
    :request {:op :supply-chain/verify :subject "to-7"}
    :approval {:status :approved :by "op-1"}}

   {:tid "t16"
    :exercises "CONTROL 1 -- the presumption is genuinely REBUTTABLE, not a blanket regional ban: the same flagged origin as t14 dispatches once BOTH a supply-chain trace and a clear-and-convincing rebuttal dossier are on file. Still escalates for the ordinary human sign-off."
    :request {:op :delivery/dispatch :subject "to-7"}
    :approval {:status :approved :by "op-1"}}

   {:tid "t17"
    :exercises "Verification for the Xinjiang-origin order bound for JPN. Approved."
    :request {:op :supply-chain/verify :subject "to-8"}
    :approval {:status :approved :by "op-1"}}

   {:tid "t18"
    :exercises "CONTROL 2 + human veto. Same flagged origin, NEITHER rebuttal fact on file, but JPN carries no currently-binding forced-labor import-ban statute, so the check is not triggered and the governor is clean -- proving the check is JURISDICTION-gated. The human trading supervisor then DECLINES anyway: a veto is not a compliance violation, and is written to the ledger as :approval-rejected, not :governor-hold."
    :request {:op :delivery/dispatch :subject "to-8"}
    :approval {:status :rejected :by "op-1"}}

   {:tid "t19"
    :exercises "Dispatching the SAME order twice. Guarded off a dedicated :dispatched? fact, never a :status value. HARD hold."
    :request {:op :delivery/dispatch :subject "to-1"}}

   {:tid "t20"
    :exercises "Settling the SAME order's invoice twice. Guarded off a dedicated :invoiced? fact. HARD hold."
    :request {:op :invoice/settle :subject "to-1"}}])

(defn- assert-seeded-subjects!
  "The demo may not invent subjects. Every `:subject` driven above must
  exist in `textiletrade.store/demo-data`'s seed, or the ledger this
  page renders would be about orders that do not exist."
  [db]
  (let [seeded (set (map :id (store/all-textile-orders db)))
        used   (set (map (comp :subject :request) scenarios))
        unknown (sort (remove seeded used))]
    (when (seq unknown)
      (throw (ex-info "scenario drives subjects that are not in the store seed"
                      {:unknown unknown :seeded (sort seeded)})))))

(defn- drive!
  "Runs one scenario through the real compiled graph and returns the
  scenario enriched with what the graph actually did."
  [actor {:keys [tid request approval] :as scenario}]
  (let [r1 (g/run* actor {:request request :context operator} {:thread-id tid})
        paused? (= :interrupted (:status r1))
        r2 (when (and approval paused?)
             (g/run* actor {:approval approval} {:thread-id tid :resume? true}))
        final (:state (or r2 r1))
        audit (:audit final [])]
    (assoc scenario
           :verdict (:verdict final)
           :paused? paused?
           :escalation (first (filter #(= :approval-requested (:t %)) audit))
           :human (when r2 (:status approval))
           :disposition (:disposition final))))

(defn run-demo!
  "Seeds a MemStore, builds the REAL actor, drives every scenario above.
  Returns {:db store :runs [..]}. Every number, row and status on the
  rendered page comes out of `:db` / `:runs` -- this function is the
  only source of page content."
  []
  (let [db (store/seed-db)
        _ (assert-seeded-subjects! db)
        actor (op/build db)]
    {:db db :runs (mapv #(drive! actor %) scenarios)}))

;; ----------------------------- html helpers -----------------------------

(defn- esc [v]
  (-> (str v)
      (str/replace "&" "&amp;")
      (str/replace "<" "&lt;")
      (str/replace ">" "&gt;")
      (str/replace "\"" "&quot;")))

(defn- fmt
  "Render a stored value, or an em dash when the domain model carries no
  value for that field on that record."
  [v]
  (if (nil? v) "—" (esc v)))

(defn- code [v] (str "<code>" (esc v) "</code>"))

(defn- codes
  "Render a SEQUENCE of values in the order the code produced it -- used
  for `:basis`, whose order is the governor's own evaluation order."
  [coll]
  (if (seq coll) (str/join " " (map code coll)) "<span class=\"muted\">—</span>"))

(defn- kw-codes
  "Render a SET. Sorted, because a set has no order and an unsorted
  render would make the output non-deterministic."
  [coll]
  (if (seq coll)
    (str/join " " (map code (sort-by str coll)))
    "<span class=\"muted\">(empty)</span>"))

(defn- flag [v]
  (if (true? v)
    "<span class=\"ok\">true</span>"
    (str "<span class=\"err\">" (if (nil? v) "—" (esc v)) "</span>")))

(defn- tr [& cells] (str "<tr>" (apply str (map #(str "<td>" % "</td>") cells)) "</tr>"))

(defn- table [headers rows]
  (str "<table><thead><tr>"
       (apply str (map #(str "<th>" (esc %) "</th>") headers))
       "</tr></thead><tbody>\n"
       (str/join "\n" rows)
       "\n</tbody></table>"))

(defn- card [title note body]
  (str "<section class=\"card\"><h2>" (esc title) "</h2>"
       (when note (str "<p class=\"muted\">" note "</p>"))
       body "</section>"))

;; ----------------------------- sections -----------------------------

(defn- ledger-of [db] (vec (store/ledger db)))

(defn- holds [db]
  (filterv #(= :governor-hold (:t %)) (ledger-of db)))

(defn- summary-section [db runs]
  (let [led (ledger-of db)
        n (fn [t] (count (filter #(= t (:t %)) led)))]
    (card "Run summary"
          (str "Every number below is a count over the actor's own append-only ledger after "
               "driving " (count runs) " operations through " (code "textiletrade.operation/build")
               ". Nothing here is entered by hand.")
          (table ["Measure" "Value"]
                 [(tr "operations driven" (str "<span class=\"num\">" (count runs) "</span>"))
                  (tr "ledger facts" (str "<span class=\"num\">" (count led) "</span>"))
                  (tr "commits" (str "<span class=\"num ok\">" (n :committed) "</span>"))
                  (tr "governor HARD holds" (str "<span class=\"num critical\">" (n :governor-hold) "</span>"))
                  (tr "human rejections (veto, not a violation)"
                      (str "<span class=\"num warn\">" (n :approval-rejected) "</span>"))
                  (tr "human approvals granted"
                      (str "<span class=\"num\">" (count (filter #(= :approved (:human %)) runs)) "</span>"))
                  (tr "dispatch drafts registered"
                      (str "<span class=\"num\">" (count (store/dispatch-history db)) "</span>"))
                  (tr "invoice drafts registered"
                      (str "<span class=\"num\">" (count (store/invoice-history db)) "</span>"))])
          )))

(defn- verdict-cell [{:keys [verdict]}]
  (cond
    (nil? verdict) "<span class=\"muted\">—</span>"
    (:hard? verdict)
    (str "<span class=\"critical\">HARD</span> "
         (codes (map :rule (:violations verdict))))
    (:escalate? verdict)
    (str "<span class=\"warn\">escalate</span>"
         (when (:high-stakes? verdict) " <span class=\"muted\">high-stakes</span>"))
    :else (str "<span class=\"ok\">clean</span> <span class=\"muted\">conf "
               (esc (:confidence verdict)) "</span>")))

(defn- human-cell [{:keys [approval human paused?]}]
  (cond
    (= :approved human) "<span class=\"ok\">approved</span>"
    (= :rejected human) "<span class=\"err\">rejected</span>"
    (and approval (not paused?))
    "<span class=\"muted\">never offered (HARD hold, no interrupt)</span>"
    :else "<span class=\"muted\">—</span>"))

(defn- disposition-cell [{:keys [disposition]}]
  (case disposition
    :commit "<span class=\"ok\">commit</span>"
    :hold "<span class=\"critical\">hold</span>"
    :escalate "<span class=\"warn\">escalate</span>"
    (str "<span class=\"muted\">" (fmt disposition) "</span>")))

(defn- timeline-section [runs]
  (card "Operation timeline"
        (str "One row = one " (code "langgraph.graph/run*") " over the compiled actor. The "
             "governor column is the verdict map the governor itself returned; the human column "
             "is the decision handed back to the graph while it was paused at "
             (code ":request-approval") ". A HARD hold never pauses, so no human is ever offered it.")
        (table ["Thread" "Op" "Order" "Governor" "Human" "Final" "What this exercises"]
               (for [{:keys [tid request escalation exercises] :as r} runs]
                 (tr (code tid)
                     (code (:op request))
                     (code (:subject request))
                     (verdict-cell r)
                     (human-cell r)
                     (str (disposition-cell r)
                          (when-let [reason (:reason escalation)]
                            (str " <span class=\"muted\">after escalation " (code reason) "</span>")))
                     (str "<span class=\"muted\">" (esc exercises) "</span>"))))))

(defn- holds-section [db]
  (let [hs (holds db)]
    (card "Governor HARD holds"
          (str "Each row is one " (code ":violations") " entry of a " (code ":governor-hold")
               " fact on the append-only ledger. The rule name and the detail text are the "
               "governor's own output — this page holds no rule text of its own. HARD holds "
               "cannot be overridden by any approver at any phase.")
          (table ["Rule" "Op" "Order" "Confidence" "Governor's own detail"]
                 (for [h hs
                       v (:violations h)]
                   (tr (str "<span class=\"critical\">" (esc (:rule v)) "</span>")
                       (code (:op h))
                       (code (:subject h))
                       (str "<span class=\"num\">" (fmt (:confidence h)) "</span>")
                       (esc (:detail v))))))))

(defn- rejections-section [db]
  (let [rs (filterv #(= :approval-rejected (:t %)) (ledger-of db))]
    (when (seq rs)
      (card "Human vetoes"
            (str "A governor-clean proposal a person declined. Written to the ledger by the same "
                 (code ":hold") " node, but with basis " (code ":approver-rejected") " — not a "
                 "compliance violation, and counted separately from the HARD holds above.")
            (table ["Op" "Order" "Basis" "Confidence"]
                   (for [r rs]
                     (tr (code (:op r)) (code (:subject r))
                         (codes (:basis r))
                         (str "<span class=\"num\">" (fmt (:confidence r)) "</span>"))))))))

(defn- order-row [db {:keys [id order-id goods-kind counterparty origin-region jurisdiction
                            credit-cleared? contract-terms sanctions-screened?
                            supply-chain-traceability-documented?
                            forced-labor-rebuttal-evidence-on-file?
                            dispatched? invoiced? dispatch-number invoice-number]
                     :as to}]
  (tr (code id)
      (esc order-id)
      (esc goods-kind)
      (esc counterparty)
      (esc origin-region)
      (code jurisdiction)
      (flag credit-cleared?)
      (fmt contract-terms)
      (flag sanctions-screened?)
      ;; the forced-labor presumption is re-evaluated here off the SAME
      ;; predicate the governor gates on, against the post-run record
      (if (facts/forced-labor-presumption-triggered? to)
        (if (and (true? supply-chain-traceability-documented?)
                 (true? forced-labor-rebuttal-evidence-on-file?))
          "<span class=\"ok\">triggered · rebutted</span>"
          "<span class=\"critical\">triggered · UNREBUTTED</span>")
        "<span class=\"muted\">not triggered</span>")
      (flag dispatched?)
      (fmt dispatch-number)
      (flag invoiced?)
      (fmt invoice-number)
      (if (store/assessment-of db id)
        "<span class=\"ok\">on file</span>"
        "<span class=\"muted\">none</span>")))

(defn- orders-section [db]
  (card "Textile-orders (store state AFTER the run)"
        (str "Read back out of " (code "textiletrade.store/all-textile-orders")
             " once every operation above had finished, so the dispatch/invoice columns are "
             "ground truth, not a prediction. Only the orders the governor cleared AND a human "
             "approved carry a registered draft number.")
        (table ["ID" "Order" "Goods" "Counterparty" "Origin" "Juris."
                "Credit" "Contract" "Sanctions" "Forced-labor presumption"
                "Dispatched" "Dispatch #" "Invoiced" "Invoice #" "Assessment"]
               (map (partial order-row db) (store/all-textile-orders db)))))

(defn- ledger-section [db]
  (card "Audit ledger (append-only, this run)"
        (str "Every decision fact " (code "textiletrade.operation") " wrote, in order. "
             (code ":committed") " facts carry the advisor's own summary; "
             (code ":governor-hold") " facts carry the governor's own rule vector.")
        (table ["#" "Fact" "Op" "Order" "Disposition" "Basis / summary"]
               (map-indexed
                (fn [i {:keys [t op subject disposition basis summary violations]}]
                  (tr (str "<span class=\"num\">" i "</span>")
                      (if (= :governor-hold t)
                        (str "<span class=\"critical\">" (esc t) "</span>")
                        (str "<span class=\"ok\">" (esc t) "</span>"))
                      (code op)
                      (code subject)
                      (esc disposition)
                      (if (seq violations)
                        (codes (map :rule violations))
                        (or (some-> summary esc) (codes basis)))))
                (ledger-of db)))))

(defn- drafts-section [db]
  (let [ds (store/dispatch-history db)
        is (store/invoice-history db)]
    (card "Registered drafts (textiletrade.registry)"
          (str "The book-of-record DRAFTS the two actuation ops produced — jurisdiction-scoped "
               "sequence numbers built by pure functions, with an UNSIGNED certificate: signature "
               "is the operator's act, not this actor's. A draft exists only where the governor "
               "cleared the op AND a human approved it.")
          (table ["Kind" "Record id" "Order" "Jurisdiction" "Immutable"]
                 (for [r (concat ds is)]
                   (tr (esc (get r "kind"))
                       (code (get r "record_id"))
                       (code (get r "textile_order_id"))
                       (code (get r "jurisdiction"))
                       (flag (get r "immutable"))))))))

(defn- phase-section []
  (card "Rollout phase gate (textiletrade.phase)"
        (str "Derived from the " (code "textiletrade.phase/phases") " var itself. "
             (code ":delivery/dispatch") " and " (code ":invoice/settle") " are deliberately "
             "absent from EVERY phase's auto set, including phase 3 — a permanent structural "
             "fact, enforced independently a second time by the governor's high-stakes set "
             (kw-codes governor/high-stakes) ". This run used phase "
             (code phase/default-phase) ".")
        (table ["Phase" "Label" "May write" "May auto-commit when governor-clean"]
               (for [[p {:keys [label writes auto]}] (sort-by key phase/phases)]
                 (tr (str "<span class=\"num\">" (esc p) "</span>")
                     (esc label)
                     (kw-codes writes)
                     (kw-codes auto))))))

;; The ONLY hand-written content on this page: a static description of
;; this actor's fixed op-gate contract (README `Ops` / `Actuation`,
;; `textiletrade.governor`, `textiletrade.phase`). It documents fixed
;; behaviour rather than reporting runtime telemetry, so it is
;; legitimately described rather than derived. Everything else on the
;; page comes out of the real run.
(def ^:private op-gate-rows
  [(tr (code :order/intake)
       "<span class=\"ok\">phase-3 auto-commit when governor-clean and above the confidence floor — no capital risk</span>")
   (tr (code :supply-chain/verify)
       "<span class=\"warn\">human approval — never auto-eligible; spec-basis citation required, never invented</span>")
   (tr (code :delivery/dispatch)
       "<span class=\"warn\">ALWAYS human approval · never auto at any phase</span> — credit-clearance, contract-on-file, forced-labor presumption (jurisdiction-gated) and sanctions screening all re-verified off the order's own facts")
   (tr (code :invoice/settle)
       "<span class=\"warn\">ALWAYS human approval · never auto at any phase</span> — evidence completeness and sanctions screening re-verified; double-settlement refused off a dedicated flag")])

(defn- op-gate-section []
  (card "Op gate contract"
        (str "Fixed contract of this actor's closed op set (static documentation of behaviour, "
             "not runtime telemetry). Confidence floor " (code governor/confidence-floor)
             "; a proposal below it escalates rather than commits.")
        (table ["Op" "Gate"] op-gate-rows)))

(defn- jurisdiction-section []
  (let [cov (facts/coverage)]
    (card "Jurisdiction spec-basis catalog (textiletrade.facts/catalog)"
          (str "The G2-style citation table the governor checks every verification proposal "
               "against. Coverage is reported honestly: " (code (:covered cov)) " of "
               (code (:requested cov)) " seeded — a jurisdiction absent from this table has NO "
               "spec-basis, full stop, and the governor holds any proposal that tries to invent "
               "one (thread " (code "t05") " above).")
          (table ["ISO3" "Owner authority" "Legal basis" "Required evidence"]
                 (for [[iso3 {:keys [owner-authority legal-basis required-evidence]}]
                       (sort-by key facts/catalog)]
                   (tr (code iso3) (esc owner-authority) (esc legal-basis)
                       (str/join "<br>" (map esc required-evidence))))))))

(defn- forced-labor-section []
  (card "Forced-labor import-ban basis (textiletrade.facts/forced-labor-import-ban-basis)"
        (str "A SEPARATE catalog from the general one above, because the mechanism is different "
             "in kind: UFLPA / 19 U.S.C. §1307 empower CBP to detain and exclude merchandise AT "
             "THE BORDER — the exact customs-entry act " (code ":delivery/dispatch") " represents "
             "when the order's own jurisdiction is the United States. That is why the check is "
             "gated on the ORDER's own jurisdiction. " (code ":binding?") " records whether the "
             "statute is CURRENTLY enforceable, not merely enacted — the governor gates on "
             "binding, not on presence. Flagged origins: "
             (kw-codes facts/flagged-origins) ".")
        (table ["ISO3" "Owner authority" "Legal basis" "Currently binding?"]
               (for [[iso3 {:keys [owner-authority legal-basis binding?]}]
                     (sort-by key facts/forced-labor-import-ban-basis)]
                 (tr (code iso3) (esc owner-authority) (esc legal-basis) (flag binding?))))))

;; ----------------------------- document -----------------------------

(defn render
  "Renders the whole operator-console document from the result of
  `run-demo!`. Every row, number and status below is derived from that
  real run; the only hand-written content is `op-gate-rows` (labelled
  as such at its definition)."
  [{:keys [db runs]}]
  (str "<!doctype html>\n"
       "<html lang=\"en\"><head><meta charset=\"utf-8\">"
       "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">"
       "<title>cloud-itonami-isic-4641 · textile/apparel/footwear wholesale — Operator Console</title>"
       "<style>" (jp-go-dds.skin/dds+skin) "</style></head><body>\n"
       "<header class=\"bar\">"
       "<h1>Wholesale of textiles, clothing and footwear (ISIC 4641) — Operator Console</h1>"
       "<span class=\"badge\">generated · governor-gated · dispatch &amp; settlement always human-approved</span>"
       "</header>\n"
       "<p class=\"subtitle\">Build-time snapshot generated by <code>textiletrade.render-html</code> "
       "(<code>clojure -M:dev:render-html</code>) by driving the real "
       "<code>textiletrade.operation</code> actor over a freshly seeded "
       "<code>textiletrade.store</code>. Deterministic — no clock, no randomness, no network; "
       "re-running produces a byte-identical file.</p>\n"
       "<main>\n"
       (summary-section db runs) "\n"
       (timeline-section runs) "\n"
       (holds-section db) "\n"
       (rejections-section db) "\n"
       (orders-section db) "\n"
       (drafts-section db) "\n"
       (ledger-section db) "\n"
       (op-gate-section) "\n"
       (phase-section) "\n"
       (jurisdiction-section) "\n"
       (forced-labor-section) "\n"
       "</main>\n"
       "<footer><p>cloud-itonami-isic-4641 · AGPL-3.0-or-later · read-only sample. "
       "Registry records shown are UNSIGNED drafts — signature is the operator's act, "
       "not this actor's.</p></footer>\n"
       "</body></html>\n"))

(defn -main [& args]
  (let [out (or (first args) "docs/samples/operator-console.html")
        {:keys [db runs] :as result} (run-demo!)
        hs (holds db)]
    ;; Build-time invariant: a console that shows no real HARD hold is
    ;; not evidence of a governor. Do not weaken this.
    (when (empty? hs)
      (throw (ex-info "no :governor-hold fact on the ledger — refusing to write a console that shows no real hold"
                      {:ledger-facts (count (store/ledger db))
                       :operations (count runs)})))
    (let [f (java.io.File. ^String out)]
      (when-let [p (.getParentFile f)] (.mkdirs p))
      (spit f (render result)))
    (println "wrote" out
             (str "(" (count (store/ledger db)) " ledger facts, "
                  (count hs) " HARD holds, "
                  (count runs) " operations, "
                  (count (store/dispatch-history db)) " dispatch drafts, "
                  (count (store/invoice-history db)) " invoice drafts)"))))
