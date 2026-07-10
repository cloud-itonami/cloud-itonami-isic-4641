(ns textiletrade.store
  "SSoT for the textile/apparel/footwear-wholesale actor, behind a
  `Store` protocol so the backend is a swap, not a rewrite -- the same
  seam every prior `cloud-itonami-isic-*` actor in this fleet uses.

    - `MemStore`     -- atom of EDN. The deterministic default for
                        dev/tests/demo (no deps).
    - `DatomicStore` -- backed by `langchain.db`, a Datomic-API-compatible
                        EAV store (datalog q / pull / upsert). Pure `.cljc`,
                        so it runs offline AND can be pointed at a real
                        Datomic Local or a kotoba-server pod by swapping
                        `langchain.db`'s `:db-api` (see langchain.kotoba-db).

  Both implement the same protocol and pass the same contract
  (test/textiletrade/store_contract_test.clj), which is the whole point:
  the actor, the Textile Trading Governor and the audit ledger never
  know which SSoT they run on.

  Like the fuel-wholesale/metal-wholesale siblings' entities, this
  vertical's `dispatch` and `settle` actuation events apply
  SEQUENTIALLY to the SAME `textile-order` -- a textile/apparel/footwear
  goods dispatch happens first (product leaves the wholesale warehouse),
  invoice settlement happens later, on the same order record. This
  matches the sequential dual-actuation shape, with dedicated
  double-actuation-guard booleans (`:dispatched?`/`:invoiced?`, never a
  `:status` value).

  The `textile-order` record carries TWO evidence surfaces the Textile
  Trading Governor reads independently: the generic per-jurisdiction
  counterparty-diligence facts (`:credit-cleared?` / `:contract-terms` /
  `:sanctions-screened?`, same shape as every sibling), AND a pair of
  forced-labor-presumption-specific facts
  (`:supply-chain-traceability-documented?` /
  `:forced-labor-rebuttal-evidence-on-file?`) that exist ONLY on this
  vertical -- see `textiletrade.governor`'s
  `forced-labor-presumption-unrebutted-violations` for why these are a
  SEPARATE, jurisdiction-gated check rather than folded into the
  generic evidence checklist.

  The ledger stays append-only on every backend: 'which textile-order
  was verified for a jurisdiction with no official spec-basis, which
  counterparty had credit-uncleared / no contract / an unrebutted
  forced-labor presumption / an unresolved sanctions-screening flag,
  which order had goods dispatched, which invoice was settled, on what
  jurisdictional and supply-chain basis, approved by whom' is always a
  query over an immutable log -- the audit trail a regulator, a
  downstream buyer running its own UFLPA reasonable-care due diligence,
  or an operator trusting a textile-wholesale actor needs, and the
  evidence an operator needs if a dispatch or an invoice is later
  disputed."
  (:require #?(:clj  [clojure.edn :as edn]
               :cljs [cljs.reader :as edn])
            [textiletrade.registry :as registry]
            [langchain.db :as d]))

(defprotocol Store
  (textile-order [s id])
  (all-textile-orders [s])
  (assessment-of [s textile-order-id] "committed supply-chain assessment, or nil")
  (ledger [s])
  (dispatch-history [s] "the append-only textile-dispatch history (textiletrade.registry drafts)")
  (invoice-history [s] "the append-only textile-invoice history (textiletrade.registry drafts)")
  (next-dispatch-sequence [s jurisdiction] "next dispatch-number sequence for a jurisdiction")
  (next-invoice-sequence [s jurisdiction] "next invoice-number sequence for a jurisdiction")
  (textile-order-already-dispatched? [s textile-order-id] "have goods already been dispatched for this order?")
  (textile-order-already-invoiced? [s textile-order-id] "has this order's invoice already been settled?")
  (commit-record! [s record] "apply a committed op's record to the SSoT")
  (append-ledger! [s fact]   "append one immutable decision fact")
  (with-textile-orders [s textile-orders] "replace/seed the textile-order directory (map id->textile-order)"))

;; ----------------------------- demo data -----------------------------

