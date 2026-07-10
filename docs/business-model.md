# Business Model: Wholesale of Textiles, Clothing and Footwear

## Classification
- Repository: `cloud-itonami-isic-4641`
- ISIC Rev.5: `4641` — wholesale of textiles, clothing and footwear
- Domain: `downstream/textile-wholesale`
- Social impact: human rights, labor rights, transparency
- Governor: `:textile-trading-governor`
- License: AGPL-3.0-or-later

## Scope
This actor covers textile-order intake through per-jurisdiction contract
/ sanctions regulatory verification, forced-labor supply-chain
rebuttable-presumption verification (for orders whose origin region or
manufacturing entity is flagged, and whose own jurisdiction currently
binds a forced-labor import-ban statute), textile/apparel/footwear goods
dispatch (goods leaving the wholesale warehouse for a counterparty), and
invoice settlement (the money side of the trade, custody / financial
transfer) for a wholesaler of textiles, clothing and footwear. It does
**not**, by itself, hold any textile-wholesale licence, import authority
or operating authority required to run a textile-wholesale business in a
given jurisdiction, perform the actual physical warehouse pick/pack/
palletize, or judge trading-book economics (inventory and route
optimization is a follow-up slice, not this R0). Whoever deploys a live
instance supplies the jurisdiction-specific operating authority, the
real warehouse-automation/ERP integrations, and bears that
jurisdiction's liability -- the software supplies the governed,
spec-cited, audited execution scaffold so the operator does not have to
build the compliance layer from scratch.

## Customer
- regional and independent textile, apparel and footwear wholesalers and
  distribution-center operators
- importers/brand distributors leaving closed trading / ERP SaaS
- apparel and footwear brands, retailers and their compliance teams who
  need UFLPA-aware dispatch controls their generic warehouse-management
  system does not enforce
- counterparties, banks, customs brokers and regulators who need an
  auditable, spec-cited, supply-chain-cited trade record

## Offer
- textile-order intake and directory management, across all three goods
  kinds (textile/fabric, apparel, footwear) in one system
- per-jurisdiction contract / sanctions regulatory verification with an
  official spec-basis citation
- forced-labor supply-chain rebuttable-presumption verification for
  orders whose origin region (e.g. the Xinjiang Uyghur Autonomous
  Region) or manufacturing entity (the CBP UFLPA Entity List) is
  flagged, with an honest jurisdiction-by-jurisdiction legal-basis
  citation (UFLPA/19 U.S.C. §1307 for the USA; the forthcoming EU
  Regulation 2024/3015 seeded for forward compatibility)
- dispatch (warehouse dispatch) gated on full evidence, a credit-cleared
  counterparty, contract-terms on file, an unrebutted forced-labor
  presumption cleared (where triggered) and a passed sanctions screen
- invoice settlement (custody / financial transfer) with double-invoice
  prevention
- evidence checklisting (credit-clearance record, contract/PO,
  sanctions-screening record, plus supply-chain traceability and
  clear-and-convincing rebuttal evidence for flagged-origin/entity
  orders)
- sanctions and credit exception workflows
- role-based access and immutable audit ledger

## Revenue
- self-host setup fee
- managed hosting subscription per trader / warehouse
- support retainer with SLA
- ERP and accounts-receivable integration
- downstream-buyer UFLPA reasonable-care compliance reporting add-on
  (export of a counterparty's own supply-chain-traceability and
  rebuttal-evidence trail, for a brand/retailer running its OWN UFLPA
  reasonable-care due diligence)

## The `:textile-trading-governor` Decision Rule

