# Review and Concerns: Sector Classification Concept A

This document reviews [Sector Classification as a Second Dimension](sector_classification_concept_a.md).
The architectural direction is sound, but the concept should not be implemented unchanged. The main concerns are
the semantics of fund weights, persistence of unsuccessful and unmapped feed observations, historical reporting,
and the licensing and stability of the proposed data sources.

## Overall assessment

The following decisions in the concept are strong and should be retained:

- Sector exposure is represented as a weighted distribution rather than a scalar field on `security`. This is the
  correct model for ETFs, funds, and other instruments with exposure to several sectors.
- `Assetclass.subCategoryNLS` remains a geographic dimension instead of continuing to overload one field with
  geography and sector.
- Provider-specific strings are separated from the internal taxonomy.
- Sector feeds use a standalone, priority-ordered connector interface rather than extending the price-feed
  connector contract.
- Unclassified securities remain visible in reports instead of being omitted from the denominator.
- Dominant-sector and weighted look-through reports are separate because they answer different questions.
- Coverage is to be measured against the real GT security inventory before implementation begins.

These choices are substantially better than adding a single sector column to `security`. Subject to the concerns
below, the concept is a good foundation.

## Concerns requiring resolution

### 1. Fund weights must not be normalized blindly

An ETF's reported `sectorWeightings` may describe only its equity portion. A mixed fund can also hold bonds, cash,
derivatives, commodities, or positions for which the provider supplies no sector. Normalizing every returned list
to 100% can therefore turn partial coverage into an apparently complete allocation and materially overstate the
portfolio's classified equity exposure.

The persisted profile should retain:

- the total weight reported by the provider;
- the percentage of the instrument that is classified;
- an explicit residual such as `UNCLASSIFIED` or `NON_EQUITY`;
- the provider's effective or `asOf` date when available;
- the retrieval timestamp.

Weights should be normalized only when the provider contract states that the returned values constitute a complete
distribution over the whole instrument. Otherwise, the unclassified residual must remain visible and participate in
the report denominator.

### 2. The proposed schema cannot support the unmapped-values workflow

The concept promises an administration screen showing provider keys for which no `sector_provider_map` row exists.
The proposed tables do not retain such an observation: an unmapped key cannot produce a
`security_sector_weight`, and `security_sector_profile` contains neither the raw key nor an import outcome.

A persistent feed-observation or import-staging model is needed. It should record at least:

- security, provider, and provider dataset/module;
- the raw provider keys and weights;
- attempt time, retrieval time, and provider effective date;
- outcome such as `SUCCESS`, `UNMAPPED`, `NOT_FOUND`, `INVALID`, or `TRANSIENT_FAILURE`;
- error details and retry state.

The published profile and weights should be replaced atomically only after the complete observation has passed
mapping and weight validation. A failed refresh must leave the last successful profile intact.

This also resolves a retry-state problem in the current proposal: `retry_count` is stored on a profile row, but no
profile row exists for a security that has never been classified successfully.

### 3. Provider-map identity is underspecified

The key `(provider, provider_key)` assumes that one provider uses every key in a single namespace. In practice,
Yahoo's `assetProfile` and `sectorWeightings` are separate datasets, and hierarchical providers may reuse similar
labels at different levels.

Mappings should be namespaced by at least:

- provider;
- dataset or module;
- hierarchy level or key type;
- provider taxonomy version, where available;
- provider key.

For example, `YAHOO / sectorWeightings / sector / technology` and
`FINDB / equities / industry / IT Services` are different mapping identities even if their text happens to overlap.

### 4. Historical-report semantics are not defined

The proposed reports accept `untilDate`, while the schema stores only the current sector profile. A portfolio valued
at a historical date would consequently be grouped using today's company classification and today's fund weights.
Sector changes, fund reallocations, and taxonomy revisions would alter past reports retroactively.

The first release should either:

- state clearly that historical valuation uses the latest available classification; or
- retain effective-dated profile versions and select the profile applicable on `untilDate`.

The first option is acceptable for an initial release if it is disclosed in the REST documentation and UI. It should
not be left implicit.

### 5. Source precedence needs quality semantics

"First non-empty connector result" treats all non-empty responses as equally valid. A partial, stale, or internally
inconsistent result can prevent a more complete lower-priority source from being considered.

Connector results should expose enough metadata for the resolver to assess:

- effective date and staleness;
- classified coverage;
- identifier match method and confidence;
- whether the distribution passed validation;
- source authority for the instrument type.

Precedence can remain deterministic, but an invalid or materially incomplete response should not count as a
successful result. Symbol-only matches should be checked especially carefully after ticker reuse, exchange moves,
mergers, and delistings.

### 6. FinanceDatabase is useful but not authoritative

