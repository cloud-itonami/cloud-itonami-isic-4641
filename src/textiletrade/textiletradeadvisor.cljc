(ns textiletrade.textiletradeadvisor
  "TextileTradeAdvisor client -- the *contained intelligence node* for the
  textile/apparel/footwear-wholesale actor.

  It normalizes textile-order intake, drafts a per-jurisdiction
  counterparty-diligence / sanctions evidence checklist (citing the
  general trade spec-basis) PLUS a forced-labor rebuttable-presumption
  citation when the order's jurisdiction currently binds one, drafts the
  goods-dispatch action, and drafts the invoice-settlement action.
  CRITICAL: it is a smart-but-untrusted advisor. It returns a *proposal*
  (with a rationale + the fields it cited), never a committed record or
  a real dispatch/settlement. Every output is censored downstream by
  `textiletrade.governor` before anything touches the SSoT, and
  `:delivery/dispatch`/`:invoice/settle` proposals NEVER auto-commit at
  any phase -- see README `Actuation`.

  Like every sibling actor's advisor, this is a deterministic mock so
  the actor graph runs offline and the governor contract is exercised
  end-to-end. In production this calls a real LLM (kotoba-llm or
  equivalent) with the same proposal shape.

  Proposal shape (all kinds):
    {:summary    str            ; human-facing draft / finding
     :rationale  str            ; why -- SCANNED by the spec-basis gate
     :cites      [kw|str ..]    ; facts/sources the LLM used -- SCANNED too
     :effect     kw             ; how a commit would mutate the SSoT
     :stake      kw|nil         ; :delivery/dispatch | :invoice/settle | nil
     :confidence 0..1}"
  (:require #?(:clj  [clojure.edn :as edn]
               :cljs [cljs.reader :as edn])
            [clojure.string :as str]
            [textiletrade.facts :as facts]
            [textiletrade.store :as store]
            [langchain.model :as model]))

(defn- normalize-intake
  "Directory upsert -- the LLM only normalizes/validates the patch; it
  does not invent the order-id, counterparty, origin-region,
  manufacturing entity or any physical/commercial value. High
  confidence, low stakes."
  [_db {:keys [patch]}]
  {:summary    (str "繊維卸売オーダー記録更新: " (pr-str (keys patch)))
   :rationale  "入力 patch の正規化のみ。新規事実の生成なし。"
   :cites      (vec (keys patch))
   :effect     :order/upsert
   :value      patch
   :stake      nil
   :confidence 0.97})