This blueprint's `:itonami.blueprint/governor` is `:textile-trading-
governor`. It is the single authority that stands between "textile/
apparel/footwear goods could be dispatched to a counterparty" and "it is
allowed to leave the wholesale warehouse," and between "an invoice could
be settled" and "it is allowed to settle." Every rule it enforces is
traceable to the domain (Wholesale of Textiles, Clothing and Footwear,
ISIC 4641) and to the three `:social-impact` tags in `blueprint.edn`
(`:human-rights`, `:labor-rights`, `:transparency`).

This is the rule the companion contract test
(`test/textiletrade/governor_contract_test.clj`) encodes end-to-end: the
TextileTradeAdvisor never dispatches goods to a counterparty or settles
an invoice the Textile Trading Governor would reject, `:delivery/
dispatch` and `:invoice/settle` NEVER auto-commit at any phase,
`:order/intake` (no direct capital risk) MAY auto-commit when clean, and
every decision (commit OR hold) leaves exactly one ledger fact.

**Authorizes a dispatch (`:delivery/dispatch`) or invoice settlement
(`:invoice/settle`) only when ALL of the following hold:**

1. **An official spec-basis citation exists for the jurisdiction** -- the
   governor will not authorize any `:supply-chain/verify`, `:delivery/
   dispatch`, or `:invoice/settle` proposal whose jurisdiction has no
   entry in the `textiletrade.facts` catalog (`:no-spec-basis`). This is
   the direct enforcement of `:transparency`: a jurisdiction whose
   customs/sanctions requirements cannot be traced to an OFFICIAL public
   source is never guessed. The advisor must not fabricate a
   jurisdiction's requirements.
2. **The jurisdiction's required GENERAL evidence is fully on file** --
   for a dispatch or invoice the order's jurisdiction must have been
   verified with a complete counterparty-diligence evidence checklist on
   record: the credit-clearance record, the contract / purchase order,
   and the sanctions-screening (OFAC / equivalent) record
   (`:evidence-incomplete`). This is deliberately the SAME 3-item generic
   checklist every wholesale-trading sibling uses -- it does NOT include
   forced-labor rebuttal evidence, which is check #5 below.
3. **The counterparty's credit has been cleared** -- the governor reads
   the dedicated `:credit-cleared?` fact on the order and refuses to
   dispatch goods when credit has NOT been cleared (the leasing
   collateral-coverage discipline, applied to counterparty credit)
   (`:credit-uncleared`). Evaluated at `:delivery/dispatch`.
4. **Contract-terms are on file** -- the governor refuses to dispatch
   when no `:contract-terms` are recorded for the order
   (`:contract-missing`). Goods never leave the warehouse against an
   undocumented trade. Evaluated at `:delivery/dispatch`.
5. **For an order whose origin region or manufacturing entity is
   flagged, AND whose own jurisdiction currently binds a forced-labor
   import-ban statute, the forced-labor rebuttable presumption has been
   REBUTTED** -- the governor reads the dedicated `:supply-chain-
   traceability-documented?` AND `:forced-labor-rebuttal-evidence-on-
   file?` facts and refuses to dispatch UNLESS BOTH are true
   (`:forced-labor-presumption-unrebutted`). This check is a NO-OP for a
   non-flagged origin/entity, AND a NO-OP for a flagged origin/entity
   whose own jurisdiction has no CURRENTLY BINDING forced-labor
   import-ban statute (today: fires only for `:jurisdiction "USA"`) --
   UNLIKE the metal-wholesale sibling's conflict-minerals check (gated
   on commodity type alone, evaluated unconditionally across
   jurisdiction), this check is deliberately GATED ON THE ORDER'S OWN
   JURISDICTION, because the Uyghur Forced Labor Prevention Act (UFLPA)
   is a U.S.-BORDER import-enforcement mechanism (CBP detains/excludes
   merchandise at the exact customs-entry act this actor's own
   `:delivery/dispatch` represents when the order's jurisdiction is the
   USA), not a downstream-disclosure obligation that could attach
   anywhere in the chain the way Dodd-Frank 1502/EU 2017/821 do -- see
   Implementation notes and `docs/adr/0001-architecture.md` Decision 4.
   CRITICALLY, this presumption is REBUTTABLE, not a blanket regional
   ban: a flagged-origin order WITH both rebuttal facts documented does
   NOT hold on this check (see `docs/adr/0001-architecture.md` Decision
   4 and the bundled demo's `to-6`/`to-7` pair). Evaluated at
   `:delivery/dispatch`.
6. **The counterparty has passed OFAC / equivalent sanctions screening**
   -- the governor reads the dedicated `:sanctions-screened?` fact and
   treats an unresolved sanctions-screening flag as a HARD, un-
   overridable hold (`:counterparty-sanctions-flag-unresolved`). Neither
   product nor money moves against an unscreened counterparty. Evaluated
   UNCONDITIONALLY at both `:delivery/dispatch` and `:invoice/settle`.
7. **The order has not already been dispatched, and the invoice has not
   already been settled** -- a double dispatch of the same order is
   refused off a dedicated `:dispatched?` fact, and a double invoice off
   a dedicated `:invoiced?` fact (never a `:status` value), the
   double-actuation guard every sibling actor in this fleet enforces
   (`:already-dispatched` / `:already-invoiced`).

**Rejects (HOLD, un-overridable, never even reaches a human) when any of
the above fail.** A proposal with no spec-basis, incomplete evidence, an
uncleared counterparty credit, no contract-terms on file, an unrebutted
forced-labor presumption on a qualifying origin/entity/jurisdiction, an
unresolved sanctions-screening flag, or a double dispatch/invoice is
held at the governor node -- a human approver cannot override these, by
construction.

**Always escalates to a human (never auto-commits) for `:delivery/
dispatch` and `:invoice/settle`**, even when every check above is clean.
Dispatching real textile/apparel/footwear goods to a counterparty from
the wholesale warehouse and settling a real invoice (real money moving
between counterparty and trader) are the two real-world actuation events
this actor performs; both are always a human trading supervisor's call.
This is enforced by TWO independent layers that agree on purpose: the
governor's confidence / actuation SOFT gate (a `:delivery/dispatch` /
`:invoice/settle` stake always escalates) and `textiletrade.phase`'s
phase table, which never puts either op in any phase's `:auto` set. The
`:human-rights`/`:labor-rights` tags are enforced upstream of the
governor, in the forced-labor supply-chain verification evidence step --
the governor's job is dispatch/invoice authorization integrity, not
trading-book optimization.

## Required Technologies

`blueprint.edn`'s `:itonami.blueprint/required-technologies` for this
business, and what each one is actually load-bearing for here (not a
generic capability list):

| Technology | What it is FOR in Wholesale of Textiles, Clothing and Footwear |
|---|---|
| `:robotics` | The autonomous garment-on-hanger (GOH) sortation conveyor robot that performs the physical hanging-apparel pick, and the autonomous pick-to-light/AS-RS (automated storage-and-retrieval) robot that performs the physical footwear/folded-textile pick, at the wholesale warehouse. The governor never dispatches hardware itself: a dispatch-clearing action must have cleared the same sign-off a human trading supervisor would need (see Robotics Premise). |
| `:identity` | Trader, trading-supervisor, warehouse-operator and counterparty identity plus role-based access, so the governor's sign-off is tied to *who* authorized a dispatch or invoice, not just *that* someone did. |
| `:forms` | Structured intake for textile-order booking, per-jurisdiction evidence capture (credit-clearance record, contract/PO, sanctions-screening record), forced-labor supply-chain evidence capture (supply-chain traceability documentation, clear-and-convincing rebuttal evidence), and sanctions / credit exception submission -- the data the Decision Rule above actually evaluates comes in through these forms. |
| `:dmn` | Encodes the `:textile-trading-governor` Decision Rule itself (spec-basis, evidence completeness, credit-clearance, contract-on-file, forced-labor-presumption rebuttal, sanctions-screening, the double-actuation guards, the actuation gate) as an evaluable decision table rather than code buried in application logic -- this is what makes the governor auditable and swappable per-deployment. |
| `:bpmn` | Orchestrates the intake -> verify -> dispatch -> settle -> audit loop end-to-end (see `docs/operator-guide.md`) across textile-order intake, supply-chain verification, dispatch, and invoice settlement, including the sanctions / credit escalation gate. |
| `:audit-ledger` | The immutable record of every verification, dispatch, invoice, sanctions flag, and hold -- this is what "an auditable, spec-cited, supply-chain-cited trade record for every dispatch and invoice" (Trust Controls, below) actually means in practice, and the evidence an operator (or a downstream buyer running its OWN UFLPA reasonable-care due diligence) needs if a dispatch or an invoice is later disputed. |
| `:optimization` | Warehouse-slotting, route optimization and trading-book optimization -- selects the profitable fulfillment strategy for a warehouse. This R0 build deliberately scopes optimization OUT (see README `Business-process coverage`); the capability is correctly marked required, the integration is a follow-up slice. |

There is NO bespoke `:textiletrade` capability library in this stack
(unlike the freight sibling's `:logistics`): the textile-trading checks
(credit-clearance, contract-on-file, forced-labor-presumption rebuttal,
sanctions-screening) are direct entity boolean reads in
`textiletrade.governor`, on top of the generic robotics/identity/forms/
dmn/bpmn/audit-ledger stack (see Capability layer).

## Trust Controls
- a jurisdiction with no official spec-basis can never be verified,
  dispatched, or invoiced against
- a dispatch never starts with incomplete counterparty-diligence evidence
- a dispatch never starts with an uncleared counterparty credit or no
  contract-terms on file
- a dispatch of goods whose origin region or manufacturing entity is
  flagged under UFLPA/CAATSA, in a jurisdiction where that presumption
  currently binds, never starts without BOTH a documented supply-chain
  trace AND a clear-and-convincing forced-labor rebuttal evidence
  dossier on file
- a dispatch or invoice never settles against an unresolved sanctions-
  screening flag
- sanctions / credit / forced-labor-rebuttal flags cannot be silently
  suppressed
- the same order can never be dispatched or invoiced twice
- a dispatch or invoice never auto-commits; both always need a human
  trading supervisor
- every dispatch and invoice (commit OR hold) leaves exactly one
  immutable ledger fact
- counterparty, credit, sanctions and supply-chain sourcing data stays
  outside Git

## Implementation notes (`:implemented`)

The Decision Rule above is implemented faithfully by
`textiletrade.governor` as six HARD checks (a human approver cannot
override them) plus one SOFT gate:

- `spec-basis-violations` -- the spec-basis check above, evaluated on
  every `:supply-chain/verify`, `:delivery/dispatch`, and
  `:invoice/settle`.
- `evidence-incomplete-violations` -- the GENERAL evidence-completeness
  check above, for `:delivery/dispatch` / `:invoice/settle`.
- `credit-uncleared-violations` -- the counterparty-credit check above
  (the leasing collateral-coverage discipline applied to counterparty
  credit); evaluated on every `:delivery/dispatch`.
- `contract-missing-violations` -- the contract-on-file check above;
  evaluated on every `:delivery/dispatch`.
- `forced-labor-presumption-unrebutted-violations` -- the forced-labor
  rebuttable-presumption check above, gated on
  `textiletrade.facts/forced-labor-presumption-triggered?` (origin-
  region OR manufacturing-entity flagged, AND the order's own
  jurisdiction currently binds a forced-labor import-ban statute);
  evaluated on every `:delivery/dispatch`. THIS IS THE DOMAIN-DEFINING
  CHECK -- no analog in the fuel-wholesale, general-trading, commission-
  brokerage, agri-wholesale or provision-trading siblings' governors; it
  is analogous in SHAPE (not in trigger or gating axis) to the metal-
  wholesale sibling's conflict-minerals-provenance-unverified check --
  see "Why the forced-labor check is jurisdiction-gated" below.
- `counterparty-sanctions-flag-unresolved-violations` -- the sanctions-
  screening check above (the same open-flag-unresolved discipline the
  freight sibling's delivery-exception-unresolved check establishes);
  evaluated unconditionally on both `:delivery/dispatch` and
  `:invoice/settle`.
- `already-dispatched-violations` / `already-invoiced-violations` -- the
  double-actuation guards above, off dedicated `:dispatched?` /
  `:invoiced?` booleans (never a `:status` value), the same discipline
  every sibling governor's guards establish.
- the confidence floor / actuation SOFT gate -- low confidence, OR a
  `:delivery/dispatch` / `:invoice/settle` stake, escalates to a human;
  and `textiletrade.phase` independently never auto-commits either op at
  any phase.

Unlike the crude-extraction sibling's governor (which calls pure
physical range-check functions in its registry), this governor needs no
range-check functions at all: its domain checks read the `textile-order`
record's own dedicated booleans directly. `:delivery/dispatch` and
`:invoice/settle` are the two real-world actuation events
(`#{:delivery/dispatch :invoice/settle}`), applied SEQUENTIALLY to the
SAME textile-order (dispatch first, invoice settlement later), the same
sequential dual-actuation shape the fuel-wholesale, agri-wholesale,
provision-trading and metal-wholesale clusters use. Neither ever
auto-commits at any phase. Warehouse-slotting/route optimization and
trading-book optimization (the `:optimization` line above) is a
follow-up slice, not in this R0 build -- see README `Business-process
coverage`.