(defn- base-order
  "The neutral, clean textile-order shape (every field in its safe
  state), so each demo order below isolates exactly ONE failure mode by
  overriding a single field. `:jurisdiction` defaults to \"USA\" (a
  jurisdiction WITH a currently-binding forced-labor import-ban statute)
  and `:origin-region` defaults to a real, common, NON-flagged apparel-
  manufacturing origin, so the base order also proves the happy path
  THROUGH the forced-labor-presumption check (evaluated, found clean),
  not merely around it."
  [overrides]
  (merge {:id "to-1" :order-id "TO-2026-0001" :goods-kind "apparel"
          :origin-region "Vietnam -- Ho Chi Minh City garment district"
          :manufacturing-entity "Song Han Garment Co."
          :entity-list-flagged? false
          :quantity-units 20000 :counterparty "Meridian Apparel Wholesale LLC"
          :price 4.85 :contract-terms "FOB, net 45 days"
          :credit-cleared? true :sanctions-screened? true
          :supply-chain-traceability-documented? true
          :forced-labor-rebuttal-evidence-on-file? true
          :dispatched? false :invoiced? false
          :jurisdiction "USA" :status :intake
          :dispatch-number nil :invoice-number nil}
         overrides))

(defn demo-data
  "A small, self-contained textile-order set covering both actuation
  lifecycles (dispatch, invoice settlement) plus the Textile Trading
  Governor's own checks, so the actor + tests run offline. Each
  violation order isolates exactly ONE failure mode (the rest stay
  clean) following the 'exercise the failure mode directly, never only
  via a happy-path actuation' discipline every sibling governor's demo
  data establishes.

  `to-6` and `to-7` together prove the forced-labor presumption is
  GENUINELY REBUTTABLE, not a blanket regional ban: `to-6` (Xinjiang
  origin, NEITHER rebuttal fact on file) HOLDS; `to-7` (the SAME
  Xinjiang origin, BOTH rebuttal facts documented) dispatches cleanly.
  `to-8` proves the check is JURISDICTION-gated (a deliberate departure
  from the metal-wholesale sibling's unconditional-across-jurisdiction
  design): the SAME Xinjiang origin with NEITHER rebuttal fact on file,
  but jurisdiction JPN (no currently-binding forced-labor import-ban
  statute seeded for JPN) -- the check does NOT fire, and the order
  dispatches cleanly (still always escalating for the ordinary human
  sign-off)."
  []
  {:textile-orders
   (into {}
         (for [o [(base-order {:id "to-1" :order-id "TO-2026-0001"})
                  (base-order {:id "to-2" :order-id "TO-2026-0002"
                               :counterparty "Atlantis Textiles Ltd"
                               :jurisdiction "ATL"})
                  (base-order {:id "to-3" :order-id "TO-2026-0003"
                               :counterparty "Cedar Footwear Distribution Corp"
                               :credit-cleared? false})
                  (base-order {:id "to-4" :order-id "TO-2026-0004"
                               :counterparty "Delta Clothing BV"
                               :contract-terms nil})
                  (base-order {:id "to-5" :order-id "TO-2026-0005"
                               :counterparty "Eagle Apparel SA"
                               :sanctions-screened? false})
                  (base-order {:id "to-6" :order-id "TO-2026-0006"
                               :goods-kind "textile-fabric"
                               :origin-region "Xinjiang Uyghur Autonomous Region, China"
                               :manufacturing-entity "Tarim Basin Textile Mills"
                               :counterparty "Fenwick Global Sourcing Inc"
                               :supply-chain-traceability-documented? false
                               :forced-labor-rebuttal-evidence-on-file? false})
                  (base-order {:id "to-7" :order-id "TO-2026-0007"
                               :goods-kind "textile-fabric"
                               :origin-region "Xinjiang Uyghur Autonomous Region, China"
                               :manufacturing-entity "Tarim Basin Textile Mills"
                               :counterparty "Granite Rebuttal-Documented Sourcing Inc"
                               :supply-chain-traceability-documented? true
                               :forced-labor-rebuttal-evidence-on-file? true})
                  (base-order {:id "to-8" :order-id "TO-2026-0008"
                               :goods-kind "textile-fabric"
                               :origin-region "Xinjiang Uyghur Autonomous Region, China"
                               :manufacturing-entity "Tarim Basin Textile Mills"
                               :counterparty "Hanami Trading KK"
                               :jurisdiction "JPN"
                               :supply-chain-traceability-documented? false
                               :forced-labor-rebuttal-evidence-on-file? false})]]
           [(:id o) o]))})

