# Contributing

`cloud-itonami-isic-4641` accepts contributions to the OSS blueprint,
capability bindings, policy tests, documentation and operator model.

## Development
This repo is SELF-CONTAINED: there is no bespoke `kotoba-lang/textiletrade`
capability library. This repo holds the business blueprint, the Textile
Trading Governor and the operator contracts.

```bash
clojure -M:dev:test
clojure -M:lint
```

## Rules
- Do not commit real counterparty, credit, sanctions or supply-chain data.
- Keep verification, dispatch and invoice settlement behind the Textile
  Trading Governor.
- Treat textile/apparel/footwear-wholesale workflows as high-risk: add
  tests for spec-basis, evidence, credit, contract, forced-labor
  presumption/rebuttal, sanctions and audit logging.
- Never fabricate a jurisdiction's requirements or a forced-labor rebuttal
  citation to make coverage look bigger.
- Document any new business-model or operator assumption in `docs/`.

## Pull Requests
PRs should describe: what behavior changed, which policy invariant is
affected, how it was tested, whether operator or certification docs need
updates.
