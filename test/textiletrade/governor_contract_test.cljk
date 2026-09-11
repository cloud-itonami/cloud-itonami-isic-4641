(ns textiletrade.governor-contract-test
  "The governor contract as executable tests. The single invariant
  under test:

    TextileTradeAdvisor never dispatches real textile/apparel/footwear
    goods to a counterparty or settles an invoice the Textile Trading
    Governor would reject, `:delivery/dispatch`/`:invoice/settle` NEVER
    auto-commit at any phase, `:order/intake` (no direct capital risk)
    MAY auto-commit when clean, and every decision (commit OR hold)
    leaves exactly one ledger fact."
  (:require [clojure.test :refer [deftest is testing]]
            [langgraph.graph :as g]
            [textiletrade.store :as store]
            [textiletrade.operation :as op]))

(defn- fresh []
  (let [db (store/seed-db)]
    [db (op/build db)]))

(def operator {:actor-id "op-1" :actor-role :trading-supervisor :phase 3})

(defn- exec-op [actor tid request context]
  (g/run* actor {:request request :context context} {:thread-id tid}))

(defn- approve! [actor tid]
  (g/run* actor {:approval {:status :approved :by "op-1"}} {:thread-id tid :resume? true}))

(defn- verify!
  "Walks `subject` through supply-chain verify -> approve, leaving a
  supply-chain assessment on file. Uses distinct thread-ids per call
  site by suffixing `tid-prefix`."
  [actor tid-prefix subject]
  (exec-op actor (str tid-prefix "-verify") {:op :supply-chain/verify :subject subject} operator)
  (approve! actor (str tid-prefix "-verify")))

(deftest clean-intake-auto-commits
  (let [[db actor] (fresh)
        res (exec-op actor "t1"
                  {:op :order/intake :subject "to-1"
                   :patch {:id "to-1" :counterparty "Meridian Apparel Wholesale LLC"}} operator)]
    (is (= :commit (get-in res [:state :disposition])))
    (is (= "Meridian Apparel Wholesale LLC" (:counterparty (store/textile-order db "to-1"))) "SSoT actually updated")
    (is (= 1 (count (store/ledger db))))))

(deftest supply-chain-verify-always-needs-approval
  (testing "supply-chain verify is never in any phase's :auto set -- always human approval, even when clean"
    (let [[db actor] (fresh)
          res (exec-op actor "t2" {:op :supply-chain/verify :subject "to-1"} operator)]
      (is (= :interrupted (:status res)))
      (let [r2 (approve! actor "t2")]
        (is (= :commit (get-in r2 [:state :disposition])))
        (is (some? (store/assessment-of db "to-1")))))))

(deftest fabricated-jurisdiction-is-held
  (testing "a supply-chain/verify proposal with no official spec-basis -> HOLD, never reaches a human"
    (let [[db actor] (fresh)
          res (exec-op actor "t3"
                    {:op :supply-chain/verify :subject "to-2"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:no-spec-basis} (-> (store/ledger db) first :basis)))
      (is (nil? (store/assessment-of db "to-2")) "no assessment written"))))

(deftest dispatch-without-assessment-is-held
  (testing "delivery/dispatch before any supply-chain verification -> HOLD (evidence incomplete)"
    (let [[db actor] (fresh)
          res (exec-op actor "t4" {:op :delivery/dispatch :subject "to-1"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:evidence-incomplete} (-> (store/ledger db) first :basis))))))