;; ----------------------------- shared commit logic -----------------------------

(defn- dispatch-order!
  "Backend-agnostic `:order/mark-dispatched` -- looks up the textile-order
  via the protocol and drafts the textile-dispatch record, and returns
  {:result .. :textile-order-patch ..} for the caller to persist."
  [s textile-order-id]
  (let [to (textile-order s textile-order-id)
        seq-n (next-dispatch-sequence s (:jurisdiction to))
        result (registry/register-dispatch-record textile-order-id (:jurisdiction to) seq-n)]
    {:result result
     :textile-order-patch {:dispatched? true
                           :dispatch-number (get result "dispatch_number")}}))

(defn- invoice-order!
  "Backend-agnostic `:order/mark-invoiced` -- looks up the textile-order
  via the protocol and drafts the textile-invoice record, and returns
  {:result .. :textile-order-patch ..} for the caller to persist."
  [s textile-order-id]
  (let [to (textile-order s textile-order-id)
        seq-n (next-invoice-sequence s (:jurisdiction to))
        result (registry/register-invoice-record textile-order-id (:jurisdiction to) seq-n)]
    {:result result
     :textile-order-patch {:invoiced? true
                           :invoice-number (get result "invoice_number")}}))

;; ----------------------------- MemStore (default) -----------------------------

(defrecord MemStore [a]
  Store
  (textile-order [_ id] (get-in @a [:textile-orders id]))
  (all-textile-orders [_] (sort-by :id (vals (:textile-orders @a))))
  (assessment-of [_ textile-order-id] (get-in @a [:assessments textile-order-id]))
  (ledger [_] (:ledger @a))
  (dispatch-history [_] (:dispatches @a))
  (invoice-history [_] (:invoices @a))
  (next-dispatch-sequence [_ jurisdiction] (get-in @a [:dispatch-sequences jurisdiction] 0))
  (next-invoice-sequence [_ jurisdiction] (get-in @a [:invoice-sequences jurisdiction] 0))
  (textile-order-already-dispatched? [_ textile-order-id] (boolean (get-in @a [:textile-orders textile-order-id :dispatched?])))
  (textile-order-already-invoiced? [_ textile-order-id] (boolean (get-in @a [:textile-orders textile-order-id :invoiced?])))
  (commit-record! [s {:keys [effect path value payload]}]
    (case effect
      :order/upsert
      (swap! a update-in [:textile-orders (:id value)] merge value)

      :supply-chain-assessment/set
      (swap! a assoc-in [:assessments (first path)] payload)

      :order/mark-dispatched
      (let [textile-order-id (first path)
            {:keys [result textile-order-patch]} (dispatch-order! s textile-order-id)
            jurisdiction (:jurisdiction (textile-order s textile-order-id))]
        (swap! a (fn [state]
                   (-> state
                       (update-in [:dispatch-sequences jurisdiction] (fnil inc 0))
                       (update-in [:textile-orders textile-order-id] merge textile-order-patch)
                       (update :dispatches registry/append result))))
        result)

      :order/mark-invoiced
      (let [textile-order-id (first path)
            {:keys [result textile-order-patch]} (invoice-order! s textile-order-id)
            jurisdiction (:jurisdiction (textile-order s textile-order-id))]
        (swap! a (fn [state]
                   (-> state
                       (update-in [:invoice-sequences jurisdiction] (fnil inc 0))
                       (update-in [:textile-orders textile-order-id] merge textile-order-patch)
                       (update :invoices registry/append result))))
        result)
      nil)
    s)
  (append-ledger! [_ fact] (swap! a update :ledger conj fact) fact)
  (with-textile-orders [s textile-orders] (when (seq textile-orders) (swap! a assoc :textile-orders textile-orders)) s))

