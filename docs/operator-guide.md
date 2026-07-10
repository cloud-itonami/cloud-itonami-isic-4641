# Operator Guide

## First Deployment
1. Register traders, warehouses, textile-orders, and trading
   supervisors.
2. Import textile-order, counterparty, credit, sanctions and
   supply-chain history.
3. Seed the per-jurisdiction spec-basis catalog (`textiletrade.facts`)
   for the jurisdictions you actually trade in, citing real official
   sources only. Separately seed/confirm the
   `forced-labor-import-ban-basis` and `flagged-origins` entries for the
   origin regions and jurisdictions relevant to your book.
4. Run read-only spec-basis validation per jurisdiction.
5. Configure sanctions / credit escalation and accounts-receivable
   accounts.
6. Publish a dry-run dispatch/invoice and audit export.

## Minimum Trading Controls
- spec-basis validation before any verification, dispatch, or invoice
- full counterparty-diligence evidence (credit-clearance record,
  contract/PO, sanctions-screening record) before any dispatch
- credit-clearance, contract-on-file and sanctions-screening checks
  before any dispatch; sanctions-screening before any invoice
- for a shipment whose origin region or manufacturing entity is
  flagged, AND whose own jurisdiction currently binds a forced-labor
  import-ban statute: a documented supply-chain trace AND a clear-and-
  convincing rebuttal evidence dossier before any dispatch
- sanctions / credit escalation gate
- audit export for every dispatch, invoice, and hold
- backup manual dispatch and invoicing process

## A Day in the Life: Intake → Verify → Dispatch → Settle → Audit

Wholesale of Textiles, Clothing and Footwear (ISIC 4641,
`cloud-itonami-isic-4641`) runs on the same intake / advise / govern /
decide / commit-or-hold loop as every itonami blueprint, but here the
loop is concrete: a regional apparel wholesaler needs to bring a
textile-order (say, a 20,000-unit apparel sale to a counterparty, sourced
from Vietnam) from intake through supply-chain verification to a goods
dispatch and an invoice settlement. Walking through one order, end to
end:

1. **Intake.** The trader books the textile-order through `:forms`:
   order-id, goods-kind, origin-region, manufacturing-entity, quantity,
   counterparty, price, contract-terms, jurisdiction, and the order's
   own diligence record (credit-cleared?, sanctions-screened?). This
   creates a textile-order record at `:order/intake` status. The
   TextileTradeAdvisor only normalizes the patch; it does not invent the
   order-id, counterparty, origin-region, manufacturing entity, or any
   commercial/diligence value.
2. **Verify.** The TextileTradeAdvisor drafts a per-jurisdiction contract
   / sanctions evidence checklist (`:supply-chain/verify`) from
   `textiletrade.facts`, citing the jurisdiction's official spec-basis
   (owner authority, legal basis, provenance) and listing the required
   evidence (credit-clearance record, contract/PO, sanctions-screening
   record) -- PLUS, when the order's own jurisdiction currently binds a
   forced-labor import-ban statute (today: USA under UFLPA), the
   forced-labor rebuttable-presumption citation, informationally. The
   `:textile-trading-governor` sign-off gate must clear: it checks the
   jurisdiction actually has an official spec-basis on file (never
   invent one). A jurisdiction with no spec-basis is a HARD hold at the
   governor node -- it never even reaches a human. This verification
   always escalates to a human for approval; it is never auto.
3. **Dispatch.** Before goods can leave the warehouse, the
   `:textile-trading-governor` sign-off gate runs the full HARD check
   set against the order's own ground truth: the spec-basis exists, the
   evidence checklist is complete, the counterparty's credit has been
   cleared, contract-terms are on file, the counterparty has passed
   sanctions screening, the order has not already been dispatched, and
   -- IF the order's own origin-region or manufacturing-entity is
   flagged AND its own jurisdiction currently binds a forced-labor
   import-ban statute -- BOTH a documented supply-chain trace and a
   clear-and-convincing rebuttal evidence dossier are on file. Any
   failure is a HARD hold that a human cannot override. Note carefully:
   a FLAGGED origin/entity does NOT automatically hold -- if the
   rebuttal evidence is genuinely documented, the presumption is
   REBUTTED and this check clears (see the bundled demo's `to-6`/`to-7`
   pair). If every check is clean, the proposal STILL always escalates
   to a human trading supervisor -- a `:delivery/dispatch` never
   auto-commits at any phase. On approval, the dispatch record is
   drafted (`<JURISDICTION>-DISPATCH-000001`) and the order's
   `:dispatched?` flag is set.
