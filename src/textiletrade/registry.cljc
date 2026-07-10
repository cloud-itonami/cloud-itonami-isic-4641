(ns textiletrade.registry
  "Pure-function textile-dispatch + textile-invoice record construction --
  an append-only textile/apparel/footwear wholesale book-of-record draft.

  Unlike the crude-extraction sibling's own registry (which ALSO hosts
  the pure well-safety range-check functions its governor calls to
  re-verify a well's own physical ground truth before any lift), this
  textile-wholesale vertical's Textile Trading Governor needs NO registry
  range-check functions at all: its domain checks (credit-uncleared,
  contract-missing, forced-labor-presumption-unrebutted, counterparty-
  sanctions-flag-unresolved) are direct entity boolean reads in
  `textiletrade.governor`, off dedicated `:credit-cleared?` /
  `:contract-terms` / `:supply-chain-traceability-documented?` /
  `:forced-labor-rebuttal-evidence-on-file?` / `:sanctions-screened?`
  facts on the `textile-order` record. So this namespace is RECORD
  CONSTRUCTION ONLY -- no pure range checks to host here.

  Like every sibling actor's registry, there is no single international
  reference-number standard for a textile-dispatch or textile-invoice
  record -- every operator/jurisdiction assigns its own reference format.
  This namespace does NOT invent one beyond a jurisdiction-scoped
  sequence number; it validates the record's required fields, the same
  honest, non-fabricating discipline `textiletrade.facts` uses.

  This namespace is pure data + pure functions -- no I/O, no network
  call to any real warehouse-management/ERP/billing system. It builds
  the RECORD an operator would keep, not the act of dispatching real
  textile/apparel/footwear goods at the wholesale warehouse or settling
  a real invoice itself (that is `textiletrade.operation`'s `:delivery/
  dispatch`/`:invoice/settle`, always human-gated -- see README
  `Actuation`)."
  (:require [clojure.string :as str]))

(defn- unsigned-certificate
  "Every certificate this actor produces is UNSIGNED -- signature is
  the operator's act, not this actor's. See README `Actuation`."
  [kind subject record-id]
  {"@context" ["https://www.w3.org/ns/credentials/v2"]
   "type" ["VerifiableCredential" kind]
   "credentialSubject" {"id" subject "record" record-id}
   "proof" nil
   "issued_by_registry" false
   "status" "draft-unsigned"})

(defn- zero-pad [n w]
  (let [s (str n)]
    (str (apply str (repeat (max 0 (- w (count s))) "0")) s)))

;; ----------------------------- record construction -----------------------------

(defn register-dispatch-record
  "Validate + construct the TEXTILE-DISPATCH registration DRAFT -- the
  operator's own legal act of dispatching real textile/apparel/footwear
  goods to a counterparty from the wholesale warehouse. Pure function --
  does not touch any real warehouse or ERP system; it builds the RECORD
  an operator would keep. `textiletrade.governor` independently
  re-verifies the counterparty's credit-clearance, contract-on-file,
  forced-labor-presumption rebuttal, sanctions-screening and evidence-
  completeness ground truth, and blocks a double-dispatch of the same
  textile-order, before this is ever allowed to commit."
  [textile-order-id jurisdiction sequence]
  (when-not (and textile-order-id (not= textile-order-id ""))
    (throw (ex-info "textile-dispatch: textile_order_id required" {})))
  (when-not (and jurisdiction (not= jurisdiction ""))
    (throw (ex-info "textile-dispatch: jurisdiction required" {})))
  (when (< sequence 0)
    (throw (ex-info "textile-dispatch: sequence must be >= 0" {})))
  (let [dispatch-number (str (str/upper-case jurisdiction) "-DISPATCH-" (zero-pad sequence 6))
        record {"record_id" dispatch-number
                "kind" "textile-dispatch-draft"
                "textile_order_id" textile-order-id
                "jurisdiction" jurisdiction
                "immutable" true}]
    {"record" record "dispatch_number" dispatch-number
     "certificate" (unsigned-certificate "TextileDispatch" dispatch-number dispatch-number)}))

(defn register-invoice-record
  "Validate + construct the TEXTILE-INVOICE registration DRAFT -- the
  operator's own legal act of settling a real textile/apparel/footwear
  invoice (the money side of a wholesale trade, custody/financial
  transfer). Pure function -- does not touch any real billing or
  accounts-receivable system; it builds the RECORD an operator would
  keep. `textiletrade.governor` independently re-verifies the sanctions-
  screening and evidence-completeness ground truth, and blocks a
  double-invoice of the same textile-order, before this is ever allowed
  to commit."
  [textile-order-id jurisdiction sequence]
  (when-not (and textile-order-id (not= textile-order-id ""))
    (throw (ex-info "textile-invoice: textile_order_id required" {})))
  (when-not (and jurisdiction (not= jurisdiction ""))
    (throw (ex-info "textile-invoice: jurisdiction required" {})))
  (when (< sequence 0)
    (throw (ex-info "textile-invoice: sequence must be >= 0" {})))
  (let [invoice-number (str (str/upper-case jurisdiction) "-INVOICE-" (zero-pad sequence 6))
        record {"record_id" invoice-number
                "kind" "textile-invoice-draft"
                "textile_order_id" textile-order-id
                "jurisdiction" jurisdiction
                "immutable" true}]
    {"record" record "invoice_number" invoice-number
     "certificate" (unsigned-certificate "TextileInvoice" invoice-number invoice-number)}))

(defn append [history result]
  (conj (vec history) (get result "record")))