(defn seed-db
  "A MemStore seeded with the demo textile-order set. The deterministic
  default."
  []
  (->MemStore (atom (assoc (demo-data)
                           :assessments {}
                           :ledger [] :dispatch-sequences {} :dispatches []
                           :invoice-sequences {} :invoices []))))

;; ----------------------------- DatomicStore (langchain.db) -----------------------------

(def ^:private schema
  "DataScript/Datomic-style schema: only constraint attrs are declared.
  Map/compound values (assessment payloads, ledger facts, dispatch/
  invoice records) are stored as EDN strings so `langchain.db`
  doesn't expand them into sub-entities -- the same convention every
  sibling actor's store uses."
  {:textile-order/id                     {:db/unique :db.unique/identity}
   :assessment/textile-order-id          {:db/unique :db.unique/identity}
   :ledger/seq                           {:db/unique :db.unique/identity}
   :dispatch/seq                         {:db/unique :db.unique/identity}
   :invoice/seq                          {:db/unique :db.unique/identity}
   :dispatch-sequence/jurisdiction       {:db/unique :db.unique/identity}
   :invoice-sequence/jurisdiction        {:db/unique :db.unique/identity}})

(defn- enc [v] (pr-str v))
(defn- dec* [s] (when s (edn/read-string s)))

;; Every textile-order field is stored as its own Datomic attr so a
;; governor pull reads the exact ground truth (no blob decode). Boolean
;; fields are coerced on read so a missing attr reads back as false
;; (parity with MemStore). [field-key tx-attr boolean?]
(def ^:private textile-order-fields
  [[:id :textile-order/id false]
   [:order-id :textile-order/order-id false]
   [:goods-kind :textile-order/goods-kind false]
   [:origin-region :textile-order/origin-region false]
   [:manufacturing-entity :textile-order/manufacturing-entity false]
   [:entity-list-flagged? :textile-order/entity-list-flagged? true]
   [:quantity-units :textile-order/quantity-units false]
   [:counterparty :textile-order/counterparty false]
   [:price :textile-order/price false]
   [:contract-terms :textile-order/contract-terms false]
   [:credit-cleared? :textile-order/credit-cleared? true]
   [:sanctions-screened? :textile-order/sanctions-screened? true]
   [:supply-chain-traceability-documented? :textile-order/supply-chain-traceability-documented? true]
   [:forced-labor-rebuttal-evidence-on-file? :textile-order/forced-labor-rebuttal-evidence-on-file? true]
   [:dispatched? :textile-order/dispatched? true]
   [:invoiced? :textile-order/invoiced? true]
   [:jurisdiction :textile-order/jurisdiction false]
   [:status :textile-order/status false]
   [:dispatch-number :textile-order/dispatch-number false]
   [:invoice-number :textile-order/invoice-number false]])

(defn- textile-order->tx [to]
  (reduce (fn [tx [k attr _bool?]]
            (let [v (get to k)]
              (cond-> tx (some? v) (assoc attr v))))
          {:textile-order/id (:id to)}
          textile-order-fields))

(def ^:private textile-order-pull (mapv second textile-order-fields))

(defn- pull->textile-order [m]
  (when (:textile-order/id m)
    (reduce (fn [to [k attr bool?]]
              (let [v (get m attr)]
                (cond
                  bool?        (assoc to k (boolean v))
                  (some? v)    (assoc to k v)
                  :else        to)))
            {:id (:textile-order/id m)}
            textile-order-fields)))

