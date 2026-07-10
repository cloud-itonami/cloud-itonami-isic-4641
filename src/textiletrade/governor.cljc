(ns textiletrade.governor
  "Textile Trading Governor -- the independent compliance layer that earns
  the TextileTradeAdvisor the right to commit. The LLM has no notion of
  jurisdictional customs/sanctions law, whether a counterparty's credit
  has actually been cleared, whether contract terms are actually on
  file, whether a REAL documented supply-chain trace and a REAL
  clear-and-convincing forced-labor rebuttal dossier actually exist for
  THIS textile-order, whether OFAC / equivalent sanctions screening has
  actually been passed, or when an act stops being a draft and becomes a
  real dispatch of textile/apparel/footwear goods or a real invoice
  settlement, so this MUST be a separate system able to *reject* a
  proposal and fall back to HOLD.

  Like the fuel-wholesale and metal-wholesale siblings' own governors,
  this textile-wholesale vertical has NO pre-existing textile-trading
  capability library to delegate to -- so the domain checks (credit-
  clearance, contract-on-file, forced-labor-presumption rebuttal,
  sanctions-screening) are direct entity boolean reads off the
  `textile-order` record, evaluated directly here, NOT delegated to a
  separate library's validated function.

  `:itonami.blueprint/governor` is `:textile-trading-governor`, grep-
  verified UNIQUE fleet-wide -- no naming-collision precedent question,
  a fresh independent build following the SAME governed-actor
  architecture (langgraph StateGraph + independent Governor + Phase
  0->3 rollout) established by `cloud-itonami-isic-6511` and applied by
  the fuel-wholesale (`cloud-itonami-isic-4671`), general-trading
  (`cloud-itonami-isic-4690`), commission-brokerage
  (`cloud-itonami-isic-4610`), agri-wholesale (`cloud-itonami-isic-4620`),
  provision-trading (`cloud-itonami-isic-4630`) and metal-wholesale
  (`cloud-itonami-isic-4662`) siblings.

  CRITICAL STRUCTURAL DIFFERENCE from the metal-wholesale sibling's own
  domain-defining check: `metaltrade.governor`'s conflict-minerals check
  is gated on METAL TYPE ALONE and evaluated UNCONDITIONALLY across every
  jurisdiction, because Dodd-Frank 1502 / EU 2017/821 bind a DOWNSTREAM
  DISCLOSING COMPANY that may sit anywhere in the supply chain relative
  to the metal-wholesaler's own trade, so market participants apply that
  diligence as an operational floor everywhere they trade. The Uyghur
  Forced Labor Prevention Act (UFLPA) is a DIFFERENT kind of mechanism:
  it empowers U.S. Customs and Border Protection to detain and exclude
  merchandise AT THE U.S. BORDER under 19 U.S.C. §1307 -- the exact
  customs-entry act this actor's OWN `:delivery/dispatch` represents
  when the order's own `:jurisdiction` is the United States. So THIS
  check -- `forced-labor-presumption-unrebutted-violations` -- is
  deliberately GATED ON THE ORDER'S OWN JURISDICTION (currently: fires
  only when `:jurisdiction` has a BINDING forced-labor import-ban statute
  in `textiletrade.facts/forced-labor-import-ban-basis`, today only
  \"USA\"), a considered DEPARTURE from the metal-wholesale sibling's
  unconditional-across-jurisdiction design -- see
  `docs/adr/0001-architecture.md` Decision 4 for the full reasoning and
  the three design options considered.

  Six checks, in priority order, ALL HARD violations: a human approver
  CANNOT override them. The confidence/actuation gate is SOFT: it asks
  a human to look (low confidence / actuation), and the human may
  approve -- but see `textiletrade.phase`: for `:stake
  :delivery/dispatch`/`:invoice/settle` (a real dispatch or invoice
  settlement) NO phase ever allows auto-commit either. Two independent
  layers agree that actuation is always a human call.

    1. Spec-basis                  -- did the jurisdiction proposal cite
                                       an OFFICIAL source
                                       (`textiletrade.facts`), or invent
                                       one?
    2. Evidence incomplete         -- for `:delivery/dispatch`/
                                       `:invoice/settle`, has the
                                       jurisdiction actually been
                                       verified with a full counterparty-
                                       diligence evidence checklist on
                                       file?
    3. Credit uncleared            -- for `:delivery/dispatch`, the
                                       counterparty's credit has NOT been
                                       cleared (the leasing collateral-
                                       coverage discipline, applied to
                                       counterparty credit). Evaluated
                                       before dispatch.
    4. Contract missing            -- for `:delivery/dispatch`, no
                                       contract-terms are on file for the
                                       order. Evaluated before dispatch.
    5. Forced-labor presumption
       unrebutted                    -- for `:delivery/dispatch`, WHEN
                                       the order's own jurisdiction
                                       currently has a BINDING forced-
                                       labor import-ban statute AND the
                                       order's origin-region or
                                       manufacturing entity is flagged
                                       (`textiletrade.facts/forced-labor-
                                       presumption-triggered?`), the
                                       order lacks a documented supply-
                                       chain trace OR lacks a clear-and-
                                       convincing forced-labor rebuttal
                                       evidence dossier (or both). THIS
                                       check has no analog in ANY prior
                                       wholesale-trading sibling's
                                       governor: it is this vertical's
                                       own defining regulatory content --
                                       the REBUTTABLE PRESUMPTION
                                       mechanic UFLPA/CAATSA operationalize,
                                       genuinely different in shape from a
                                       simple missing-certificate check
                                       (the presumption is REBUTTABLE: a
                                       flagged order WITH a complete
                                       rebuttal dossier on file does NOT
                                       hold on this check -- see the
                                       function's own docstring and
                                       `test/textiletrade/
                                       governor_contract_test.clj`'s
                                       `forced-labor-presumption-is-
                                       genuinely-rebuttable-not-a-blanket-
                                       ban`). NO-OP for a non-flagged
                                       origin/entity, and NO-OP when the
                                       order's own jurisdiction has no
                                       CURRENTLY BINDING forced-labor
                                       import-ban statute. Evaluated
                                       before dispatch.
    6. Counterparty sanctions flag
       unresolved                    -- for `:delivery/dispatch` and
                                       `:invoice/settle`, the counterparty
                                       has NOT passed OFAC / equivalent
                                       sanctions screening -- a HARD,
                                       un-overridable hold. Evaluated
                                       UNCONDITIONALLY at both actuation
                                       ops.
    7. Confidence floor / actuation
       gate                          -- LLM confidence below threshold,
                                       OR the op is `:delivery/dispatch`/
                                       `:invoice/settle` (REAL acts)
                                       -> escalate.

  Two more guards, double-dispatch/double-invoice prevention, are
  enforced but NOT listed as numbered HARD checks above because they
  need no upstream comparison at all -- `already-dispatched-violations`/
  `already-invoiced-violations` refuse to dispatch/invoice the SAME
  textile-order twice, off dedicated `:dispatched?`/`:invoiced?` facts
  (never a `:status` value) -- the SAME 'check a dedicated boolean, not
  status' discipline every prior governor's guards establish, informed
  by `cloud-itonami-isic-6492`'s status-lifecycle bug (ADR-2607071320)."
  (:require [textiletrade.facts :as facts]
            [textiletrade.store :as store]))