4. **Settle.** Once goods have actually been dispatched, the invoice is
   settled (`:invoice/settle`): the money side of the trade, custody /
   financial transfer. The governor re-checks the spec-basis, the
   evidence completeness, the sanctions screening, and that this order's
   invoice has not already been settled. As with the dispatch, a clean
   invoice STILL always escalates to a human trading supervisor --
   `:invoice/settle` never auto-commits. On approval the invoice record
   is drafted (`<JURISDICTION>-INVOICE-000001`) and the order's
   `:invoiced?` flag is set.
5. **Audit.** The verification, the dispatch sign-off, the dispatch
   record, the invoice sign-off, and the invoice record are all appended
   to the `:audit-ledger` -- immutable and exportable, so a counterparty
   or regulatory dispute (including a CBP UFLPA detention proceeding, or
   a downstream buyer's own UFLPA reasonable-care review) can be traced
   back to the exact spec-basis citation, evidence checklist, supply-
   chain rebuttal dossier, and supervisor sign-off that authorized the
   dispatch and invoice. If something is wrong with the counterparty (a
   credit deterioration, a sanctions hit, a contract gap, an unrebutted
   forced-labor flag), that gets raised as a flag and routed through the
   escalation gate instead of being silently suppressed -- a dispatch
   for that order then waits on governor sign-off of the flag's
   resolution.

Any deviation from this loop is exactly what the Trust Controls in
`docs/business-model.md` exist to catch: an order verified against a
fabricated spec-basis, a dispatch started with incomplete evidence, an
uncleared counterparty credit or a contract gap, a forced-labor
presumption suppressed or waved through without genuine rebuttal
evidence, a sanctions screening suppressed to force a dispatch through,
or an invoice posted without a human sign-off.

## Feel the Decision Gate: `clojure -M:dev:run`

This vertical has no companion playable prototype. The fastest hands-on
way to feel why the `:textile-trading-governor` gate exists -- and why
the forced-labor check is a REBUTTABLE PRESUMPTION rather than a
blanket regional ban -- is the bundled demo, which walks one clean
textile-order through intake → verify → dispatch → settle (each
dispatch/settle pausing for human approval) and then exercises every
HARD-hold failure mode in isolation, PLUS two control scenarios:

- a jurisdiction with no official spec-basis → HOLD (`:no-spec-basis`),
- a counterparty whose credit has not been cleared → HOLD
  (`:credit-uncleared`),
- an order with no contract-terms on file → HOLD (`:contract-missing`),
- a Xinjiang-origin, USA-jurisdiction shipment with NO forced-labor
  rebuttal evidence on file → HOLD
  (`:forced-labor-presumption-unrebutted`) -- the domain-defining check,
- **the SAME Xinjiang-origin, USA-jurisdiction shipment, but WITH a
  documented supply-chain trace AND a clear-and-convincing rebuttal
  evidence dossier on file → dispatches CLEANLY** -- proving the
  presumption is genuinely rebuttable, not a blanket regional ban,
- **the SAME Xinjiang-origin shipment with NO rebuttal evidence, but
  jurisdiction JPN instead of USA → ALSO dispatches CLEANLY** -- proving
  the check is jurisdiction-gated, not a global commodity floor,
- a counterparty that has not passed sanctions screening → HOLD
  (`:counterparty-sanctions-flag-unresolved`),
- a double dispatch of the same order → HOLD (`:already-dispatched`),
- a double invoice of the same order → HOLD (`:already-invoiced`).

Each HOLD settles at the governor node and never reaches a human
approver -- the same failure mode the audit ledger is built to catch and
the minimum trading controls above are built to prevent. It is not a
substitute for those controls, but it is the fastest way for a new
operator (or a reviewer) to feel, hands-on, why the gate exists -- and
why "flagged origin" does not mean "automatic ban" -- before touching a
real deployment.

## Certification
Certified operators must prove spec-basis-grounded verification,
evidence-backed dispatch readiness (credit-clearance, contract-on-file,
sanctions-screening), genuine forced-labor-presumption rebuttal
diligence (not rubber-stamped evidence flags) for flagged-origin/entity
shipments, and human review for every dispatch- and invoice-affecting
action.
