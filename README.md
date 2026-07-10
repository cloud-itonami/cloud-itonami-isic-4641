# cloud-itonami-isic-4641

Open Business Blueprint for **ISIC Rev.5 4641**: Wholesale of Textiles,
Clothing and Footwear -- textile-order intake, per-jurisdiction
counterparty-diligence / sanctions regulatory verification, forced-labor
supply-chain rebuttable-presumption verification, textile/apparel/
footwear goods dispatch, and invoice settlement for a wholesale textile,
apparel and footwear trader.

This repository publishes a textile/apparel/footwear-wholesale actor --
textile-order intake, per-jurisdiction contract / sanctions regulatory
verification, forced-labor supply-chain verification, goods dispatch and
invoice settlement -- as an OSS business that any qualified operator can
fork, deploy, run, improve and sell, so a regional textile wholesaler
never surrenders counterparty, credit, sanctions and supply-chain data to
a closed trading / ERP SaaS.

Built on this workspace's
[`langgraph`](https://github.com/kotoba-lang/langgraph)
StateGraph runtime (portable `.cljc`, supervised superstep loop,
interrupts, Datomic/in-mem checkpoints) -- the same actor pattern as
every prior actor in this fleet -- here it is **TextileTradeAdvisor ⊣
Textile Trading Governor**. This blueprint's own
`:itonami.blueprint/governor` keyword, `:textile-trading-governor`, is a
UNIQUE keyword fleet-wide (grep-verified: no other blueprint declares
it) -- a fresh, independent build.

**Like the fuel-wholesale, general-trading, commission-brokerage,
agri-wholesale, provision-trading and metal-wholesale siblings, this
vertical is SELF-CONTAINED**: there is no `kotoba-lang/textiletrade` to
delegate textile-trading validation to, so the credit-clearance /
contract-on-file / forced-labor-presumption / sanctions-screening checks
live as direct entity boolean reads in `textiletrade.governor` (off
dedicated `:credit-cleared?` / `:contract-terms` / `:supply-chain-
traceability-documented?` / `:forced-labor-rebuttal-evidence-on-file?` /
`:sanctions-screened?` facts on the `textile-order` record), rather than
wrapping an external capability library's own validated function.

> **Why an actor layer at all?** An LLM is great at drafting an order
> summary, normalizing records, and reading a credit file -- but it has
> **no notion of which jurisdiction's customs / sanctions law is
> official, no license to dispatch real textile/apparel/footwear goods
> to a counterparty or settle a real invoice, and no way to know on its
> own whether the counterparty's credit has actually been cleared,
> whether contract terms are actually on file, whether a shipment's
> origin region or manufacturing entity is legally FLAGGED under the
> Uyghur Forced Labor Prevention Act's rebuttable presumption -- and, if
> so, whether the clear-and-convincing rebuttal evidence a REBUTTABLE
> presumption demands actually exists on file -- or whether OFAC /
> equivalent sanctions screening has actually been passed**. Letting it
> dispatch goods or settle an invoice directly invites fabricated
> regulatory citations, goods leaving the warehouse to an uncreditworthy
> or unscreened counterparty, a shipment dispatched against an
> unrebutted forced-labor presumption, and an invoice settling against a
> sanctioned party -- exposing the operator to real enforcement (CBP
> detention/exclusion, civil/criminal sanctions liability) and financial
> liability, for whoever runs it. This project seals the
> TextileTradeAdvisor into a single node and wraps it with an
> independent **Textile Trading Governor**, a human **approval
> workflow**, and an immutable **audit ledger**.

## Scope: what this actor does and does not do

This actor covers textile-order intake through contract / sanctions
regulatory verification, forced-labor supply-chain rebuttable-
presumption verification, textile/apparel/footwear goods dispatch and
invoice settlement. It does **not**, by itself, hold any textile-
wholesale licence, import authority or operating authority required to
run a textile-wholesale business in a given jurisdiction, and it does
not claim to. It also does not perform the actual physical warehouse
pick/pack/palletize or route optimization itself, or judge trading-book
economics -- inventory/route optimization (the blueprint's own
`:optimization` technology) is a follow-up slice, not in this R0.
Whoever deploys and operates a live instance (a qualified trading
supervisor / warehouse operator) supplies any jurisdiction-specific
operating authority, the real warehouse-automation dispatch integration
and the real ERP / accounts-receivable integrations, and bears that
jurisdiction's liability -- the software supplies the governed,
spec-cited, audited execution scaffold so that operator does not have to
build the compliance layer from scratch.

### Actuation

**Dispatching real textile/apparel/footwear goods to a counterparty from
the wholesale warehouse and settling a real invoice are never
autonomous, at any phase, by construction.** Two independent layers
enforce this (`textiletrade.governor`'s `:delivery/dispatch`/
`:invoice/settle` high-stakes gate and `textiletrade.phase`'s phase
table, which never puts either op in any phase's `:auto` set) -- see
`textiletrade.phase`'s docstring and
`test/textiletrade/phase_test.clj`'s
`delivery-dispatch-never-auto-at-any-phase`/
`invoice-settle-never-auto-at-any-phase`. The actor may draft, check and
recommend; a human trading supervisor is always the one who actually
dispatches a goods shipment or settles an invoice. Grounded in
textile-trading doctrine (the same discipline every regulator in
`textiletrade.facts` codifies: a real dispatch and a real invoice
settlement are human sign-off acts) -- a genuine DUAL-actuation shape,
applied SEQUENTIALLY to the SAME textile-order (dispatch first, invoice
settlement later).

### The rebuttable presumption, faithfully modeled

The Uyghur Forced Labor Prevention Act (UFLPA, Pub. L. 117-78, 2021)
does not gate this actor's forced-labor check with a simple
present/absent certificate the way a phytosanitary or food-safety
certificate gates the agri-wholesale/provision-trading siblings' checks.
It creates a **REBUTTABLE PRESUMPTION**: goods mined, produced, or
manufactured wholly or in part in the Xinjiang Uyghur Autonomous Region
(or made by an entity on the UFLPA Entity List) are PRESUMED to be made
with forced labor and barred from U.S. importation, UNLESS the importer
provides CLEAR AND CONVINCING EVIDENCE otherwise. `textiletrade.governor`
models this faithfully: `forced-labor-presumption-unrebutted-violations`
does not fire at all for a non-flagged origin/entity, and for a FLAGGED
origin/entity it does not fire once BOTH a documented supply-chain trace
AND a clear-and-convincing rebuttal evidence dossier are on file --
proven directly in `test/textiletrade/governor_contract_test.clj`'s
`forced-labor-presumption-is-genuinely-rebuttable-not-a-blanket-ban` and
the bundled demo (`to-6` HOLDS with no rebuttal evidence; `to-7`, the
SAME Xinjiang origin, dispatches cleanly once both rebuttal facts are
documented).

## The core contract

```
textile-order intake + jurisdiction facts (textiletrade.facts, spec-cited)
        |
        v
   ┌───────────────────────┐   proposal      ┌───────────────────────┐
   │ TextileTradeAdvisor   │ ─────────────▶ │ Textile Trading        │  (independent system)
   │ (sealed)              │  + citations    │ Governor -- spec-basis │
   └───────────────────────┘                 │ · evidence-incomplete  │
          │                 commit ◀┼ · credit-uncleared ·  │
          │                         │ contract-missing ·     │
    record + ledger        escalate ┼ forced-labor-presumption-│
          │              (ALWAYS for│ unrebutted ·            │
          │       :delivery/        │ counterparty-sanctions- │
          │       dispatch/         │ flag-unresolved ·       │
          │       :invoice/         │ already-dispatched ·    │
          │       settle)           │ already-invoiced        │
          │                         └───────────────────────┘
          ▼
      human approval
```

**The TextileTradeAdvisor never dispatches goods to a counterparty or
settles an invoice the Textile Trading Governor would reject, and never
does so without a human sign-off.** Hard violations (fabricated
regulatory requirements; unsupported evidence; an uncleared counterparty
credit; no contract-terms on file; an unrebutted forced-labor
presumption; an unresolved sanctions-screening flag; a double dispatch/
invoice) force **hold** and *cannot* be approved past; a clean dispatch/
invoice proposal still always routes to a human.

## Run

```bash
clojure -M:dev:run     # walk one clean dispatch + invoice lifecycle, six HARD-hold cases, and two forced-labor control scenarios, through the actor
clojure -M:dev:test    # governor contract · phase invariants · store parity · registry conformance · facts coverage
clojure -M:lint        # clj-kondo (errors fail; CI mirrors this)
```

## Robotics premise

All cloud-itonami verticals are designed on the premise that a **robot
performs the physical domain work**. Here an autonomous garment-on-
hanger (GOH) sortation conveyor picks hanging apparel, and an autonomous
pick-to-light/AS-RS (automated storage-and-retrieval) robot picks boxed
footwear and folded/palletized textiles, at the wholesale warehouse,
under the actor, gated by the independent **Textile Trading Governor**.
The governor never dispatches hardware itself: a dispatch-clearing
action must have cleared the same sign-off a human trading supervisor
would need. This restates the fleet-wide robotics premise three ways
(ADR-2607011000): the blueprint declares `:robotics true`, the README
names the robots that perform the physical act, and the Textile Trading
Governor is the independent gate those robots' commands must pass -- a
robot may pick a garment or stage a footwear carton, but only after the
governor and a human supervisor both agree it is safe to.

## Open business

This repository is not only source code. It is a public, forkable
business model:

| Layer | What is open |
|---|---|
| OSS core | Actor runtime, Textile Trading Governor, dispatch/invoice draft records, audit ledger |
| Business blueprint | Customer, offer, pricing, unit economics, sales motion |
| Operator playbook | How to fork, license, deploy and support the service in a jurisdiction |
| Trust controls | Governance, security reporting, actuation invariant, audit requirements |

See [`docs/business-model.md`](docs/business-model.md) and
[`docs/operator-guide.md`](docs/operator-guide.md) to start this as an
open business on itonami.cloud, and
[`docs/adr/0001-architecture.md`](docs/adr/0001-architecture.md) for the
full architecture and decision record.

## Capability layer

This blueprint resolves its technology stack via
[`kotoba-lang/industry`](https://github.com/kotoba-lang/industry) (ISIC
`4641`). Like the fuel-wholesale, general-trading, commission-brokerage,
agri-wholesale, provision-trading and metal-wholesale siblings, this
vertical is NOT backed by a separate bespoke domain capability lib: the
textile-trading checks (credit-clearance, contract-on-file, forced-
labor-presumption rebuttal, sanctions-screening) are direct entity
boolean reads in `textiletrade.governor`, on top of the generic
robotics/identity/forms/dmn/bpmn/audit-ledger stack.

## Layout

| File | Role |
|---|---|
| `src/textiletrade/store.cljc` | **Store** protocol -- `MemStore` ‖ `DatomicStore` (`langchain.db`) + append-only audit ledger + dispatch AND invoice history (dual history). The double-actuation guard checks dedicated `:dispatched?`/`:invoiced?` booleans rather than a `:status` value |
| `src/textiletrade/registry.cljc` | Dispatch/invoice draft records (record construction only -- the Textile Trading Governor's checks are direct entity booleans, so there are no pure range-check functions to host here) |
| `src/textiletrade/facts.cljc` | Per-jurisdiction customs/sanctions catalog with an official spec-basis citation per entry, PLUS a separate forced-labor import-ban-basis catalog (UFLPA/CAATSA), honest coverage reporting |
| `src/textiletrade/textiletradeadvisor.cljc` | **TextileTradeAdvisor** -- `mock-advisor` ‖ `llm-advisor`; intake/supply-chain-verification/dispatch/invoice proposals |
| `src/textiletrade/governor.cljc` | **Textile Trading Governor** -- 6 HARD checks (spec-basis · evidence-incomplete · credit-uncleared · contract-missing · forced-labor-presumption-unrebutted · counterparty-sanctions-flag-unresolved) + 2 double-actuation guards + 1 soft (confidence/actuation gate) |
| `src/textiletrade/phase.cljc` | **Phase 0→3** -- read-only → assisted intake → assisted verify → supervised (dispatch/invoice always human; order intake is the ONLY auto-eligible op, no direct capital risk) |
| `src/textiletrade/operation.cljc` | **OperationActor** -- langgraph StateGraph |
| `src/textiletrade/sim.cljc` | demo driver |
| `test/textiletrade/*_test.clj` | governor contract · phase invariants · store parity · registry conformance · facts coverage |

## Business-process coverage (honest)

This actor covers textile-order intake through contract / sanctions
regulatory verification, forced-labor supply-chain verification,
textile/apparel/footwear goods dispatch and invoice settlement -- the
core governed lifecycle:

| Covered | Not covered (out of scope for this R0) |
|---|---|
| Textile-order intake + per-jurisdiction evidence checklisting, HARD-gated on an official spec-basis citation (`:order/intake`/`:supply-chain/verify`) | Real warehouse-automation/ERP integration, route optimization and trading-book economics |
| Goods dispatch, HARD-gated on full evidence, a credit-cleared counterparty, contract-terms on file, an unrebutted forced-labor presumption (where triggered), a passed sanctions screen and no double-dispatch (`:delivery/dispatch`) | |
| Invoice settlement, HARD-gated on full evidence, a passed sanctions screen and no double-invoice (`:invoice/settle`) | |
| Immutable audit ledger for every intake/verification/dispatch/invoice decision | |

Extending coverage is additive: add the next gate (e.g. a country-of-
origin textile-labeling reconciliation check) as its own governed op
with its own HARD checks and tests, following the SAME "an independent
governor re-verifies against the actor's own records before any
real-world act" pattern this repo's flagship ops already establish.

## Jurisdiction coverage (honest)

`textiletrade.facts/coverage` reports how many requested jurisdictions
actually have an official spec-basis in `textiletrade.facts/catalog` --
currently 4 seeded (JPN, USA, GBR, DEU) out of ~194 jurisdictions
worldwide. `textiletrade.facts/forced-labor-import-ban-basis` separately
seeds a forced-labor rebuttable-presumption import-ban citation for only
2 of those 4 (USA -- currently BINDING; DEU/EU -- adopted, phased
application from approximately 2027, seeded `:binding? false` for
forward compatibility). This is a starting catalog to prove the
governor contract end-to-end, not a claim of global coverage. Adding a
jurisdiction, an origin-region, or a forced-labor statute for an
already-seeded jurisdiction is additive: one map/set entry, citing a
real official source -- never fabricate a jurisdiction's or a statute's
requirements to make coverage look bigger. See
[`docs/business-model.md`](docs/business-model.md) `Jurisdiction
coverage (honest)` for the full citation-by-citation confidence
breakdown.

## Maturity

`:implemented` -- `TextileTradeAdvisor` + `Textile Trading Governor` run
as real, tested code (see `Run` above), following the SAME governed-
actor architecture as the other prior actors across this fleet, with its
own distinct, independently-named governor and its own direct-entity-
boolean textile-trading checks, most closely modeled on the metal-
wholesale sibling's (`cloud-itonami-isic-4662`) commodity-provenance
check shape -- but with a genuinely different trigger (origin-region/
entity-list, not commodity-type) and a genuinely different gating axis
(jurisdiction-gated, not jurisdiction-unconditional) reasoned from the
real legal shape of UFLPA's border-enforcement mechanism. See
`docs/adr/0001-architecture.md` for the history and design.

## License

Code and implementation templates are AGPL-3.0-or-later.
