(ns textiletrade.sim
  "Demo driver -- `clojure -M:dev:run`. Walks a clean textile-order
  through intake -> supply-chain verification -> textile/apparel/
  footwear goods dispatch (escalate/approve/commit) -> invoice
  settlement (escalate/approve/commit), then shows HARD-hold scenarios:
  a jurisdiction with no spec-basis, a counterparty whose credit has not
  been cleared, an order with no contract-terms on file, a Xinjiang-
  origin shipment with NO forced-labor rebuttal evidence on file, a
  counterparty that has not passed sanctions screening, a double
  dispatch, and a double invoice -- PLUS two control scenarios:

    1. The SAME Xinjiang-origin shipment, but WITH a documented supply-
       chain trace AND a clear-and-convincing rebuttal evidence dossier
       on file, dispatches CLEANLY -- proving the forced-labor
       REBUTTABLE PRESUMPTION is genuinely rebuttable, not a blanket
       regional ban.
    2. The SAME Xinjiang-origin shipment with NEITHER rebuttal fact on
       file, but jurisdiction JPN (no currently-binding forced-labor
       import-ban statute seeded for JPN) instead of USA, ALSO
       dispatches cleanly -- proving the check is deliberately
       JURISDICTION-gated (this actor's own dispatch act is not itself
       a U.S. customs entry), a considered departure from the metal-
       wholesale sibling's jurisdiction-unconditional conflict-minerals
       check.

  Like every sibling actor's domain checks, this actor's checks
  (`credit-uncleared`, `contract-missing`,
  `forced-labor-presumption-unrebutted`,
  `counterparty-sanctions-flag-unresolved`) are evaluated directly at
  `:delivery/dispatch` (and sanctions at `:invoice/settle` too) rather
  than via a separate screening op -- a real dispatch decision validates
  counterparty credit, contract-on-file, the forced-labor presumption
  (where triggered) and sanctions screening at the point of the act
  itself, not as a discrete pre-screening ceremony. Each check is still
  exercised directly and independently below, one order per HARD-hold
  scenario, following the SAME 'exercise the failure mode directly,
  never only via a happy-path actuation' discipline `parksafety`'s
  ADR-2607071922 Decision 5 and every sibling since establish."
  (:require [langgraph.graph :as g]
            [textiletrade.store :as store]
            [textiletrade.operation :as op]))

(def operator {:actor-id "op-1" :actor-role :trading-supervisor :phase 3})

(defn- exec-op [actor tid request context]
  (g/run* actor {:request request :context context} {:thread-id tid}))

(defn- approve! [actor tid]
  (g/run* actor {:approval {:status :approved :by "op-1"}} {:thread-id tid :resume? true}))