(deftest credit-uncleared-is-held-and-unoverridable
  (testing "a counterparty whose credit has not been cleared -> HOLD, and never reaches request-approval -- the leasing collateral-coverage discipline applied to counterparty credit"
    (let [[db actor] (fresh)
          _ (verify! actor "t5pre" "to-3")
          res (exec-op actor "t5" {:op :delivery/dispatch :subject "to-3"} operator)]
      (is (= :hold (get-in res [:state :disposition])) "settles immediately, no interrupt")
      (is (not= :interrupted (:status res)))
      (is (some #{:credit-uncleared} (-> (store/ledger db) last :basis)))
      (is (empty? (store/dispatch-history db))))))

(deftest contract-missing-is-held-and-unoverridable
  (testing "an order with no contract-terms on file -> HOLD, and never reaches request-approval"
    (let [[db actor] (fresh)
          _ (verify! actor "t6pre" "to-4")
          res (exec-op actor "t6" {:op :delivery/dispatch :subject "to-4"} operator)]
      (is (= :hold (get-in res [:state :disposition])) "settles immediately, no interrupt")
      (is (not= :interrupted (:status res)))
      (is (some #{:contract-missing} (-> (store/ledger db) last :basis)))
      (is (empty? (store/dispatch-history db))))))

(deftest counterparty-sanctions-flag-unresolved-is-held-and-unoverridable
  (testing "a counterparty that has not passed OFAC / equivalent sanctions screening -> HOLD, and never reaches request-approval (evaluated at both dispatch and invoice)"
    (let [[db actor] (fresh)
          _ (verify! actor "t7pre" "to-5")
          res (exec-op actor "t7" {:op :delivery/dispatch :subject "to-5"} operator)]
      (is (= :hold (get-in res [:state :disposition])) "settles immediately, no interrupt")
      (is (not= :interrupted (:status res)))
      (is (some #{:counterparty-sanctions-flag-unresolved} (-> (store/ledger db) last :basis)))
      (is (empty? (store/dispatch-history db))))))

(deftest forced-labor-presumption-unrebutted-is-held-and-unoverridable
  (testing "a Xinjiang-origin, USA-jurisdiction textile-order (to-6) with NEITHER a documented supply-chain trace NOR a clear-and-convincing rebuttal dossier -> HOLD, and never reaches request-approval -- the domain-defining check"
    (let [[db actor] (fresh)
          _ (verify! actor "t8pre" "to-6")
          res (exec-op actor "t8" {:op :delivery/dispatch :subject "to-6"} operator)]
      (is (= :hold (get-in res [:state :disposition])) "settles immediately, no interrupt")
      (is (not= :interrupted (:status res)))
      (is (some #{:forced-labor-presumption-unrebutted} (-> (store/ledger db) last :basis)))
      (is (empty? (store/dispatch-history db))))))

(deftest forced-labor-presumption-is-genuinely-rebuttable-not-a-blanket-ban
  (testing "to-7 carries the SAME Xinjiang origin as to-6, but BOTH rebuttal facts (supply-chain-traceability-documented?, forced-labor-rebuttal-evidence-on-file?) are true -> the presumption is REBUTTED, dispatch does NOT hold on this check, still always escalates for the ordinary human sign-off"
    (let [[db actor] (fresh)
          _ (verify! actor "t9pre" "to-7")
          r1 (exec-op actor "t9" {:op :delivery/dispatch :subject "to-7"} operator)]
      (is (= :interrupted (:status r1)) "pauses for the ordinary human dispatch sign-off, NOT a forced-labor hold")
      (let [r2 (approve! actor "t9")]
        (is (= :commit (get-in r2 [:state :disposition])))
        (is (true? (:dispatched? (store/textile-order db "to-7"))))
        (is (= 1 (count (store/dispatch-history db))))))))

(deftest forced-labor-check-is-jurisdiction-gated-not-a-blanket-regional-ban
  (testing "to-8 carries the SAME Xinjiang origin and the SAME unrebutted (false/false) evidence facts as to-6, but jurisdiction JPN has NO currently-binding forced-labor import-ban statute seeded -> the check does NOT fire, dispatch still always escalates only for the usual human sign-off, a considered departure from the metal-wholesale sibling's jurisdiction-unconditional conflict-minerals check"
    (let [[db actor] (fresh)
          _ (verify! actor "t10pre" "to-8")
          r1 (exec-op actor "t10" {:op :delivery/dispatch :subject "to-8"} operator)]
      (is (= :interrupted (:status r1)) "pauses for the ordinary human dispatch sign-off, NOT a forced-labor hold")
      (let [r2 (approve! actor "t10")]
        (is (= :commit (get-in r2 [:state :disposition])))
        (is (true? (:dispatched? (store/textile-order db "to-8"))))
        (is (= 1 (count (store/dispatch-history db))))))))

(deftest dispatch-always-escalates-then-human-decides
  (testing "a clean, fully-verified, credit-cleared, contract-on-file, non-flagged-origin, sanctions-screened order still ALWAYS interrupts for human approval -- :delivery/dispatch is never auto"
    (let [[db actor] (fresh)
          _ (verify! actor "t11pre" "to-1")
          r1 (exec-op actor "t11" {:op :delivery/dispatch :subject "to-1"} operator)]
      (is (= :interrupted (:status r1)) "pauses for human approval even when governor-clean")
      (testing "approve -> commit, dispatch record drafted"
        (let [r2 (approve! actor "t11")]
          (is (= :commit (get-in r2 [:state :disposition])))
          (is (true? (:dispatched? (store/textile-order db "to-1"))))
          (is (= 1 (count (store/dispatch-history db))) "one draft dispatch record"))))))

(deftest invoice-settle-always-escalates-then-human-decides
  (testing "a clean, fully-verified, already-dispatched order still ALWAYS interrupts for human approval -- :invoice/settle is never auto"
    (let [[db actor] (fresh)
          _ (verify! actor "t12pre" "to-1")
          _ (exec-op actor "t12dispatch" {:op :delivery/dispatch :subject "to-1"} operator)
          _ (approve! actor "t12dispatch")
          r1 (exec-op actor "t12" {:op :invoice/settle :subject "to-1"} operator)]
      (is (= :interrupted (:status r1)) "pauses for human approval even when governor-clean")
      (testing "approve -> commit, invoice record drafted"
        (let [r2 (approve! actor "t12")]
          (is (= :commit (get-in r2 [:state :disposition])))
          (is (true? (:invoiced? (store/textile-order db "to-1"))))
          (is (= 1 (count (store/invoice-history db))) "one draft invoice record"))))))

(deftest delivery-dispatch-double-dispatch-is-held
  (testing "dispatching the same textile-order twice -> HOLD on the second attempt"
    (let [[db actor] (fresh)
          _ (verify! actor "t13pre" "to-1")
          _ (exec-op actor "t13a" {:op :delivery/dispatch :subject "to-1"} operator)
          _ (approve! actor "t13a")
          res (exec-op actor "t13" {:op :delivery/dispatch :subject "to-1"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:already-dispatched} (-> (store/ledger db) last :basis)))
      (is (= 1 (count (store/dispatch-history db))) "still only the one earlier dispatch"))))

(deftest invoice-settle-double-invoice-is-held
  (testing "settling the same textile-order's invoice twice -> HOLD on the second attempt"
    (let [[db actor] (fresh)
          _ (verify! actor "t14pre" "to-1")
          _ (exec-op actor "t14dispatch" {:op :delivery/dispatch :subject "to-1"} operator)
          _ (approve! actor "t14dispatch")
          _ (exec-op actor "t14a" {:op :invoice/settle :subject "to-1"} operator)
          _ (approve! actor "t14a")
          res (exec-op actor "t14" {:op :invoice/settle :subject "to-1"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:already-invoiced} (-> (store/ledger db) last :basis)))
      (is (= 1 (count (store/invoice-history db))) "still only the one earlier invoice"))))

(deftest every-decision-leaves-one-ledger-fact
  (testing "write-only-through-ledger: N operations -> N ledger facts"
    (let [[db actor] (fresh)]
      (exec-op actor "a" {:op :order/intake :subject "to-1"
                          :patch {:id "to-1" :counterparty "Meridian Apparel Wholesale LLC"}} operator)
      (exec-op actor "b" {:op :supply-chain/verify :subject "to-2"} operator)
      (is (= 2 (count (store/ledger db)))
          "one commit + one hold, both recorded"))))