(defrecord DatomicStore [conn]
  Store
  (textile-order [_ id]
    (pull->textile-order (d/pull (d/db conn) textile-order-pull [:textile-order/id id])))
  (all-textile-orders [_]
    (->> (d/q '[:find [?id ...] :where [?e :textile-order/id ?id]] (d/db conn))
         (map #(pull->textile-order (d/pull (d/db conn) textile-order-pull [:textile-order/id %])))
         (sort-by :id)))
  (assessment-of [_ textile-order-id]
    (dec* (d/q '[:find ?p . :in $ ?toid
                :where [?a :assessment/textile-order-id ?toid] [?a :assessment/payload ?p]]
              (d/db conn) textile-order-id)))
  (ledger [_]
    (->> (d/q '[:find ?s ?f :where [?e :ledger/seq ?s] [?e :ledger/fact ?f]] (d/db conn))
         (sort-by first)
         (mapv (comp dec* second))))
  (dispatch-history [_]
    (->> (d/q '[:find ?s ?r :where [?e :dispatch/seq ?s] [?e :dispatch/record ?r]] (d/db conn))
         (sort-by first)
         (mapv (comp dec* second))))
  (invoice-history [_]
    (->> (d/q '[:find ?s ?r :where [?e :invoice/seq ?s] [?e :invoice/record ?r]] (d/db conn))
         (sort-by first)
         (mapv (comp dec* second))))
  (next-dispatch-sequence [_ jurisdiction]
    (or (d/q '[:find ?n . :in $ ?j
              :where [?e :dispatch-sequence/jurisdiction ?j] [?e :dispatch-sequence/next ?n]]
            (d/db conn) jurisdiction)
        0))
  (next-invoice-sequence [_ jurisdiction]
    (or (d/q '[:find ?n . :in $ ?j
              :where [?e :invoice-sequence/jurisdiction ?j] [?e :invoice-sequence/next ?n]]
            (d/db conn) jurisdiction)
        0))
  (textile-order-already-dispatched? [s textile-order-id]
    (boolean (:dispatched? (textile-order s textile-order-id))))
  (textile-order-already-invoiced? [s textile-order-id]
    (boolean (:invoiced? (textile-order s textile-order-id))))
  (commit-record! [s {:keys [effect path value payload]}]
    (case effect
      :order/upsert
      (d/transact! conn [(textile-order->tx value)])

      :supply-chain-assessment/set
      (d/transact! conn [{:assessment/textile-order-id (first path) :assessment/payload (enc payload)}])

      :order/mark-dispatched
      (let [textile-order-id (first path)
            {:keys [result textile-order-patch]} (dispatch-order! s textile-order-id)
            jurisdiction (:jurisdiction (textile-order s textile-order-id))
            next-n (inc (next-dispatch-sequence s jurisdiction))]
        (d/transact! conn
                     [(textile-order->tx (assoc textile-order-patch :id textile-order-id))
                      {:dispatch-sequence/jurisdiction jurisdiction :dispatch-sequence/next next-n}
                      {:dispatch/seq (count (dispatch-history s)) :dispatch/record (enc (get result "record"))}])
        result)

      :order/mark-invoiced
      (let [textile-order-id (first path)
            {:keys [result textile-order-patch]} (invoice-order! s textile-order-id)
            jurisdiction (:jurisdiction (textile-order s textile-order-id))
            next-n (inc (next-invoice-sequence s jurisdiction))]
        (d/transact! conn
                     [(textile-order->tx (assoc textile-order-patch :id textile-order-id))
                      {:invoice-sequence/jurisdiction jurisdiction :invoice-sequence/next next-n}
                      {:invoice/seq (count (invoice-history s)) :invoice/record (enc (get result "record"))}])
        result)
      nil)
    s)
  (append-ledger! [s fact]
    (d/transact! conn [{:ledger/seq (count (ledger s)) :ledger/fact (enc fact)}])
    fact)
  (with-textile-orders [s textile-orders]
    (when (seq textile-orders) (d/transact! conn (mapv textile-order->tx (vals textile-orders)))) s))

(defn datomic-store
  "A DatomicStore (langchain.db backend) seeded from `data`
  ({:textile-orders ..}); empty when omitted."
  ([] (datomic-store {}))
  ([{:keys [textile-orders]}]
   (let [s (->DatomicStore (d/create-conn schema))]
     (with-textile-orders s textile-orders))))

(defn datomic-seed-db
  "A DatomicStore seeded with the demo textile-order set -- the
  Datomic-backed analog of `seed-db`, used to prove protocol parity."
  []
  (datomic-store (demo-data)))