(defn- verify-supply-chain
  "Per-jurisdiction counterparty-diligence / sanctions evidence
  checklist draft, PLUS -- when the order's own jurisdiction currently
  binds a forced-labor import-ban statute -- a forced-labor rebuttable-
  presumption citation drawn from
  `textiletrade.facts/forced-labor-presumption-citation`. `:no-spec?`
  injects the failure mode we must defend against: proposing a
  checklist for a jurisdiction with NO official spec-basis in
  `textiletrade.facts` -- the Textile Trading Governor must reject this
  (never invent a jurisdiction's requirements). The forced-labor
  citation is informational only here -- the governor's own
  `forced-labor-presumption-unrebutted-violations` check re-verifies the
  order's OWN `:supply-chain-traceability-documented?`/`:forced-labor-
  rebuttal-evidence-on-file?` facts, gated on its OWN `:jurisdiction` and
  `:origin-region`/`:entity-list-flagged?`, directly at `:delivery/
  dispatch`, independent of what this advisor cites."
  [db {:keys [subject no-spec?]}]
  (let [to (store/textile-order db subject)
        iso3 (if no-spec? "ATL" (:jurisdiction to))
        sb (facts/spec-basis iso3)]
    (if (nil? sb)
      {:summary    (str iso3 " の公式spec-basisが見つかりません")
       :rationale  "textiletrade.facts に未登録の法域。要件を推測で作らない。"
       :cites      []
       :effect     :supply-chain-assessment/set
       :value      {:jurisdiction iso3 :checklist [] :spec-basis nil}
       :stake      nil
       :confidence 0.9}
      (let [binding? (facts/forced-labor-import-ban-binding? iso3)
            fl-basis (when binding? (facts/forced-labor-presumption-citation iso3))]
        {:summary    (str iso3 " (" (:owner-authority sb) ") 向け必要書類 "
                          (count (:required-evidence sb)) " 件を提案"
                          (when binding? "、UFLPA強制労働の反証可能な推定チェックを含む"))
         :rationale  (str "公式ソース: " (:provenance sb) " / 法的根拠: " (:legal-basis sb)
                          (when fl-basis
                            (str " / 強制労働根拠: " (:legal-basis fl-basis)
                                 " (" (:owner-authority fl-basis) ")")))
         :cites      (cond-> [(:legal-basis sb) (:provenance sb)]
                       fl-basis (conj (:legal-basis fl-basis)))
         :effect     :supply-chain-assessment/set
         :value      (cond-> {:jurisdiction iso3
                              :checklist (:required-evidence sb)
                              :spec-basis (:provenance sb)
                              :legal-basis (:legal-basis sb)}
                       fl-basis (assoc :forced-labor-basis (:legal-basis fl-basis)))
         :stake      nil
         :confidence 0.9}))))

(defn- propose-dispatch
  "Draft the actual TEXTILE-DISPATCH action -- dispatching real textile/
  apparel/footwear goods to a counterparty from the wholesale warehouse.
  ALWAYS `:stake :delivery/dispatch` -- this is a REAL-WORLD act (an
  autonomous garment-on-hanger sortation conveyor or footwear pick-to-
  light/AS-RS robot physically performs the goods pick at the warehouse,
  or an operator does), never a draft the actor may auto-run. See
  README `Actuation`: no phase ever adds this op to a phase's `:auto`
  set (`textiletrade.phase`); the governor also always escalates on
  `:delivery/dispatch`. Two independent layers agree, deliberately."
  [db {:keys [subject]}]
  (let [to (store/textile-order db subject)
        credit-ok? (and to (true? (:credit-cleared? to)))
        contract-ok? (and to (some? (:contract-terms to))
                          (not= "" (:contract-terms to)))
        triggered? (and to (facts/forced-labor-presumption-triggered? to))
        rebuttal-ok? (or (not triggered?)
                         (and (true? (:supply-chain-traceability-documented? to))
                              (true? (:forced-labor-rebuttal-evidence-on-file? to))))
        sanctions-ok? (and to (true? (:sanctions-screened? to)))]
    {:summary    (str subject " 向け出荷提案"
                      (when to (str " (counterparty=" (:counterparty to)
                                    ", origin=" (:origin-region to) ")")))
     :rationale  (if to
                   (str "credit-cleared?=" credit-ok?
                        " contract-on-file?=" contract-ok?
                        " forced-labor-presumption-triggered?=" triggered?
                        " rebuttal-verified?=" rebuttal-ok?
                        " sanctions-screened?=" sanctions-ok?)
                   "textile-orderが見つかりません")
     :cites      (if to [subject] [])
     :effect     :order/mark-dispatched
     :value      {:textile-order-id subject}
     :stake      :delivery/dispatch
     :confidence (if (and credit-ok? contract-ok? rebuttal-ok? sanctions-ok?) 0.9 0.3)}))

(defn- propose-invoice
  "Draft the actual INVOICE-SETTLEMENT action -- settling a real
  textile/apparel/footwear invoice (the money side of a wholesale
  trade, custody/financial transfer). ALWAYS `:stake :invoice/settle`
  -- this is a REAL-WORLD act (real money moves between counterparty and
  trader), never a draft the actor may auto-run. See README
  `Actuation`: no phase ever adds this op to a phase's `:auto` set
  (`textiletrade.phase`); the governor also always escalates on
  `:invoice/settle`. Two independent layers agree, deliberately."
  [db {:keys [subject]}]
  (let [to (store/textile-order db subject)
        dispatched? (and to (:dispatched? to))
        sanctions-ok? (and to (true? (:sanctions-screened? to)))]
    {:summary    (str subject " 向け請求提案"
                      (when to (str " (counterparty=" (:counterparty to) ")")))
     :rationale  (if to
                   (str "dispatched?=" dispatched?
                        " sanctions-screened?=" sanctions-ok?)
                   "textile-orderが見つかりません")
     :cites      (if to [subject] [])
     :effect     :order/mark-invoiced
     :value      {:textile-order-id subject}
     :stake      :invoice/settle
     :confidence (if (and dispatched? sanctions-ok?) 0.9 0.3)}))

(defn infer
  "Route a request to the right proposal generator.
  request: {:op kw :subject id ...op-specific...}"
  [db {:keys [op] :as request}]
  (case op
    :order/intake        (normalize-intake db request)
    :supply-chain/verify  (verify-supply-chain db request)
    :delivery/dispatch    (propose-dispatch db request)
    :invoice/settle       (propose-invoice db request)
    {:summary "未対応の操作" :rationale (str op) :cites []
     :effect :noop :stake nil :confidence 0.0}))

;; ----------------------------- Advisor protocol -----------------------------

(defprotocol Advisor
  (-advise [advisor store request] "store + request -> proposal map"))

(defn mock-advisor
  "The deterministic advisor (the `infer` logic above). Default everywhere."
  [] (reify Advisor (-advise [_ st req] (infer st req))))

(def ^:private system-prompt
  (str "あなたは繊維・アパレル・履物卸売事業者の出荷・請求エージェントの助言者です。"
       "与えられた事実のみに基づき、提案を1つだけEDNマップで返します。"
       "説明や前置きは一切書かず、EDNだけを出力します。\n"
       "キー: :summary(人向けドラフト) :rationale(根拠/必ず事実から) "
       ":cites(使った事実キーのベクタ) "
       ":effect(:order/upsert|:supply-chain-assessment/set|:order/mark-dispatched|"
       ":order/mark-invoiced) "
       ":stake(:delivery/dispatch か :invoice/settle か nil) :confidence(0..1)。\n"
       "重要: 登録されていない法域の関税・制裁要件を絶対に創作してはいけません。"
       "新疆ウイグル自治区原産または北朝鮮労働者関連など強制労働の反証可能な推定"
       "(rebuttable presumption)の対象となる荷口について、サプライチェーン追跡記録や"
       "反証エビデンスの状態を偽って報告してはいけません。取引先信用審査・契約有無・"
       "制裁スクリーニングの状態も偽って報告してはいけません。"))

(defn- facts-for [st {:keys [op subject]}]
  (case op
    :supply-chain/verify {:textile-order (store/textile-order st subject)}
    :delivery/dispatch   {:textile-order (store/textile-order st subject)}
    :invoice/settle      {:textile-order (store/textile-order st subject)}
    {:textile-order (store/textile-order st subject)}))

(defn- parse-proposal
  "Parse the model's EDN proposal defensively. Any parse/shape failure
  yields a safe low-confidence noop so the Textile Trading Governor
  escalates/holds -- an LLM hiccup can never auto-dispatch goods or
  auto-settle an invoice."
  [content]
  (let [p (try (edn/read-string (str/trim (str content)))
               (catch #?(:clj Exception :cljs :default) _ nil))]
    (if (map? p)
      (-> p
          (update :cites #(vec (or % [])))
          (update :confidence #(if (number? %) (double %) 0.0))
          (update :effect #(or % :noop)))
      {:summary "LLM応答を解釈できませんでした" :rationale (str content)
       :cites [] :effect :noop :stake nil :confidence 0.0})))

(defn llm-advisor
  "An advisor backed by a `langchain.model/ChatModel` (real inference)."
  ([chat-model] (llm-advisor chat-model {}))
  ([chat-model gen-opts]
   (reify Advisor
     (-advise [_ st req]
       (let [msgs [{:role :system :content system-prompt}
                   {:role :user :content (str "操作: " (:op req)
                                              "\n対象: " (:subject req)
                                              "\n事実: " (pr-str (facts-for st req)))}]
             resp (model/-generate chat-model msgs gen-opts)]
         (parse-proposal (:content resp)))))))

(defn trace
  "Decision-grounded audit record -- persisted to the :audit channel."
  [request proposal]
  {:t          :textiletradeadvisor-proposal
   :op         (:op request)
   :subject    (:subject request)
   :summary    (:summary proposal)
   :rationale  (:rationale proposal)
   :cites      (:cites proposal)
   :confidence (:confidence proposal)})
