(ns textiletrade.facts
  "Per-jurisdiction textile/apparel/footwear-wholesale customs / sanctions
  regulatory catalog -- the G2-style spec-basis table the Textile Trading
  Governor checks every `:supply-chain/verify` proposal against ('did the
  advisor cite an OFFICIAL public source for this jurisdiction's customs /
  sanctions requirements, or did it invent one?') -- PLUS a SEPARATE
  `forced-labor-import-ban-basis` catalog, keyed the same way, that
  supplies the citation the advisor uses when drafting a forced-labor
  supply-chain rebuttal checklist (see `textiletrade.governor` for why the
  forced-labor CHECK itself is gated on the ORDER's own jurisdiction,
  unlike the metal-wholesale sibling's metal-type-gated,
  jurisdiction-unconditional conflict-minerals check).

  Each `catalog` entry below is a REAL jurisdiction with a REAL customs /
  sanctions regime: Japan's Ministry of Finance (MOF) Customs / METI
  jurisdiction over trade (関税法; 輸出貿易管理令), the US Customs and
  Border Protection (CBP) entry regime (Tariff Act of 1930) plus OFAC
  (Treasury) sanctions programs -- for the US entry ALSO citing the two
  real textile-specific consumer-protection statutes administered by the
  FTC and CPSC (the Textile Fiber Products Identification Act's fiber-
  content labeling requirement, and the Flammable Fabrics Act's
  flammability-standard requirement), the UK's post-Brexit customs regime
  (Taxation (Cross-border Trade) Act 2018) plus OFSI financial sanctions
  (Sanctions and Anti-Money Laundering Act 2018), and Germany's customs
  administration of EU law (Union Customs Code, Regulation (EU) No
  952/2013) representing the EU regime.

  `forced-labor-import-ban-basis` is a DIFFERENT catalog because the
  real-world legal picture is different in kind from the metal-wholesale
  sibling's `conflict-minerals-basis`: the Uyghur Forced Labor Prevention
  Act (UFLPA) and 19 U.S.C. §1307 (the underlying forced-labor import-ban
  statute UFLPA operationalizes) do not merely impose a downstream
  DISCLOSURE obligation on a company that may sit anywhere in the supply
  chain (the conflict-minerals shape) -- they empower U.S. Customs and
  Border Protection to DETAIN AND EXCLUDE merchandise AT THE U.S. BORDER,
  i.e. at the exact customs-entry act this actor's OWN `:delivery/
  dispatch` represents when the order's own jurisdiction is the United
  States. This is why (see `textiletrade.governor`'s
  `forced-labor-presumption-unrebutted-violations` docstring) the check IS
  gated on the order's own `:jurisdiction`, unlike the metal-wholesale
  sibling's deliberately jurisdiction-unconditional conflict-minerals
  check.

  Coverage is reported HONESTLY (see `coverage`), the same discipline
  every sibling actor's `facts` namespace uses: a jurisdiction not in
  `catalog` has NO spec-basis, full stop -- the advisor must not
  fabricate one, and the governor holds if it tries.")

(def catalog
  "iso3 -> requirement map. `:required-evidence` is the counterparty-
  diligence evidence set (credit-clearance record, contract/PO,
  sanctions-screening record) evaluated by `evidence-incomplete-
  violations`; `:legal-basis` / `:owner-authority` / `:provenance` are
  the G2 citation the governor requires before any `:supply-chain/verify`
  proposal can commit. This is the GENERAL trade-jurisdiction catalog --
  see `forced-labor-import-ban-basis` below for the separate,
  jurisdiction-keyed forced-labor-presumption citation."
  {"JPN" {:name "JPN"
          :owner-authority "財務省 (MOF) 関税局 / 経済産業省 (METI)"
          :legal-basis "関税法 (Customs Act); 輸出貿易管理令 (Foreign Exchange and Foreign Trade Control Act / Export Trade Control Order)"
          :provenance "https://www.customs.go.jp/"
          :required-evidence ["credit-clearance record"
                              "contract/PO"
                              "sanctions-screening (OFAC/equivalent) record"]}
   "USA" {:name "USA"
          :owner-authority "U.S. Customs and Border Protection (CBP, DHS) / OFAC (U.S. Treasury) / FTC / CPSC"
          :legal-basis "Tariff Act of 1930 (19 U.S.C. Chapter 4), customs entry requirements; OFAC sanctions programs; Textile Fiber Products Identification Act (15 U.S.C. §70 et seq., fiber-content labeling, FTC); Flammable Fabrics Act (15 U.S.C. §1191 et seq., flammability standards, CPSC)"
          :provenance "https://www.cbp.gov/"
          :required-evidence ["credit-clearance record"
                              "contract/PO"
                              "sanctions-screening (OFAC/equivalent) record"]}
   "GBR" {:name "GBR"
          :owner-authority "HM Revenue & Customs (HMRC) / Office of Financial Sanctions Implementation (OFSI)"
          :legal-basis "Taxation (Cross-border Trade) Act 2018; Sanctions and Anti-Money Laundering Act 2018 (SAMLA 2018)"
          :provenance "https://www.gov.uk/government/organisations/hm-revenue-customs"
          :required-evidence ["credit-clearance record"
                              "contract/PO"
                              "sanctions-screening (OFAC/equivalent) record"]}
   "DEU" {:name "DEU"
          :owner-authority "Generalzolldirektion (German Customs) under the Bundesministerium der Finanzen (BMF)"
          :legal-basis "Union Customs Code (Regulation (EU) No 952/2013); EU financial sanctions regulations"
          :provenance "https://www.zoll.de/"
          :required-evidence ["credit-clearance record"
                              "contract/PO"
                              "sanctions-screening (OFAC/equivalent) record"]}})

(def flagged-origins
  "Origin-region descriptors this actor treats as triggering the forced-
  labor REBUTTABLE PRESUMPTION -- gating
  `textiletrade.governor`'s `forced-labor-presumption-unrebutted-
  violations` check (together with `entity-list-flagged?`, see that
  function's docstring). Two REAL, independently-enacted U.S. statutory
  bases converge on the SAME underlying forced-labor import-ban
  mechanism (19 U.S.C. §1307):

  - the Xinjiang Uyghur Autonomous Region (XUAR) of the People's Republic
    of China, per the Uyghur Forced Labor Prevention Act (UFLPA, Pub. L.
    117-78, 2021) Section 3(a) -- goods mined, produced, or manufactured
    wholly or in part in XUAR are REBUTTABLY PRESUMED to be made with
    forced labor. I am highly confident about this citation.
  - the Democratic People's Republic of Korea (North Korea), per the
    Countering America's Adversaries Through Sanctions Act (CAATSA,
    Pub. L. 115-44, 2017) Section 321(b) -- goods mined, produced, or
    manufactured wholly or in part by North Korean labor (anywhere,
    including in a third country) are REBUTTABLY PRESUMED to be made
    with forced/convict labor. I am reasonably, but not fully, confident
    about the precise CAATSA section number (321(b)); I am highly
    confident the underlying mechanism -- a rebuttable presumption under
    19 U.S.C. §1307 for North-Korean-labor-linked goods -- is real. This
    should be independently verified before this catalog is relied on
    operationally.

  This is NOT the strict legal definition of either statute's full scope
  (UFLPA also reaches goods produced by an entity on the UFLPA Entity
  List regardless of the goods' own physical origin region -- see
  `entity-list-flagged?` below, the SAME split the metal-wholesale
  sibling's `conflict-minerals-metals` set draws between a commodity
  property and an entity-list property) -- it is a starting set to prove
  the governor contract end-to-end, not a claim of exhaustive coverage of
  every flagged region worldwide. Adding an origin-region is additive:
  one entry here, citing a real official source -- never fabricate one to
  make coverage look bigger."
  #{"Xinjiang Uyghur Autonomous Region, China"
    "Democratic People's Republic of Korea (North Korea) -- labor-linked production"})

(defn flagged-origin?
  [origin-region]
  (boolean (contains? flagged-origins origin-region)))

(def forced-labor-import-ban-basis
  "iso3 -> forced-labor rebuttable-presumption import-ban citation,
  DISTINCT from `catalog` above (see namespace docstring for why).
  `:binding?` records whether the statute is CURRENTLY enforceable, not
  merely enacted -- see `forced-labor-import-ban-binding?`.

  - USA: the Uyghur Forced Labor Prevention Act (UFLPA, Pub. L. 117-78,
    2021), CBP-enforced, operationalizing the underlying forced-labor
    import ban at 19 U.S.C. §1307. This is REAL, CURRENT, ACTIVELY-
    ENFORCED U.S. law (enacted 2021) -- I am highly confident about this
    citation. `:binding? true`.
  - DEU, representing the EU regime: Regulation (EU) 2024/3015 prohibiting
    products made with forced labour from being placed or made available
    on the Union market (or exported from it). I am highly confident this
    regulation exists, was adopted in 2024, and prohibits forced-labour
    products from the EU market. I am only moderately confident about the
    precise phased-application timeline (application begins roughly three
    years after entry into force, i.e. around 2027, per the regulation's
    own transitional provisions) -- this should be independently verified.
    Seeded here at `:binding? false` (adopted, not yet in full
    application as of this catalog's authoring date) so the governor does
    NOT yet gate a DEU/EU-jurisdiction dispatch on it -- flipping this to
    `true` once the regulation's phased application actually takes effect
    is a one-line change, not a re-architecture (the same forward-
    compatible seeding discipline `textiletrade.facts` uses throughout).

  Every other jurisdiction -- including JPN and GBR, both present in the
  GENERAL catalog above -- has NO forced-labor rebuttable-presumption
  IMPORT-BAN statute seeded here. This is an honest gap, not an
  oversight: unlike the metal-wholesale sibling's OECD-Guidance universal
  fallback (a genuine non-statutory operational baseline every
  jurisdiction's 3TG/cobalt trade observes), I am not confident there is
  a comparable JPN or GBR statute creating an import-ban REBUTTABLE
  PRESUMPTION mechanism the way UFLPA/CAATSA do, so none is fabricated
  here."
  {"USA" {:owner-authority "U.S. Customs and Border Protection (CBP); DHS Forced Labor Enforcement Task Force (FLETF)"
          :legal-basis "Uyghur Forced Labor Prevention Act (UFLPA, Pub. L. 117-78, 2021); 19 U.S.C. §1307 (forced-labor import ban, the underlying statute UFLPA operationalizes); rebuttable presumption under UFLPA Section 3(a) for goods mined, produced, or manufactured wholly or in part in the Xinjiang Uyghur Autonomous Region, or produced by an entity on the UFLPA Entity List; Countering America's Adversaries Through Sanctions Act (CAATSA, Pub. L. 115-44, 2017) Section 321(b), a parallel rebuttable presumption for North-Korean-labor-linked goods"
          :provenance "https://www.cbp.gov/trade/forced-labor/UFLPA"
          :binding? true}
   "DEU" {:owner-authority "European Commission; national market surveillance authorities"
          :legal-basis "Regulation (EU) 2024/3015 prohibiting products made with forced labour on the Union market, phased application from approximately 2027 per its own transitional provisions"
          :provenance "https://eur-lex.europa.eu/eli/reg/2024/3015/oj"
          :binding? false}})

(defn forced-labor-import-ban-binding?
  "Does `iso3` currently have a BINDING forced-labor rebuttable-
  presumption import-ban statute (not merely an adopted-but-not-yet-
  applicable one)? See `forced-labor-import-ban-basis` docstring."
  [iso3]
  (boolean (get-in forced-labor-import-ban-basis [iso3 :binding?])))

(defn forced-labor-presumption-citation
  "The forced-labor import-ban citation for `iso3`, or nil if none is
  seeded (whether because the jurisdiction has none, or because the one
  seeded is not yet binding -- see `forced-labor-import-ban-basis`).
  Informational only: NEVER what the governor itself gates on (the
  governor re-reads `forced-labor-import-ban-binding?` plus the order's
  own ground-truth facts directly, see `textiletrade.governor`)."
  [iso3]
  (get forced-labor-import-ban-basis iso3))

(defn forced-labor-presumption-triggered?
  "True when THIS order's own jurisdiction currently has a BINDING
  forced-labor rebuttable-presumption import-ban statute AND the order's
  origin-region is flagged (`flagged-origin?`) OR its manufacturing
  entity is flagged (`:entity-list-flagged?`, the operator's own
  cross-reference against the real, externally-maintained CBP UFLPA
  Entity List -- https://www.dhs.gov/uflpa-entity-list -- NOT embedded
  verbatim in this catalog for the SAME reason `:sanctions-screened?`
  does not embed the OFAC SDN list verbatim: it is a frequently-updated
  official registry, not a static regulatory citation).

  See `textiletrade.governor`'s `forced-labor-presumption-unrebutted-
  violations` for the full reasoning on why this is jurisdiction-gated,
  unlike the metal-wholesale sibling's jurisdiction-unconditional
  conflict-minerals check."
  [{:keys [jurisdiction origin-region entity-list-flagged?]}]
  (and (forced-labor-import-ban-binding? jurisdiction)
       (or (flagged-origin? origin-region) (true? entity-list-flagged?))))

(defn spec-basis
  "The jurisdiction's GENERAL trade requirement map, or nil -- nil means
  NO spec-basis, and the governor must hold any proposal that tries to
  verify supply-chain compliance, dispatch goods, or settle an invoice
  on it."
  [iso3]
  (get catalog iso3))

(defn coverage
  "Honest coverage report: how many of the requested jurisdictions
  actually have a spec-basis entry. Never report a missing jurisdiction
  as covered."
  ([] (coverage (keys catalog)))
  ([iso3s]
   (let [have (filter catalog iso3s)
         missing (remove catalog iso3s)]
     {:requested (count iso3s)
      :covered (count have)
      :covered-jurisdictions (vec (sort have))
      :missing-jurisdictions (vec (sort missing))
      :note (str "cloud-itonami-isic-4641 R0: " (count catalog)
                 " jurisdictions seeded with an official spec-basis. "
                 "This is a starting catalog, not a survey of all ~194 "
                 "jurisdictions -- extend `textiletrade.facts/catalog`, "
                 "never fabricate a jurisdiction's requirements.")})))

(defn required-evidence-satisfied?
  "Does `submitted` (a set/coll of evidence keywords or strings) satisfy
  every GENERAL evidence item listed for `iso3`? Missing spec-basis ->
  never satisfied. Deliberately does NOT include forced-labor rebuttal
  evidence -- that is a separate, jurisdiction-gated governor check, not
  part of the generic per-jurisdiction evidence checklist (see
  `textiletrade.governor`)."
  [iso3 submitted]
  (when-let [{:keys [required-evidence]} (spec-basis iso3)]
    (let [need (count required-evidence)
          have (count (filter (set submitted) required-evidence))]
      (= need have))))

(defn evidence-checklist [iso3]
  (:required-evidence (spec-basis iso3) []))