[FinanceDatabase](https://github.com/JerBouma/FinanceDatabase) describes its equity categorization as a manually
curated, loose approximation of GICS assembled from publicly available sources. Its MIT license is useful for the
repository, but it does not by itself establish the redistribution rights of every upstream data element.

FinanceDatabase is therefore suitable as a best-effort baseline and probe candidate, not as the authoritative source
of truth. The implementation should record its snapshot version or commit and make replacement possible without
changing the internal taxonomy.

### 7. The GICS licensing conclusion is too optimistic

Using different internal codes or describing copied categories as a GT taxonomy does not necessarily make a
GICS-derived hierarchy independent. MSCI states that use of or access to GICS products, services, or information
requires a license in its [GICS methodology](https://www.msci.com/documents/1296102/11185224/GICS%20Methodology%202023.pdf).

Before seeding the detailed hierarchy, GT should choose one of the following explicitly:

- license and identify GICS or another investment taxonomy;
- adopt an openly published economic-activity taxonomy and maintain a GT-specific investment-sector mapping; or
- define a genuinely original, documented GT taxonomy without copying proprietary assignments or definitions.

This is a release gate rather than a naming issue.

### 8. Yahoo should be treated as an optional experimental source

The proposed Yahoo `quoteSummary` endpoint is undocumented and relies on cookie/crumb behavior. It can change or
disappear without a compatibility commitment. Its permitted automated use, persistence, and redistribution should
also be reviewed against the [Yahoo API terms](https://legal.yahoo.com/us/en/yahoo/terms/product-atos/apitnc/index.html)
and [Yahoo Developer Network guidelines](https://legal.yahoo.com/us/en/yahoo/guidelines/ydn/index.html).

Until those questions are resolved, Yahoo should be an explicitly optional connector rather than the only default
source capable of ETF look-through. Failures must degrade to the last valid profile or `Unclassified`, never to a
silently empty allocation.

### 9. Global manual locking requires governance

Sector facts are reasonable global data, but a manual `locked` profile would also be global. One administrator's
judgment would affect every tenant and indefinitely suppress automatic corrections. The propose-change/audit flow
helps, but the concept should define who may lock or unlock a profile, how the reason is recorded, and whether a
tenant-specific reporting override is intentionally unsupported.

## Additional resources worth considering

### Open taxonomy references

- [ISIC Revision 5](https://unstats.un.org/unsd/classifications/Econ/isic/4) from the United Nations provides an
  openly published global hierarchy and machine-readable structure. It classifies economic activities rather than
  investment sectors, so mappings will not always be one-to-one.
- [NACE Revision 2.1](https://ec.europa.eu/eurostat/web/nace) is especially relevant to GT's European coverage.
  Eurostat provides explanatory notes, correspondence tables, and machine-readable formats. It has the same
  economic-activity-versus-investment-sector limitation as ISIC.
- [FTSE Russell ICB](https://www.lseg.com/en/ftse-russell/industry-classification-benchmark-icb) is a mature,
  four-level investment classification used by several European exchanges, including SIX. Its methodology is a
  useful reference, but taxonomy and security-assignment licensing must be established before redistribution.

### Issuer and security classification sources

- [SEC EDGAR public APIs](https://www.sec.gov/search-filings/edgar-application-programming-interfaces) provide
  no-key JSON data for US filers. SEC SIC codes are old and coarse, but they can serve as a free US fallback or a
  cross-check.
- ETF issuers such as iShares, Vanguard, and SPDR publish holdings and sector allocations in factsheets or download
  files. Issuer data is generally the most authoritative source for a fund, although formats, access methods, and
  reuse terms differ. Provider-specific adapters could be added for the most valuable GT holdings.
- [EFAMA's European Fund Classification](https://www.efama.org/SitePages/EFCF.aspx) is a holdings-based,
  pan-European fund classification. It does not provide sector weights, but it can distinguish equity, bond,
  multi-asset, money-market, geographic, and sector-focused funds and prevent inappropriate interpretation.
- [GLEIF's ISIN-to-LEI relationship files](https://www.gleif.org/en/lei-data/lei-mapping/download-isin-to-lei-relationship-files)
  help resolve securities to issuers. They are an identity resource, not a classification source, and their legacy
  ISIN coverage is incomplete.
- Commercial normalized feeds such as Morningstar, LSEG, FactSet, Bloomberg, or EODHD should remain possible
  connector implementations. If dependable international ETF look-through is a requirement, a licensed feed may
  cost less to maintain than multiple unofficial or issuer-specific integrations.

## Recommended first-release boundary

Keep the schema capable of representing a hierarchy, but initially publish and report only top-level sectors. ETF
sources normally expose top-level allocations, and that is the level used by the proposed reports. Industry groups
and industries can be introduced after their taxonomy, mappings, and licensing basis have been validated.

The first release should include:

- current profiles rather than historical versions, with the historical-report limitation disclosed;
- classified coverage and an explicit unclassified/non-equity residual;
- persistent feed observations and visible unmapped values;
- atomic publication that preserves the last successful profile;
- an optional provider architecture with no hard dependency on Yahoo;
- provenance containing source, dataset version, effective date, retrieval date, and match method.

## Required probe and acceptance evidence

The existing probe is the right starting point, but a high response rate alone is insufficient. Its acceptance report
should measure:

- identifier-match accuracy separately from classification availability;
- coverage by MIC, instrument type, and asset class;
- coverage weighted by portfolio market value as well as security count;
- the original ETF weight total and unclassified residual before any normalization;
- source freshness and effective dates where available;
- disagreement rates between sources;
- a manually verified sample covering major exchanges and instrument types;
- false matches caused by ticker reuse, suffix derivation, exchange changes, or ambiguous ISIN searches;
- every unresolved and unmapped security in machine-readable output.

## Conclusion

The concept's architectural direction should be approved, but implementation should wait until the data contract and
source-policy concerns above are resolved. In particular, partial ETF exposure must not be presented as complete,
unmapped and failed observations must be persisted, historical semantics must be explicit, and the taxonomy and feed
licensing basis must be defensible. With those corrections, the proposal provides a strong and extensible second
classification dimension for GT.
