# Governance

`cloud-itonami-isic-4641` is an OSS open-business blueprint for wholesale
of textiles, clothing and footwear.

## Maintainers
Maintainers may merge changes that preserve these invariants:
- a shipment whose origin-region or manufacturing entity is flagged for
  forced labor can never dispatch without a documented, clear-and-
  convincing rebuttal evidence trail on file.
- the Textile Trading Governor remains independent of the advisor.
- hard policy violations (fabricated spec-basis, forced-labor-presumption
  suppression, sanctions-flag suppression) cannot be overridden by human
  approval.
- every verification, dispatch, invoice and hold is auditable.
- counterparty, credit, sanctions and supply-chain data stays outside Git.

## Decision Records
Architecture decisions live in `docs/adr/`. Changes to the trust model,
storage contract, public business model, operator certification or
license should add or update an ADR.

## Operator Governance
Anyone may fork and operate independently. itonami.cloud certification is
a separate trust mark and should require security, audit and data-flow
review.

Certified operators can lose certification for:
- bypassing dispatch or settlement policy checks
- dispatching against an unrebutted forced-labor presumption
- mishandling counterparty or supply-chain data
- misrepresenting certification status
- failing to respond to security incidents
