# ADR-0001: TextileTradeAdvisor ⊣ Textile Trading Governor architecture

## Status

Accepted. `cloud-itonami-isic-4641` published directly as `:implemented`
in the `kotoba-lang/industry` registry.

## Context

`cloud-itonami-isic-4641` publishes an OSS business blueprint for
wholesale of textiles, clothing and footwear (textile-order intake,
per-jurisdiction contract / sanctions regulatory verification, forced-
labor supply-chain rebuttable-presumption verification, goods dispatch,
and invoice settlement). Like every prior actor in this fleet, the
blueprint alone is not an implementation: this ADR records the governed-
actor architecture that establishes it as real, tested code, following
the same langgraph StateGraph + independent Governor + Phase 0->3
rollout pattern established by `cloud-itonami-isic-6511` (life
insurance) and applied across many prior siblings, most directly the
PRINCIPAL wholesale-trading siblings: `cloud-itonami-isic-4671` (fuel
wholesale, single-commodity excise/sanctions focus), `cloud-itonami-
isic-4690` (general/diversified wholesale trading, multi-commodity
export-control/sanctions focus), `cloud-itonami-isic-4620`
(agri-wholesale, RAW agricultural inputs and live animals, biosecurity
focus, the fleet's first kind-gated certificate split),
`cloud-itonami-isic-4630` (provision trading, PROCESSED food/beverage/
tobacco, the fleet's first three-way category-gated split), and --
MOST DIRECTLY -- `cloud-itonami-isic-4662` (metal wholesale, whose
domain-defining check is gated on a property of the COMMODITY/SHIPMENT
itself rather than the trade's jurisdiction, the closest architectural
precedent this build follows and deliberately departs from -- see
Decision 4). `cloud-itonami-isic-4610` (commission brokerage, AGENCY,
never takes title, dual-agency conflict-of-interest focus) is a related
but structurally different (agency, not principal) sibling.

ISIC 4641 is a PRINCIPAL trading model like 4671/4690/4620/4630/4662 --
the wholesaler takes title and resells. Like the metal-wholesale
sibling, its defining regulatory exposure is genuinely different from
the jurisdiction-of-the-trade regulatory citations (excise, food-safety,
biosecurity, export-control) that gate 4671/4690/4620/4630: it is
**forced-labor supply-chain due diligence**, most concretely the U.S.
Uyghur Forced Labor Prevention Act (UFLPA, Pub. L. 117-78, 2021), which
creates a REBUTTABLE PRESUMPTION that goods mined, produced, or
manufactured wholly or in part in China's Xinjiang Uyghur Autonomous
Region (or made by an entity on the UFLPA Entity List) are made with
forced labor and are barred from U.S. importation unless the importer
provides clear and convincing evidence otherwise. This is real, current,
actively-enforced U.S. law (enacted 2021, CBP-enforced), operationalizing
the underlying forced-labor import ban at 19 U.S.C. §1307. This is why
this vertical's domain-defining check is gated on the SHIPMENT's own
origin-region/manufacturing-entity, not jurisdiction OR a jurisdiction/
kind pairing -- SHAPE-analogous to the metal-wholesale sibling's
metal-type gating axis -- but see Decision 4 for why this build
deliberately departs from that sibling's jurisdiction-UNCONDITIONAL
design.

Like the five prior principal wholesale-trading siblings, this vertical
has NO bespoke domain capability library in `kotoba-lang` to wrap
(verified: no `kotoba-lang/textiletrade`-style repo exists, and
`kotoba-lang/robotics` is the generic cross-cutting robotics contract
every cloud-itonami vertical already uses, not a domain-specific library
for this vertical). This build therefore uses self-contained domain
logic. The textile-trading checks (credit-clearance, contract-on-file,
forced-labor-presumption rebuttal, sanctions-screening) are direct
entity boolean reads in `textiletrade.governor`, off dedicated
`:credit-cleared?` / `:contract-terms` / `:supply-chain-traceability-
documented?` / `:forced-labor-rebuttal-evidence-on-file?` / `:sanctions-
screened?` facts on the `textile-order` record -- NO pure range-check
functions are needed (contrast the crude-extraction sibling, whose
registry hosts its reservoir/annular/water-cut/H2S range checks).

This blueprint's own `:itonami.blueprint/governor` keyword,
`:textile-trading-governor`, is grep-verified UNIQUE among the actor
fleet repos checked out at build time -- no naming-collision precedent
question, a fresh independent build.

## Decision

### Decision 1: fresh governor identity, no reuse precedent needed

`:textile-trading-governor` is grep-verified unique across every
`blueprint.edn` checked out locally at build time (354 governor entries
surveyed, no `textile`/`apparel`/`footwear`/`garment`/`fashion` match
outside this build). This build follows the SAME governed-actor
architecture as every prior actor, but with its own distinct governor
identity.

### Decision 2: self-contained domain logic, direct entity booleans (no `kotoba-lang/textiletrade` to wrap, and no range-check functions to host)

Like the fuel-wholesale, general-trading, commission-brokerage,
agri-wholesale, provision-trading and metal-wholesale siblings (and
unlike the crude-extraction sibling, which hosts pure physical
range-check functions in its registry because its governor re-verifies
measured physical values), this textile-wholesale vertical needs no
range-check functions: there is no pre-existing textile-trading
capability library to delegate to, AND the governor's domain checks
(credit-clearance, contract-on-file, forced-labor-presumption rebuttal,
sanctions-screening) are direct entity boolean reads off the
`textile-order` record's own dedicated facts -- not measured-value-vs-
limit range comparisons. So `textiletrade.registry` is RECORD
CONSTRUCTION ONLY (no range-check functions), and `textiletrade.
governor` reads the order's booleans directly.

### Decision 3: dual-actuation shape, SEQUENTIAL on the SAME `textile-order` entity

Like the fuel-wholesale sibling's `fuel-order` entity, the agri-
wholesale sibling's `agri-order` entity, the provision-trading sibling's
`provision-order` entity and the metal-wholesale sibling's `metal-order`
entity, this vertical's `dispatch` and `settle` actuation events apply
SEQUENTIALLY to the SAME `textile-order` -- a textile/apparel/footwear
goods dispatch happens first (product leaves the wholesale warehouse),
invoice settlement happens later (the money side of the trade, custody
/ financial transfer), on the same order record. `high-stakes` is
`#{:delivery/dispatch :invoice/settle}`; neither ever auto-commits at
any phase.

### Decision 4: `forced-labor-presumption-unrebutted` -- gated on ORIGIN-REGION/ENTITY-LIST, JURISDICTION-GATED (a deliberate departure from the metal-wholesale sibling's unconditional-across-jurisdiction design); the defining design decision of this build

This is the decision that most distinguishes this vertical from every
prior wholesale-trading sibling, and it required both a genuinely
different TRIGGER (shipment origin/entity, not jurisdiction or metal
type) and a genuinely different reasoned GATING AXIS from the sibling it
otherwise most closely resembles in shape.

**Why this check is SHAPE-analogous to the metal-wholesale sibling's
conflict-minerals check.** Both checks gate on a property INTRINSIC to
the shipment/commodity rather than the trade's jurisdiction or a
jurisdiction/kind pairing (contrast the agri-wholesale sibling's
phytosanitary/animal-health split and the provision-trading sibling's
food-safety/alcohol/tobacco split, both of which remain implicitly
jurisdiction-shaped). Both fold TWO distinct real-world sub-requirements
into ONE named governor rule (chain-of-custody + smelter-certification
for metals; supply-chain-traceability + rebuttal-evidence-dossier for
textiles) because both are arms of the SAME real-world provenance
concern, following the SAME 'fold two sub-requirements into one named
rule, name the specific sub-fact in `:detail`' discipline the metal-
wholesale sibling's Decision 4 and the provision-trading sibling's
Decision 4 both establish.

**Why this check is jurisdiction-GATED, unlike the metal-wholesale
sibling's jurisdiction-UNCONDITIONAL check.** This is the load-bearing
departure, and it follows directly from how the two underlying legal
regimes actually work, not from an arbitrary choice to differentiate.
Dodd-Frank Section 1502 and EU Regulation 2017/821 (the metal-wholesale
sibling's conflict-minerals statutes) regulate a DOWNSTREAM COMPANY'S
disclosure/due-diligence obligation (a US SEC-reporting issuer; an EU
importer) that may sit ANYWHERE in the supply chain relative to where
the metal-wholesaler's own trade occurs -- which is why that sibling
correctly made its check unconditional across jurisdiction (Decision 4
of that ADR): real market participants apply that diligence as an
operational floor essentially everywhere they trade, because a
downstream counterparty's own exposure does not depend on where THIS
trader's sale happens.

UFLPA is a DIFFERENT kind of mechanism. It empowers U.S. Customs and
Border Protection to DETAIN AND EXCLUDE merchandise AT THE U.S. BORDER
under 19 U.S.C. §1307 -- the presumption specifically bars IMPORTATION
INTO THE UNITED STATES. This is the exact customs-entry act this
actor's OWN `:delivery/dispatch` represents when the order's own
`:jurisdiction` is the United States. When the order's own jurisdiction
is something else (say, JPN), UFLPA's border-detention mechanism does
not attach to THIS actor's own dispatch act -- a downstream party who
later resells into the US would separately face their OWN UFLPA
exposure on THEIR OWN importation, outside the scope of what this
actor's own dispatch record represents.

Three design options were considered:

- **Option A (rejected): fold forced-labor rebuttal evidence into the
  generic `evidence-incomplete-violations` checklist**, adding two
  conditional items (`supply-chain traceability documentation`,
  `forced-labor rebuttal evidence`) to `textiletrade.facts/catalog`'s
  per-jurisdiction `:required-evidence`. Rejected for the SAME reason
  the metal-wholesale sibling's ADR rejected the analogous option: this
  would force forced-labor diligence to inherit jurisdiction-gating it
  does not actually have at the SHIPMENT level (a Xinjiang-origin order
  destined for GBR would need to add conditional logic to exempt itself
  from a GENERIC checklist item that does not actually apply there), and
  would force every OTHER jurisdiction's evidence checklist -- covering
  shipments that are never forced-labor-flagged -- to carry conditional
  items irrelevant to most orders.
- **Option B (rejected): a jurisdiction-UNCONDITIONAL check, copying the
  metal-wholesale sibling's Decision 4 shape exactly** -- firing whenever
  the origin/entity is flagged, REGARDLESS of the order's own
  jurisdiction. Rejected: this would misrepresent UFLPA's border-
  detention mechanism as a global operational floor the way conflict-
  minerals diligence genuinely is. Real UFLPA enforcement is
  fundamentally a U.S. CBP border-entry action; a Xinjiang-origin
  textile-order whose OWN dispatch is a JPN customs entry is not itself
  the act UFLPA polices, even though a LATER resale into the US by a
  downstream party would be. Applying the metal-wholesale sibling's
  gating axis here without re-examining whether the underlying legal
  mechanism actually supports it would have been mechanical copying, not
  faithful modeling -- the task this build set out to do was model the
  REAL mechanic, not replicate the closest precedent's shape uncritically.
- **Option C (chosen): a jurisdiction-GATED check, evaluated only when
  the order's own `:jurisdiction` currently binds a forced-labor
  import-ban statute (`textiletrade.facts/forced-labor-import-ban-
  binding?`, today true only for `"USA"`) AND the order's origin-region
  or manufacturing-entity is flagged
  (`textiletrade.facts/forced-labor-presumption-triggered?`)** --
  `forced-labor-presumption-unrebutted-violations` fires only under
  BOTH conditions, and even then only refuses dispatch UNLESS BOTH
  `:supply-chain-traceability-documented?` AND `:forced-labor-rebuttal-
  evidence-on-file?` are true. The bundled demo's `to-8` proves the
  jurisdiction-gating directly: a Xinjiang-origin order with BOTH
  rebuttal facts false, but `:jurisdiction "JPN"`, dispatches cleanly --
  the SAME facts that HOLD `to-6` (identical origin/entity, `:jurisdiction
  "USA"`) do not hold here, because JPN carries no currently-binding
  forced-labor import-ban statute in this actor's scope.

**The REBUTTABLE-PRESUMPTION mechanic itself, faithfully modeled.**
Independent of the jurisdiction-gating decision above, this check is
also structurally different from a simple present/absent-certificate
check (contrast the agri-wholesale sibling's phytosanitary-certificate
check or the provision-trading sibling's food-safety-certificate check):
it does not fire AT ALL for a non-flagged origin/entity (there is no
presumption to rebut), and for a FLAGGED origin/entity it does NOT fire
once BOTH rebuttal facts are documented -- the presumption is genuinely
REBUTTABLE, not a blanket regional ban. The bundled demo's `to-6`/`to-7`
pair proves this directly: `to-6` (Xinjiang origin, USA jurisdiction,
NEITHER rebuttal fact on file) HOLDS on `:forced-labor-presumption-
unrebutted`; `to-7` (the IDENTICAL Xinjiang origin and USA jurisdiction,
BOTH rebuttal facts documented) dispatches CLEANLY, still always
escalating for the ordinary human sign-off -- proving the check
genuinely implements a rebuttable presumption, not a per-origin ban.
`test/textiletrade/governor_contract_test.clj`'s
`forced-labor-presumption-is-genuinely-rebuttable-not-a-blanket-ban` and
`forced-labor-check-is-jurisdiction-gated-not-a-blanket-regional-ban`
encode both proofs as executable tests.

**Why CAATSA (North Korea) is included alongside UFLPA (Xinjiang).**
`textiletrade.facts/flagged-origins` seeds TWO real, independently-
enacted origin triggers converging on the SAME underlying 19 U.S.C.
§1307 forced-labor import-ban mechanism: the Xinjiang Uyghur Autonomous
Region under UFLPA (high confidence), and North-Korean-labor-linked
production under the Countering America's Adversaries Through Sanctions
Act (CAATSA, Pub. L. 115-44, 2017) Section 321(b) (reasonably, but not
fully, confident about the precise section citation -- the underlying
rebuttable-presumption mechanism itself is a real, well-documented
feature of U.S. forced-labor import-ban enforcement). Both statutes are
cited in `textiletrade.facts/forced-labor-import-ban-basis`'s USA entry;
neither is fabricated to inflate coverage -- see `docs/business-
model.md` `Jurisdiction coverage (honest)` for the full confidence
breakdown, following the SAME 'honest confidence disclosure, flag
uncertainty explicitly' discipline every sibling's `facts` namespace
establishes.

**Why the EU's Regulation (EU) 2024/3015 is seeded but NOT currently
binding.** Unlike the metal-wholesale sibling's OECD Guidance (a
genuine non-statutory universal baseline that sibling's conflict-
minerals check treats as an operational floor everywhere), I am not
confident there is a comparable universal non-statutory forced-labor
baseline this actor should apply globally by default -- so no such
fallback is fabricated here. Regulation (EU) 2024/3015 IS a real,
adopted EU forced-labour-products market-prohibition regulation, but I
am only moderately confident about its precise phased-application
timeline (my understanding: full application begins roughly three years
after entry into force, approximately 2027). It is therefore seeded in
`forced-labor-import-ban-basis` at `:binding? false` -- present for
forward-compatible citation purposes (so a human reviewer sees it when
verifying a DEU-jurisdiction order), but NOT currently gating the
governor's HARD check. Flipping `:binding?` to `true` once the
regulation's phased application actually takes effect is a one-line
change to `textiletrade.facts`, not a re-architecture of the governor.

### Decision 5: `counterparty-sanctions-flag-unresolved?` -- the open-flag-unresolved discipline (reapplied, not new)

An unresolved sanctions-screening flag -- the counterparty has not
passed OFAC / equivalent sanctions screening -- is a HARD,
un-overridable hold. This reuses the SAME open-flag-unresolved
discipline the freight sibling's `delivery-exception-unresolved?` check
(and the fuel-wholesale/general-trading/commission-brokerage/agri-
wholesale/provision-trading/metal-wholesale siblings' own sanctions
checks) establish -- an open concern cannot be silently suppressed to
force a dispatch or invoice through. Evaluated UNCONDITIONALLY at both
`:delivery/dispatch` and `:invoice/settle`, and UNCONDITIONALLY
regardless of origin/entity flag status or jurisdiction (unlike the
forced-labor-presumption check in Decision 4, sanctions screening
applies uniformly to every order -- there is no regulatory reason to
differentiate it by origin, entity, or jurisdiction; only the
forced-labor-presumption-rebuttal requirement itself is shipment- and
jurisdiction-specific).

### Decision 6: dedicated double-actuation-guard booleans

`:dispatched?` / `:invoiced?` are dedicated booleans on the
`textile-order` record, never a single `:status` value -- the same
discipline every prior governor's guards establish, informed by
`cloud-itonami-isic-6492`'s real status-lifecycle bug
(ADR-2607071320).

### Decision 7: Store protocol, MemStore + DatomicStore parity

`textiletrade.store/Store` is implemented by both `MemStore` (atom-
backed, default for dev/tests/demo) and `DatomicStore` (`langchain.db`-
backed), proven to satisfy the same contract in
`test/textiletrade/store_contract_test.clj`. The ledger stays
append-only on every backend: which textile-order was verified for a
jurisdiction with no official spec-basis, which counterparty had
credit-uncleared / no contract / an unrebutted forced-labor presumption
/ an unresolved sanctions-screening flag, which order was dispatched,
which invoice was settled, on what jurisdictional and supply-chain
basis, approved by whom -- always a query over an immutable log.

### Decision 8: Phase 0->3 with `:delivery/dispatch`/`:invoice/settle` NEVER auto

`textiletrade.phase`'s phase table puts `:order/intake` (no direct
capital risk) in phase 3's `:auto` set as its only member;
`:delivery/dispatch` and `:invoice/settle` are deliberately ABSENT from
every phase's `:auto` set, including phase 3 -- a permanent structural
fact. `textiletrade.governor`'s high-stakes gate enforces the same
invariant independently: two layers agree that actuation is always a
human trading supervisor's call.

### Decision 9: mock + LLM advisor pair

`textiletrade.textiletradeadvisor` provides a deterministic
`mock-advisor` (default, runs offline) and an `llm-advisor` backed by a
`langchain.model/ChatModel`. The LLM advisor's EDN proposal is parsed
defensively: any parse/shape failure yields a safe low-confidence noop
so the governor escalates/holds -- an LLM hiccup can never auto-dispatch
goods or auto-settle an invoice. The mock advisor's `verify-supply-
chain` proposal drafts the forced-labor rebuttable-presumption citation
informationally (via `textiletrade.facts/forced-labor-presumption-
citation`) for a human reviewer's benefit whenever the order's
jurisdiction currently binds one, but this citation is NEVER what the
governor checks at `:delivery/dispatch` -- the governor independently
re-reads the order's own `:supply-chain-traceability-documented?`/
`:forced-labor-rebuttal-evidence-on-file?` ground truth directly
(Decision 4), so a compromised or mistaken advisor citation can never
substitute for the real facts.

### Decision 10: `:robotics true`, reasoned separately for hanging-apparel and footwear/folded-goods handling

`:itonami.blueprint/robotics` is `true`, a deliberate call reasoned
specifically for this vertical rather than copied from a sibling
default -- following the SAME kind-differentiated reasoning discipline
the agri-wholesale sibling's README Robotics Premise, the provision-
trading sibling's Decision 10, and the metal-wholesale sibling's
Decision 10 all establish. This vertical spans TWO physically distinct
goods forms with two distinct, well-precedented automation claims:

- **Hanging apparel** is handled at major apparel distribution centers
  with automated garment-on-hanger (GOH) sortation conveyor systems --
  well-established, widely-deployed warehouse automation (vendors such
  as Vanderlande, Dematic and SSI Schaefer sell GOH sortation lines
  specifically for apparel distribution).
- **Footwear and folded/boxed/palletized textiles** are handled with
  automated pick-to-light and AS/RS (automated storage-and-retrieval
  system) technology, directly analogous to the metal-wholesale
  sibling's overhead-crane pick, the agri-wholesale sibling's elevator-
  loadout robot, and the provision-trading sibling's AS/RS-class
  warehouse pallet-picking robot.

Both automation claims terminate at the wholesaler's own warehouse
dispatch point, matching every sibling's own scope disclaimer ("hand off
to a carrier" for the long-haul leg beyond the actor's own physical
dispatch act). This is a materially different physical claim from a
pure intermediation/brokerage vertical (the general-trading and
commission-brokerage siblings both correctly set `:robotics false`,
having no analogous physical dispatch act at all) -- so `:robotics true`
here was reasoned on this vertical's own terms (GOH sortation and
pick-to-light/AS-RS automation are both real and load-bearing for THIS
actor's `:delivery/dispatch`), not defaulted from either extreme
precedent.

## Alternatives considered

- **Wrapping a bespoke `kotoba-lang/textiletrade` capability library.**
  Considered and explicitly ruled out: no such library exists, and
  `kotoba-lang/robotics` is generic, not textile-trading-specific.
  Forcing a false capability-library integration would be dishonest;
  this build correctly uses self-contained domain logic instead.
- **Hosting pure range-check functions in the registry (as the crude
  sibling does).** Considered and ruled out: the textile-trading domain
  checks are direct entity booleans (credit cleared? contract on file?
  forced-labor presumption rebutted? sanctions screened?), not
  measured-value-vs-limit range comparisons, so there are no range
  checks to host. `textiletrade.registry` is record construction only.
- **Folding forced-labor rebuttal evidence into the generic
  jurisdiction evidence checklist, or gating the forced-labor check
  jurisdiction-UNCONDITIONALLY (copying the metal-wholesale sibling's
  shape exactly).** Considered and rejected -- see Decision 4 Options A
  and B above for the full reasoning: Option A would misrepresent
  forced-labor risk as a property of the trade's generic jurisdiction
  paperwork rather than the shipment's own provenance; Option B would
  misrepresent UFLPA's U.S.-border-detention mechanism as a
  jurisdiction-unconditional global operational floor, when its
  detention mechanism is inherently tied to the act of U.S. importation.
- **Treating CAATSA's North Korea presumption as legally identical to
  UFLPA's Xinjiang presumption, or omitting CAATSA's precise section
  citation entirely.** Considered and rejected as dishonest either way
  -- both are real, but I am not equally confident in every citation
  detail; the differing confidence levels are disclosed explicitly in
  `docs/business-model.md` rather than smoothed over.
- **A `:kind`-distinguished entity for dispatch vs. invoice** (matching
  the retail sibling's `order` shape). Rejected: dispatch and invoice
  settlement happen SEQUENTIALLY on the SAME textile-order in this
  domain, not as alternative actions -- the fuel-wholesale, agri-
  wholesale, provision-trading and metal-wholesale siblings' sequential
  shape is the honest match here.
- **Defaulting `:robotics` to `false`** (matching the general-trading and
  commission-brokerage siblings, which are non-physical intermediation/
  brokerage verticals with no analogous physical dispatch act).
  Considered and rejected: this vertical's `:delivery/dispatch` is a
  genuine physical act (textile/apparel/footwear goods actually leaving
  a wholesale warehouse via automated GOH sortation or pick-to-light/
  AS-RS pick), closer in kind to the fuel-wholesale, agri-wholesale,
  provision-trading and metal-wholesale siblings' physical dispatch acts
  -- see Decision 10.
- **Building warehouse-slotting/route optimization and trading-book
  optimization in this R0.** Rejected in favor of a scoped R0 slice (the
  `:optimization` capability is correctly marked required, the
  integration is a follow-up), consistent with this fleet's 'extending
  coverage is additive' convention.

## Consequences

- Fresh independent actor in this fleet, following the SAME governed-
  actor architecture as every prior sibling.
- Establishes the textile-trading checks as direct entity boolean reads
  (no pure range-check functions needed), an honest structural
  differentiator from the crude-extraction sibling's registry-hosted
  physical range checks.
- Establishes the fleet's first ORIGIN-REGION/ENTITY-LIST-gated,
  JURISDICTION-GATED domain-defining check -- SHAPE-analogous to the
  metal-wholesale sibling's metal-type-gated, jurisdiction-UNCONDITIONAL
  conflict-minerals check, but a deliberate, reasoned departure on the
  gating axis because UFLPA's underlying legal mechanism (a U.S. border-
  detention action) is genuinely different in kind from Dodd-Frank 1502/
  EU 2017/821's downstream-disclosure mechanism. A template for any
  future vertical whose defining regulatory concern attaches to the
  shipment itself AND is inherently tied to a specific importing
  jurisdiction's own border-enforcement action.
- Faithfully models a REBUTTABLE PRESUMPTION (not a simple missing-
  certificate check, and not a blanket regional ban): proven directly by
  the bundled demo's `to-6`/`to-7` pair and
  `test/textiletrade/governor_contract_test.clj`'s
  `forced-labor-presumption-is-genuinely-rebuttable-not-a-blanket-ban`.
- `MemStore` || `DatomicStore` parity is proven by
  `test/textiletrade/store_contract_test.clj`.
- 44 tests / 202 assertions pass; lint is clean; the demo
  (`clojure -M:dev:run`) walks one clean dispatch + invoice lifecycle,
  five HARD-hold scenarios (no spec-basis, credit-uncleared, contract-
  missing, forced-labor-presumption-unrebutted, sanctions, double
  dispatch, double invoice), PLUS two control scenarios (the SAME
  Xinjiang-origin order dispatching cleanly once rebuttal evidence is
  documented; the SAME unrebutted Xinjiang-origin order dispatching
  cleanly under a non-binding jurisdiction), proving BOTH that the
  presumption is genuinely rebuttable AND that the check is genuinely
  jurisdiction-gated, end-to-end.
- `blueprint.edn`'s `:robotics true` is a reasoned, vertical-specific
  call covering TWO distinct goods forms (hanging apparel, footwear/
  folded textiles), documented in README and `docs/business-model.md`,
  not a default carried over from either extreme sibling precedent.

## References

- `cloud-itonami-isic-6511/docs/adr/0001-architecture.md` (origin of the
  general governed-actor architecture pattern)
- `cloud-itonami-isic-4671/docs/adr/0001-architecture.md` (fuel-
  wholesale sibling; origin of the sequential dual-actuation shape and
  the self-contained-domain-logic pattern this build follows)
- `cloud-itonami-isic-4690/docs/adr/0001-architecture.md` (general-
  trading sibling)
- `cloud-itonami-isic-4610/docs/adr/0001-architecture.md` (commission-
  brokerage sibling; origin of the 'a genuinely new regulatory concern
  gets its own named check' precedent)
- `cloud-itonami-isic-4620/docs/adr/0001-architecture.md` (agri-
  wholesale sibling; origin of the fleet's first kind-gated certificate
  split -- contrast: still implicitly jurisdiction-shaped, unlike this
  build's shipment-provenance gating)
- `cloud-itonami-isic-4630/docs/adr/0001-architecture.md` (provision-
  trading sibling; origin of the fleet's first many-to-one category-
  gated split and the 'fold two sub-requirements into one named rule'
  precedent this build's Decision 4 follows for the forced-labor check)
- `cloud-itonami-isic-4662/docs/adr/0001-architecture.md` (metal-
  wholesale sibling; the CLOSEST architectural precedent -- origin of
  the fleet's first commodity/shipment-property-gated,
  jurisdiction-UNCONDITIONAL domain-defining check; this build's
  Decision 4 explains in detail why its own check is SHAPE-analogous but
  deliberately JURISDICTION-GATED, a considered departure grounded in
  UFLPA's genuinely different (border-detention, not downstream-
  disclosure) legal mechanism)
- `cloud-itonami-isic-0610/docs/adr/0001-architecture.md` (crude-
  extraction sibling; contrast: hosts pure physical range-check
  functions in its registry, which this vertical does NOT need)
- 関税法 (Customs Act); 輸出貿易管理令 (Export Trade Control Order)
  (Japan, MOF Customs / METI)
- Tariff Act of 1930 (19 U.S.C. Chapter 4); OFAC sanctions programs;
  Textile Fiber Products Identification Act (15 U.S.C. §70 et seq.,
  FTC); Flammable Fabrics Act (15 U.S.C. §1191 et seq., CPSC) (US, CBP /
  Treasury / FTC / CPSC)
- Taxation (Cross-border Trade) Act 2018; Sanctions and Anti-Money
  Laundering Act 2018 (SAMLA 2018) (UK, HMRC / OFSI)
- Union Customs Code (Regulation (EU) No 952/2013); EU financial
  sanctions regulations (EU; Germany, Zoll / BMF)
- Uyghur Forced Labor Prevention Act (UFLPA, Pub. L. 117-78, 2021);
  19 U.S.C. §1307 (forced-labor import ban) (US, CBP / DHS FLETF)
- Countering America's Adversaries Through Sanctions Act (CAATSA, Pub.
  L. 115-44, 2017) Section 321(b) (US; North-Korean-labor rebuttable
  presumption under 19 U.S.C. §1307 -- section-number confidence:
  moderate, see `docs/business-model.md`)
- Regulation (EU) 2024/3015 prohibiting products made with forced labour
  on the Union market (EU; phased application timeline confidence:
  moderate, see `docs/business-model.md`)