(defn -main [& _]
  (let [db (store/seed-db)
        actor (op/build db)]
    (println "== order/intake to-1 (apparel, Vietnam origin, USA jurisdiction, clean) ==")
    (println (exec-op actor "t1" {:op :order/intake :subject "to-1"
                                  :patch {:id "to-1" :counterparty "Meridian Apparel Wholesale LLC"}} operator))

    (println "== supply-chain/verify to-1 (escalates -- human approves) ==")
    (println (exec-op actor "t2" {:op :supply-chain/verify :subject "to-1"} operator))
    (println (approve! actor "t2"))

    (println "== delivery/dispatch to-1 (always escalates -- :delivery/dispatch) ==")
    (let [r (exec-op actor "t3" {:op :delivery/dispatch :subject "to-1"} operator)]
      (println r)
      (println "-- human trading supervisor approves --")
      (println (approve! actor "t3")))

    (println "== invoice/settle to-1 (always escalates -- :invoice/settle) ==")
    (let [r (exec-op actor "t4" {:op :invoice/settle :subject "to-1"} operator)]
      (println r)
      (println "-- human trading supervisor approves --")
      (println (approve! actor "t4")))

    (println "== supply-chain/verify to-2 (no spec-basis -> HARD hold) ==")
    (println (exec-op actor "t5" {:op :supply-chain/verify :subject "to-2"} operator))

    (println "== supply-chain/verify to-3 (escalates -- human approves; sets up the credit-uncleared test) ==")
    (println (exec-op actor "t6" {:op :supply-chain/verify :subject "to-3"} operator))
    (println (approve! actor "t6"))

    (println "== delivery/dispatch to-3 (credit not cleared -> HARD hold) ==")
    (println (exec-op actor "t7" {:op :delivery/dispatch :subject "to-3"} operator))

    (println "== supply-chain/verify to-4 (escalates -- human approves; sets up the contract-missing test) ==")
    (println (exec-op actor "t8" {:op :supply-chain/verify :subject "to-4"} operator))
    (println (approve! actor "t8"))

    (println "== delivery/dispatch to-4 (no contract-terms on file -> HARD hold) ==")
    (println (exec-op actor "t9" {:op :delivery/dispatch :subject "to-4"} operator))

    (println "== supply-chain/verify to-5 (escalates -- human approves; sets up the sanctions test) ==")
    (println (exec-op actor "t10" {:op :supply-chain/verify :subject "to-5"} operator))
    (println (approve! actor "t10"))

    (println "== delivery/dispatch to-5 (sanctions screening not passed -> HARD hold) ==")
    (println (exec-op actor "t11" {:op :delivery/dispatch :subject "to-5"} operator))

    (println "== supply-chain/verify to-6 (Xinjiang origin, USA; escalates -- human approves; sets up the forced-labor test) ==")
    (println (exec-op actor "t12" {:op :supply-chain/verify :subject "to-6"} operator))
    (println (approve! actor "t12"))

    (println "== delivery/dispatch to-6 (Xinjiang origin, USA, NO rebuttal evidence -> HARD hold, the domain-defining check) ==")
    (println (exec-op actor "t13" {:op :delivery/dispatch :subject "to-6"} operator))

    (println "== supply-chain/verify to-7 (Xinjiang origin, USA, WITH rebuttal evidence; escalates -- human approves) ==")
    (println (exec-op actor "t14" {:op :supply-chain/verify :subject "to-7"} operator))
    (println (approve! actor "t14"))

    (println "== delivery/dispatch to-7 (Xinjiang origin, USA, BOTH rebuttal facts documented -> dispatches cleanly -- PROVES THE PRESUMPTION IS GENUINELY REBUTTABLE, not a blanket regional ban) ==")
    (let [r (exec-op actor "t15" {:op :delivery/dispatch :subject "to-7"} operator)]
      (println r)
      (println "-- human trading supervisor approves --")
      (println (approve! actor "t15")))

    (println "== supply-chain/verify to-8 (Xinjiang origin, JPN jurisdiction; escalates -- human approves; sets up the jurisdiction-gating control) ==")
    (println (exec-op actor "t16" {:op :supply-chain/verify :subject "to-8"} operator))
    (println (approve! actor "t16"))

    (println "== delivery/dispatch to-8 (Xinjiang origin, NO rebuttal evidence, but JPN jurisdiction has NO currently-binding forced-labor import-ban statute -> dispatches cleanly -- PROVES THE CHECK IS JURISDICTION-GATED, unlike the metal-wholesale sibling's unconditional conflict-minerals check) ==")
    (let [r (exec-op actor "t17" {:op :delivery/dispatch :subject "to-8"} operator)]
      (println r)
      (println "-- human trading supervisor approves --")
      (println (approve! actor "t17")))

    (println "== delivery/dispatch to-1 AGAIN (double-dispatch -> HARD hold) ==")
    (println (exec-op actor "t18" {:op :delivery/dispatch :subject "to-1"} operator))

    (println "== invoice/settle to-1 AGAIN (double-invoice -> HARD hold) ==")
    (println (exec-op actor "t19" {:op :invoice/settle :subject "to-1"} operator))

    (println "== audit ledger ==")
    (doseq [f (store/ledger db)] (println f))

    (println "== draft textile-dispatch records ==")
    (doseq [r (store/dispatch-history db)] (println r))

    (println "== draft textile-invoice records ==")
    (doseq [r (store/invoice-history db)] (println r))))
