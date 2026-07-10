# Security Policy

This project handles counterparty, credit, sanctions and supply-chain
compliance workflows. Treat vulnerabilities as potentially high impact
even when the demo data is synthetic.

## Do Not Disclose Publicly

Report privately before opening public issues for:

- credential exposure
- real counterparty or supply-chain data exposure
- authorization bypass
- Textile Trading Governor bypass
- forced-labor-presumption rebuttal-evidence forgery or suppression
- audit-ledger tampering
- over-disclosure in reports or exports

## Reporting

Use GitHub private vulnerability reporting when available for the repository.
If that is unavailable, contact the repository maintainers through the
cloud-itonami organization before publishing details.

Include:

- affected commit or version
- reproduction steps
- expected and actual behavior
- impact on counterparty data, policy enforcement or audit logging
- suggested fix, if known

## Production Guidance

- Store secrets outside Git.
- Keep real counterparty, credit and supply-chain data outside this repository.
- Run policy tests before deployment.
- Export and review audit logs regularly.
- Use least privilege for operators and service accounts.