## Why the forced-labor check is a SEPARATE check, jurisdiction-gated -- NOT unconditional like the metal-wholesale sibling's conflict-minerals check

The generic `evidence-incomplete-violations` check (present in every
wholesale-trading sibling) verifies the jurisdiction's counterparty-
diligence paperwork -- credit-clearance record, contract/PO, sanctions-
screening record -- keyed by WHERE the trade happens. Forced-labor
supply-chain rebuttal is a genuinely different kind of fact: it verifies
the SHIPMENT'S OWN origin and manufacturing provenance, independent of
the generic paperwork. This makes it SHAPE-analogous to the metal-
wholesale sibling's conflict-minerals-provenance-unverified check (a
commodity/shipment property, not a generic-paperwork property) -- but
the two checks differ in a load-bearing way on WHETHER jurisdiction
gates them, and the difference is not arbitrary: it follows directly
from how the two underlying real-world legal regimes actually work.

Dodd-Frank Section 1502 and EU Regulation 2017/821 (the metal-
wholesale sibling's conflict-minerals statutes) do not regulate WHERE a
trade happens -- they regulate a DOWNSTREAM COMPANY'S disclosure/due-
diligence obligation (a US SEC-reporting issuer; an EU importer), which
may sit ANYWHERE in the supply chain relative to where the
metal-wholesaler's own trade occurs. That is why the metal-wholesale
sibling's ADR (Decision 4) correctly makes that check UNCONDITIONAL
across jurisdiction: real market participants who trade 3TG/cobalt apply
that diligence as an operational floor essentially everywhere they
trade, because their downstream counterparty's exposure does not depend
on where THIS trader's own sale happens.

The Uyghur Forced Labor Prevention Act (UFLPA) works differently. It
empowers U.S. Customs and Border Protection to DETAIN AND EXCLUDE
merchandise AT THE U.S. BORDER under the underlying forced-labor import
ban, 19 U.S.C. §1307 -- the presumption specifically bars
IMPORTATION INTO THE UNITED STATES. When THIS actor's own `:delivery/
dispatch` represents a customs entry into a jurisdiction OTHER than the
United States (say, a textile-order whose own `:jurisdiction` is JPN),
UFLPA's border-detention mechanism does not attach to THIS actor's own
dispatch act -- a downstream party who later resells into the US would
separately face their OWN UFLPA exposure on THEIR OWN importation, which
is outside the scope of what this actor's own dispatch record
represents. Three design options were considered (see
`docs/adr/0001-architecture.md` Decision 4 for the full reasoning):
folding forced-labor evidence into the generic per-jurisdiction
checklist (rejected, for the same reason the metal-wholesale sibling
rejected it -- it would force every jurisdiction's checklist to carry a
conditional item irrelevant to most orders); a jurisdiction-unconditional
check copying the metal-wholesale sibling's shape exactly (rejected --
this would misrepresent UFLPA as a global operational floor the way
conflict-minerals diligence genuinely is, when in fact its detention
mechanism is inherently tied to the act of U.S. importation); and the
CHOSEN option, a check gated on the order's OWN jurisdiction currently
binding a forced-labor import-ban statute (today: USA only) AND the
order's origin/entity being flagged. This is an intentional,
well-reasoned DEPARTURE from the metal-wholesale sibling's design, not
an oversight -- the two checks are SHAPE-analogous (both gate on a
shipment-intrinsic property, both fold two sub-requirements into one
named rule, both are the fleet's domain-defining check for their
vertical) but differ correctly on the gating axis because the two
underlying legal regimes are genuinely different in kind.

## Capability layer

Like the fuel-wholesale (`cloud-itonami-isic-4671`), general-trading
(`cloud-itonami-isic-4690`), commission-brokerage
(`cloud-itonami-isic-4610`), agri-wholesale (`cloud-itonami-isic-4620`),
provision-trading (`cloud-itonami-isic-4630`) and metal-wholesale
(`cloud-itonami-isic-4662`) siblings, this vertical is SELF-CONTAINED:
there is no `kotoba-lang/textiletrade` to delegate textile-trading
validation to. The credit-clearance / contract-on-file / forced-labor-
presumption-rebuttal / sanctions-screening checks live as direct entity
boolean reads in `textiletrade.governor` (off dedicated `:credit-
cleared?` / `:contract-terms` / `:supply-chain-traceability-documented?`
/ `:forced-labor-rebuttal-evidence-on-file?` / `:sanctions-screened?`
facts on the `textile-order` record) -- this vertical's governor needs
no pure range-check functions at all, because its domain checks ARE
direct boolean reads.

## Jurisdiction coverage (honest)

`textiletrade.facts/catalog` currently seeds 4 jurisdictions with an
official GENERAL (customs/sanctions) spec-basis, each a REAL regime:

- **Japan (JPN)** -- 関税法 (Customs Act) and 輸出貿易管理令 (Export Trade
  Control Order), administered by 財務省 (MOF) Customs and 経済産業省
  (METI). I am highly confident about this citation (the same general
  customs/export-control basis established across this fleet's other
  wholesale-trading siblings, honestly reused here since it genuinely
  covers ANY commodity, not textile-specific).
- **United States (USA)** -- the Tariff Act of 1930 (19 U.S.C. Chapter 4)
  customs entry requirements, administered by U.S. Customs and Border
  Protection (CBP), plus OFAC (Treasury) sanctions programs -- I am
  reasonably confident about CBP's general customs role and OFAC's
  sanctions role, reused honestly from this fleet's other siblings.
  ADDITIONALLY, for this vertical specifically, I cite two REAL
  textile-specific U.S. consumer-protection statutes: the **Textile
  Fiber Products Identification Act** (15 U.S.C. §70 et seq.), which
  requires fiber-content labeling on textile products and is
  administered by the Federal Trade Commission (FTC); and the
  **Flammable Fabrics Act** (15 U.S.C. §1191 et seq.), which sets
  flammability standards for clothing textiles (including the well-known
  children's-sleepwear flammability standards at 16 C.F.R. Parts
  1615/1616) and is administered by the Consumer Product Safety
  Commission (CPSC). I am highly confident both statutes are real and
  genuinely apply to this vertical -- they are cited here informationally
  (in the jurisdiction's `:legal-basis` narrative), the same way the
  fuel-wholesale sibling folds the U.S. fuel excise tax into its own USA
  citation, WITHOUT expanding the generic 3-item evidence checklist (see
  `textiletrade.facts/required-evidence-satisfied?`'s docstring).
- **United Kingdom (GBR)** -- the Taxation (Cross-border Trade) Act 2018,
  administered by HM Revenue & Customs (HMRC), plus UK financial
  sanctions under the Sanctions and Anti-Money Laundering Act 2018
  (SAMLA 2018), administered by the Office of Financial Sanctions
  Implementation (OFSI). I am reasonably confident about both Act names
  and the agency split, reused honestly from this fleet's other
  siblings; I have not independently verified the precise post-Brexit
  customs-code cross-references.
- **Germany (DEU)**, representing the EU regime -- the Union Customs Code
  (Regulation (EU) No 952/2013), administered on the ground by the
  Generalzolldirektion (German Customs) under the Bundesministerium der
  Finanzen (BMF), plus EU financial sanctions regulations. I am highly
  confident about the Union Customs Code citation (a well-known,
  directly-applicable EU regulation), reused honestly from this fleet's
  metal-wholesale sibling.

`textiletrade.facts/forced-labor-import-ban-basis` seeds a SEPARATE,
jurisdiction-keyed catalog with a forced-labor rebuttable-presumption
import-ban citation for only 2 of the 4 jurisdictions above:

- **United States (USA)** -- the **Uyghur Forced Labor Prevention Act**
  (UFLPA, Pub. L. 117-78, 2021), CBP-enforced, operationalizing the
  underlying forced-labor import ban at **19 U.S.C. §1307**. This is
  REAL, CURRENT, ACTIVELY-ENFORCED U.S. law (enacted December 2021) -- I
  am highly confident about this citation, including the rebuttable-
  presumption mechanic itself: goods mined, produced, or manufactured
  wholly or in part in the Xinjiang Uyghur Autonomous Region (or by an
  entity on the CBP-published UFLPA Entity List,
  https://www.dhs.gov/uflpa-entity-list) are presumed to be made with
  forced labor and barred from importation, unless the importer
  overcomes the presumption with clear and convincing evidence. I
  ALSO cite, alongside UFLPA, the **Countering America's Adversaries
  Through Sanctions Act (CAATSA, Pub. L. 115-44, 2017), Section 321(b)**,
  which I understand creates a PARALLEL rebuttable presumption under the
  same 19 U.S.C. §1307 for goods produced with North Korean labor
  (including in a third country). I am reasonably, but not fully,
  confident about the precise CAATSA section number -- this should be
  independently verified before this citation is relied upon
  operationally; I am highly confident the underlying mechanism (a
  North-Korean-labor rebuttable presumption under 19 U.S.C. §1307) is
  real. `:binding? true`.
- **Germany (DEU), representing the EU** -- **Regulation (EU) 2024/3015**
  prohibiting products made with forced labour from being placed or made
  available on the Union market (or exported from it). I am highly
  confident this regulation exists, was formally adopted in 2024, and
  prohibits forced-labour products from the EU market. I am only
  MODERATELY confident about the precise phased-application timeline --
  my understanding is that full application begins roughly three years
  after entry into force (approximately 2027) per the regulation's own
  transitional provisions, with a longer transition for certain
  database-related provisions -- this should be independently verified
  before this catalog is relied on operationally. Because I am not
  confident the regulation is YET in full binding application as of this
  catalog's authoring date, it is seeded here at `:binding? false`: the
  governor does NOT currently gate a DEU/EU-jurisdiction dispatch on it.
  Flipping this to `true` once the regulation's phased application
  actually takes effect is a one-line change (see
  `textiletrade.facts/forced-labor-import-ban-basis` docstring), not a
  re-architecture.

Every other jurisdiction -- including JPN and GBR, both present in the
GENERAL catalog above -- has NO forced-labor rebuttable-presumption
IMPORT-BAN statute seeded here. This is an honest gap, not an oversight:
unlike the metal-wholesale sibling's OECD-Guidance universal fallback (a
genuine non-statutory operational baseline every jurisdiction's
3TG/cobalt trade observes), I am not confident there is a comparable JPN
or GBR statute creating an import-ban REBUTTABLE PRESUMPTION mechanism
the way UFLPA/CAATSA do, so none is fabricated here. The UK's Modern
Slavery Act 2015 requires large companies to publish supply-chain
transparency STATEMENTS, but -- to the best of my knowledge -- does not
itself create a border-enforcement rebuttable-presumption import-ban
mechanism comparable to UFLPA, so it is deliberately NOT cited as a
`forced-labor-import-ban-basis` entry (a transparency-statement
obligation is a materially different legal mechanism from an import
ban).

This is a starting catalog to prove the governor contract end-to-end,
not a claim of global coverage (4 of ~194 jurisdictions worldwide for
the general catalog; 2 of those 4 for the forced-labor-specific
catalog, only 1 of which is currently binding). Adding a jurisdiction,
an origin-region, or a forced-labor statute for an already-seeded
jurisdiction is additive: one map/set entry in
`textiletrade.facts/catalog`, `textiletrade.facts/flagged-origins`, or
`textiletrade.facts/forced-labor-import-ban-basis`, citing a real
official source -- never fabricate a jurisdiction's or a statute's
requirements to make coverage look bigger.

## Maturity

`:implemented` -- `TextileTradeAdvisor` + `Textile Trading Governor` run
as real, tested code (`clojure -M:dev:test`: 44 tests / 202 assertions,
0 failures; lint clean), following the SAME governed-actor architecture
as the other prior actors across this fleet, with its own distinct,
independently-named governor and its own direct-entity-boolean
textile-trading checks. See `docs/adr/0001-architecture.md` for the
history and design.

## Robotics Premise

`blueprint.edn` sets `:itonami.blueprint/robotics true`. This is a
reasoned call, not a default carried over from a sibling: real modern
textile/apparel/footwear wholesale distribution centers already run
substantial physical automation, in two distinct forms depending on the
goods kind:

- **Hanging apparel** (garments that must stay on a hanger through
  distribution to avoid wrinkling/re-pressing cost) is handled at major
  apparel distribution centers with automated garment-on-hanger (GOH)
  sortation conveyor systems -- well-established, widely-deployed
  automation at apparel 3PLs and brand distribution centers (vendors
  such as Vanderlande, Dematic and SSI Schaefer all sell GOH sortation
  lines for exactly this purpose).
- **Footwear and folded/boxed/palletized textiles** are handled at
  footwear and general apparel distribution centers with automated
  pick-to-light and AS/RS (automated storage-and-retrieval system)
  technology -- directly analogous to the metal-wholesale sibling's
  overhead-crane pick, the agri-wholesale sibling's elevator-loadout
  robot, and the provision-trading sibling's AS/RS-class warehouse
  pallet-picking robot.

An autonomous GOH sortation conveyor robot performs the physical
hanging-apparel pick, and an autonomous pick-to-light/AS-RS robot
performs the physical footwear/folded-textile pick, at the wholesale
warehouse -- the point at which this actor's `:delivery/dispatch` occurs
-- under the actor, gated by the independent Textile Trading Governor.

Either way, the governor never dispatches hardware itself: a dispatch-
clearing action must have cleared the same sign-off a human trading
supervisor would need. A robot may sort a hanging garment or stage a
footwear carton, but only after the governor (every HARD check clean)
and a human supervisor both agree it is safe to -- the same operating-
state-machine-gated-by-governor premise every cloud-itonami vertical
restates (ADR-2607011000): the blueprint declares `:robotics true`, the
README names the robots that perform the physical act, and the Textile
Trading Governor is the independent gate those robots' commands must
pass.
