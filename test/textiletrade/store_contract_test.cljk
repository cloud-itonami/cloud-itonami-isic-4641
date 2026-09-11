(ns textiletrade.store-contract-test
  "The Store contract, run against BOTH backends. Proving MemStore and
  the Datomic-backed (langchain.db) store satisfy the same contract is
  what makes 'swap the SSoT for Datomic / kotoba-server' a
  configuration change, not a rewrite -- see `cloud-itonami-isic-6511`'s
  `underwriting.store-contract-test` for the same pattern on the
  sibling actor."
  (:require [clojure.test :refer [deftest is testing]]
            [textiletrade.store :as store]))

(defn- backends []
  [["MemStore" (store/seed-db)] ["DatomicStore" (store/datomic-seed-db)]])

(deftest read-parity
  (doseq [[label s] (backends)]
    (testing label
      (is (= "USA" (:jurisdiction (store/textile-order s "to-1"))))
      (is (= "Meridian Apparel Wholesale LLC" (:counterparty (store/textile-order s "to-1"))))
      (is (= "apparel" (:goods-kind (store/textile-order s "to-1"))))
      (is (= "ATL" (:jurisdiction (store/textile-order s "to-2"))))
      (is (false? (:credit-cleared? (store/textile-order s "to-3"))) "to-3 credit not cleared")
      (is (nil? (:contract-terms (store/textile-order s "to-4"))) "to-4 no contract-terms")
      (is (false? (:sanctions-screened? (store/textile-order s "to-5"))) "to-5 sanctions not screened")
      (is (= "Xinjiang Uyghur Autonomous Region, China" (:origin-region (store/textile-order s "to-6"))))
      (is (false? (:supply-chain-traceability-documented? (store/textile-order s "to-6"))) "to-6 no supply-chain trace")
      (is (false? (:forced-labor-rebuttal-evidence-on-file? (store/textile-order s "to-6"))) "to-6 no rebuttal evidence")
      (is (true? (:supply-chain-traceability-documented? (store/textile-order s "to-7"))) "to-7 rebuttal documented")
      (is (true? (:forced-labor-rebuttal-evidence-on-file? (store/textile-order s "to-7"))) "to-7 rebuttal documented")
      (is (= "JPN" (:jurisdiction (store/textile-order s "to-8"))))
      (is (false? (:dispatched? (store/textile-order s "to-1"))))
      (is (false? (:invoiced? (store/textile-order s "to-1"))))
      (is (= ["to-1" "to-2" "to-3" "to-4" "to-5" "to-6" "to-7" "to-8"]
             (mapv :id (store/all-textile-orders s))))
      (is (nil? (store/assessment-of s "to-1")))
      (is (= [] (store/ledger s)))
      (is (= [] (store/dispatch-history s)))
      (is (= [] (store/invoice-history s)))
      (is (zero? (store/next-dispatch-sequence s "USA")))
      (is (zero? (store/next-invoice-sequence s "USA")))
      (is (false? (store/textile-order-already-dispatched? s "to-1")))
      (is (false? (store/textile-order-already-invoiced? s "to-1"))))))

(deftest write-and-ledger-parity
  (doseq [[label s] (backends)]
    (testing label
      (testing "partial upsert merges, preserving untouched fields"
        (store/commit-record! s {:effect :order/upsert
                                 :value {:id "to-1" :counterparty "Meridian Apparel Wholesale LLC"}})
        (is (= "Meridian Apparel Wholesale LLC" (:counterparty (store/textile-order s "to-1"))))
        (is (= "USA" (:jurisdiction (store/textile-order s "to-1"))) "unrelated field preserved"))
      (testing "supply-chain-assessment payloads commit and read back"
        (store/commit-record! s {:effect :supply-chain-assessment/set :path ["to-1"]
                                 :payload {:jurisdiction "USA" :checklist ["a" "b"]}})
        (is (= {:jurisdiction "USA" :checklist ["a" "b"]} (store/assessment-of s "to-1"))))
      (testing "textile dispatch drafts a record and advances the dispatch sequence"
        (store/commit-record! s {:effect :order/mark-dispatched :path ["to-1"]})
        (is (= "USA-DISPATCH-000000" (get (first (store/dispatch-history s)) "record_id")))
        (is (= "textile-dispatch-draft" (get (first (store/dispatch-history s)) "kind")))
        (is (true? (:dispatched? (store/textile-order s "to-1"))))
        (is (= 1 (count (store/dispatch-history s))))
        (is (= 1 (store/next-dispatch-sequence s "USA")))
        (is (true? (store/textile-order-already-dispatched? s "to-1"))))
      (testing "invoice settlement drafts a record and advances the invoice sequence"
        (store/commit-record! s {:effect :order/mark-invoiced :path ["to-1"]})
        (is (= "USA-INVOICE-000000" (get (first (store/invoice-history s)) "record_id")))
        (is (= "textile-invoice-draft" (get (first (store/invoice-history s)) "kind")))
        (is (true? (:invoiced? (store/textile-order s "to-1"))))
        (is (= 1 (count (store/invoice-history s))))
        (is (= 1 (store/next-invoice-sequence s "USA")))
        (is (true? (store/textile-order-already-invoiced? s "to-1"))))
      (testing "ledger is append-only and order-preserving"
        (store/append-ledger! s {:op :a :disposition :commit})
        (store/append-ledger! s {:op :b :disposition :hold})
        (is (= [:commit :hold] (mapv :disposition (store/ledger s))))))))

(deftest datomic-empty-store-is-usable
  (let [s (store/datomic-store)]
    (is (nil? (store/textile-order s "nope")))
    (is (= [] (store/all-textile-orders s)))
    (is (= [] (store/ledger s)))
    (is (= [] (store/dispatch-history s)))
    (is (= [] (store/invoice-history s)))
    (is (zero? (store/next-dispatch-sequence s "USA")))
    (is (zero? (store/next-invoice-sequence s "USA")))
    (store/with-textile-orders s {"x" {:id "x" :order-id "TO-X" :goods-kind "footwear"
                                       :origin-region "Vietnam -- Ho Chi Minh City garment district"
                                       :manufacturing-entity "Song Han Garment Co."
                                       :entity-list-flagged? false
                                       :quantity-units 5000 :counterparty "c" :price 12.50
                                       :contract-terms "FOB, net 45 days"
                                       :credit-cleared? true :sanctions-screened? true
                                       :supply-chain-traceability-documented? true
                                       :forced-labor-rebuttal-evidence-on-file? true
                                       :dispatched? false :invoiced? false
                                       :jurisdiction "USA" :status :intake
                                       :dispatch-number nil :invoice-number nil}})
    (is (= "c" (:counterparty (store/textile-order s "x"))))))