(def confidence-floor 0.6)

(def high-stakes
  "Stakes grave enough to always require a human, even when clean.
  Dispatching real textile/apparel/footwear goods to a counterparty from
  the wholesale warehouse and settling a real invoice (real money moving
  between counterparty and trader) are the two real-world actuation
  events this actor performs -- a two-member set, matching every
  sibling's own dual-actuation shape."
  #{:delivery/dispatch :invoice/settle})

;; ----------------------------- checks -----------------------------

(defn- spec-basis-violations
  "A `:supply-chain/verify` (or `:delivery/dispatch`/`:invoice/settle`)
  proposal with no spec-basis citation is a HARD violation -- never
  invent a jurisdiction's customs/sanctions requirements."
  [{:keys [op]} proposal]
  (when (contains? #{:supply-chain/verify :delivery/dispatch :invoice/settle} op)
    (let [value (:value proposal)]
      (when (or (empty? (:cites proposal))
                (and (contains? value :spec-basis) (nil? (:spec-basis value))))
        [{:rule :no-spec-basis
          :detail "公式spec-basisの引用が無い提案は法域要件として扱えない"}]))))

(defn- evidence-incomplete-violations
  "For `:delivery/dispatch`/`:invoice/settle`, the jurisdiction's
  required GENERAL counterparty-diligence evidence (credit-clearance
  record, contract/PO, sanctions-screening record) must actually be
  satisfied -- do not trust the advisor's self-reported confidence
  alone. Deliberately does NOT check forced-labor rebuttal evidence --
  that is `forced-labor-presumption-unrebutted-violations` below, gated
  on the order's origin/entity/jurisdiction rather than the generic
  per-jurisdiction checklist."
  [{:keys [op subject]} st]
  (when (contains? #{:delivery/dispatch :invoice/settle} op)
    (let [to (store/textile-order st subject)
          assessment (store/assessment-of st subject)]
      (when-not (and assessment
                     (facts/required-evidence-satisfied?
                      (:jurisdiction to) (:checklist assessment)))
        [{:rule :evidence-incomplete
          :detail "法域の必要書類(信用審査記録/契約書またはPO/制裁スクリーニング記録)が充足していない状態での提案"}]))))

(defn- credit-uncleared-violations
  "For `:delivery/dispatch`, refuses to dispatch textile/apparel/
  footwear goods to a counterparty whose credit has NOT been cleared --
  counterparty credit not cleared (the leasing collateral-coverage
  discipline, applied to counterparty credit). Evaluated at the
  warehouse, ahead of any physical pick/pack."
  [{:keys [op subject]} st]
  (when (= op :delivery/dispatch)
    (let [to (store/textile-order st subject)]
      (when (not (true? (:credit-cleared? to)))
        [{:rule :credit-uncleared
          :detail (str subject " の取引先信用審査(credit-clearance)が未了 -- 出荷提案は進められない")}]))))

(defn- contract-missing-violations
  "For `:delivery/dispatch`, refuses to dispatch textile/apparel/
  footwear goods when no contract-terms are on file for the order."
  [{:keys [op subject]} st]
  (when (= op :delivery/dispatch)
    (let [to (store/textile-order st subject)]
      (when (or (nil? (:contract-terms to)) (= "" (:contract-terms to)))
        [{:rule :contract-missing
          :detail (str subject " に契約条項(contract-terms)の記録が無い -- 出荷提案は進められない")}]))))

(defn- forced-labor-presumption-unrebutted-violations
  "For `:delivery/dispatch`, WHEN the order's own jurisdiction currently
  has a BINDING forced-labor import-ban statute AND the order's origin-
  region or manufacturing entity is flagged
  (`textiletrade.facts/forced-labor-presumption-triggered?`), refuses to
  dispatch UNLESS BOTH a documented supply-chain trace
  (`:supply-chain-traceability-documented?`) AND a clear-and-convincing
  forced-labor rebuttal evidence dossier
  (`:forced-labor-rebuttal-evidence-on-file?`, e.g. entity-specific
  employment/wage records, third-party audit reports -- the kind of
  evidence CBP's UFLPA Operational Guidance for Importers describes) are
  on file. Folds these TWO distinct real-world sub-requirements into ONE
  named rule -- the SAME discipline the metal-wholesale sibling's
  `conflict-minerals-provenance-unverified-violations` check establishes
  (see `docs/adr/0001-architecture.md` Decision 4): a supply-chain trace
  without the broader clear-and-convincing evidentiary dossier (or vice
  versa) is EQUALLY unsafe to dispatch against, because REBUTTING a
  REBUTTABLE PRESUMPTION under UFLPA genuinely requires both arms -- the
  `:detail` string still names which sub-fact specifically failed, so no
  audit-ledger precision is lost.

  THIS IS THE MECHANIC THAT DISTINGUISHES A REBUTTABLE PRESUMPTION FROM A
  SIMPLE MISSING-CERTIFICATE CHECK: this rule does NOT fire at all for a
  non-flagged origin/entity (there is no presumption to rebut), and for a
  FLAGGED origin/entity it does NOT fire when BOTH rebuttal facts are
  true -- the presumption is genuinely REBUTTABLE, not a blanket
  regional ban. `test/textiletrade/governor_contract_test.clj`'s
  `forced-labor-presumption-is-genuinely-rebuttable-not-a-blanket-ban`
  proves this directly: the SAME flagged-origin order HOLDS with no
  rebuttal evidence on file and dispatches cleanly (still always
  escalating for the ordinary human sign-off) once BOTH rebuttal facts
  are documented.

  UNLIKE the metal-wholesale sibling's conflict-minerals check (gated on
  metal type alone, evaluated UNCONDITIONALLY regardless of
  jurisdiction), this check is deliberately GATED ON THE ORDER'S OWN
  JURISDICTION -- see namespace-level reasoning in `textiletrade.facts`
  and this namespace's own docstring: UFLPA operationalizes a U.S.
  BORDER import-ban mechanism, not a downstream-disclosure obligation
  that could attach anywhere in the chain, so it is honest to gate the
  check on whether THIS actor's own dispatch act is itself a
  U.S.-jurisdiction customs entry."
  [{:keys [op subject]} st]
  (when (= op :delivery/dispatch)
    (let [to (store/textile-order st subject)]
      (when (and (facts/forced-labor-presumption-triggered? to)
                 (not (and (true? (:supply-chain-traceability-documented? to))
                           (true? (:forced-labor-rebuttal-evidence-on-file? to)))))
        [{:rule :forced-labor-presumption-unrebutted
          :detail (str subject " (origin=" (:origin-region to)
                       ", entity-list-flagged?=" (boolean (:entity-list-flagged? to))
                       ") のUFLPA/CAATSA強制労働の反証可能な推定(rebuttable presumption)に対し、"
                       "サプライチェーン追跡記録(supply-chain-traceability)="
                       (boolean (:supply-chain-traceability-documented? to))
                       " / 明白かつ説得力のある反証エビデンス(clear-and-convincing rebuttal evidence)="
                       (boolean (:forced-labor-rebuttal-evidence-on-file? to))
                       " -- いずれかが未充足のため出荷提案は進められない")}]))))

(defn- counterparty-sanctions-flag-unresolved-violations
  "For `:delivery/dispatch` and `:invoice/settle`, an unresolved
  sanctions-screening flag -- the counterparty has NOT passed OFAC /
  equivalent sanctions screening -- is a HARD, un-overridable hold.
  Evaluated UNCONDITIONALLY at both actuation ops: neither goods nor
  money moves against an unscreened counterparty."
  [{:keys [op subject]} st]
  (when (contains? #{:delivery/dispatch :invoice/settle} op)
    (let [to (store/textile-order st subject)]
      (when (not (true? (:sanctions-screened? to)))
        [{:rule :counterparty-sanctions-flag-unresolved
          :detail (str subject " の取引先制裁スクリーニング(OFAC等)が未了 -- 出荷・請求提案は進められない")}]))))

(defn- already-dispatched-violations
  "For `:delivery/dispatch`, refuses to dispatch the SAME textile-order
  twice, off a dedicated `:dispatched?` fact (never a `:status` value)."
  [{:keys [op subject]} st]
  (when (= op :delivery/dispatch)
    (when (store/textile-order-already-dispatched? st subject)
      [{:rule :already-dispatched
        :detail (str subject " は既に出荷済み")}])))

(defn- already-invoiced-violations
  "For `:invoice/settle`, refuses to settle the SAME textile-order's
  invoice twice, off a dedicated `:invoiced?` fact (never a `:status`
  value)."
  [{:keys [op subject]} st]
  (when (= op :invoice/settle)
    (when (store/textile-order-already-invoiced? st subject)
      [{:rule :already-invoiced
        :detail (str subject " は既に請求済み")}])))

(defn check
  "Censors a TextileTradeAdvisor proposal against the governor rules.
  Returns {:ok? bool :violations [..] :confidence c :escalate? bool
  :high-stakes? bool :hard? bool}."
  [request _context proposal st]
  (let [hard (into []
                   (concat (spec-basis-violations request proposal)
                           (evidence-incomplete-violations request st)
                           (credit-uncleared-violations request st)
                           (contract-missing-violations request st)
                           (forced-labor-presumption-unrebutted-violations request st)
                           (counterparty-sanctions-flag-unresolved-violations request st)
                           (already-dispatched-violations request st)
                           (already-invoiced-violations request st)))
        conf (:confidence proposal 0.0)
        low? (< conf confidence-floor)
        stakes? (boolean (high-stakes (:stake proposal)))
        hard? (boolean (seq hard))]
    {:ok?          (and (not hard?) (not low?) (not stakes?))
     :violations   hard
     :confidence   conf
     :hard?        hard?
     :escalate?    (and (not hard?) (or low? stakes?))
     :high-stakes? stakes?}))

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
