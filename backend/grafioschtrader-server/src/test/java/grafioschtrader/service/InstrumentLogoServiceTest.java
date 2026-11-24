package grafioschtrader.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import grafioschtrader.dto.InstrumentLogoRefDto;
import grafioschtrader.entities.Assetclass;
import grafioschtrader.entities.Security;
import grafioschtrader.service.InstrumentLogoService.CatalogSnapshot;
import grafioschtrader.service.InstrumentLogoService.LogoEntry;
import grafioschtrader.types.AssetclassType;
import grafioschtrader.types.LogoScope;
import grafioschtrader.types.SpecialInvestmentInstruments;

class InstrumentLogoServiceTest {

  private static final String SHA = "a".repeat(64);

  private static CatalogSnapshot sampleCatalog() {
    return CatalogSnapshot.fromEntries(List.of(
        // Deployed company logos
        LogoEntry.of(1, "US0378331005", "Apple Inc.", LogoScope.COMPANY, "US/US0378331005.svg", SHA, null, null, null),
        LogoEntry.of(2, "CH0012255144", "THE SWATCH GROUP AG (UHRN)", LogoScope.COMPANY, "CH/CH0012255144.svg", SHA,
            null, null, null),
        // Deployed issuer logos (in catalog order)
        LogoEntry.of(10, null, "iShares", LogoScope.ISSUER, "issuers/ishares.svg", SHA, "iShares\niSh",
            "www.ishares.com", null),
        LogoEntry.of(11, null, "Vanguard", LogoScope.ISSUER, "issuers/vanguard.svg", SHA, "Vanguard",
            "investor.vanguard.com\nwww.ch.vanguard", ".vanguard.com"),
        LogoEntry.of(12, null, "Lyxor", LogoScope.ISSUER, "issuers/lyxor.svg", SHA, "Lyxor",
            "www.lyxoretf.ch\nwww.lyxoretf.de", null),
        LogoEntry.of(13, null, "Credit Suisse", LogoScope.ISSUER, "issuers/credit-suisse.svg", SHA,
            "Credit Suisse\nCS ETF", "amfunds.credit-suisse.com", null),
        // User-uploaded DB SVGs (assetPath == null)
        LogoEntry.of(100, "DE0009999991", "Custom Uploaded Stock", LogoScope.COMPANY, null, SHA, null, null, null),
        LogoEntry.of(101, null, "Custom DB Issuer", LogoScope.ISSUER, null, SHA, "CustomIssuer", "custom.example.com",
            null)));
  }

  @Test
  void stage1ExplicitOverridesIsinAndIssuerForBothDeployedAndDbSvg() {
    CatalogSnapshot catalog = sampleCatalog();

    // Explicit DB-SVG overrides deployed ISIN
    InstrumentLogoRefDto dbExplicit = InstrumentLogoService.resolve(100, "US0378331005", "Apple Inc.", null,
        SpecialInvestmentInstruments.DIRECT_INVESTMENT, catalog);
    assertThat(dbExplicit).isNotNull();
    assertThat(dbExplicit.idInstrumentLogo()).isEqualTo(100);
    assertThat(dbExplicit.assetPath()).isNull();
    assertThat(dbExplicit.explicit()).isTrue();

    // Explicit deployed issuer overrides everything on a private ISIN-less security
    InstrumentLogoRefDto deployedExplicit = InstrumentLogoService.resolve(10, null, "My Private Fund", null,
        SpecialInvestmentInstruments.DIRECT_INVESTMENT, catalog);
    assertThat(deployedExplicit).isNotNull();
    assertThat(deployedExplicit.idInstrumentLogo()).isEqualTo(10);
    assertThat(deployedExplicit.explicit()).isTrue();
  }

  @Test
  void stage2DeployedIsinMatchesAutomaticallyAndBeatsIssuerRules() {
    CatalogSnapshot catalog = sampleCatalog();

    InstrumentLogoRefDto matched = InstrumentLogoService.resolve(null, "us0378331005", "iShares Apple Tracker",
        "https://www.ishares.com/x", SpecialInvestmentInstruments.ETF, catalog);
    assertThat(matched).isNotNull();
    assertThat(matched.idInstrumentLogo()).isEqualTo(1);
    assertThat(matched.explicit()).isFalse();
  }

  @Test
  void stage2And3NeverAutoMatchUserUploadedDbSvgs() {
    CatalogSnapshot catalog = sampleCatalog();

    // DB-SVG with matching ISIN must NOT auto-resolve in Stage 2
    InstrumentLogoRefDto noAutoCompany = InstrumentLogoService.resolve(null, "DE0009999991", "Some Foreign Security",
        null, SpecialInvestmentInstruments.DIRECT_INVESTMENT, catalog);
    assertThat(noAutoCompany).isNull();

    // DB-SVG issuer must NOT auto-resolve in Stage 3
    InstrumentLogoRefDto noAutoIssuer = InstrumentLogoService.resolve(null, null, "CustomIssuer World ETF",
        "https://custom.example.com/fund", SpecialInvestmentInstruments.ETF, catalog);
    assertThat(noAutoIssuer).isNull();
  }

  @Test
  void deployedEntryOverriddenWithDbSvgKeepsAutoMatchAndServesFromDbEndpoint() {
    CatalogSnapshot catalog = CatalogSnapshot.fromEntries(List.of(LogoEntry.of(50, "CH0244767585", "UBS GROUP AG",
        LogoScope.COMPANY, "CH/CH0244767585.svg", SHA, null, null, null, 0, true)));

    InstrumentLogoRefDto resolved = InstrumentLogoService.resolve(null, "CH0244767585", "UBS", null,
        SpecialInvestmentInstruments.DIRECT_INVESTMENT, catalog);
    assertThat(resolved).isNotNull();
    assertThat(resolved.idInstrumentLogo()).isEqualTo(50);
    assertThat(resolved.explicit()).isFalse();
    assertThat(resolved.assetPath()).isNull();
    assertThat(resolved.createdBy()).isEqualTo(0);
  }

  @Test
  void stage3MatchesIssuerLongestWholeTokenNamePatternBeforeProductLinkHost() {
    CatalogSnapshot catalog = sampleCatalog();

    // Longest token match wins: "Credit Suisse" beats short "iSh"
    InstrumentLogoRefDto longestWins = InstrumentLogoService.resolve(null, "IE00B0000001",
        "iSh Credit Suisse Blend ETF", "https://www.vanguard.com/x", SpecialInvestmentInstruments.ETF, catalog);
    assertThat(longestWins).isNotNull();
    assertThat(longestWins.idInstrumentLogo()).isEqualTo(13);
    assertThat(longestWins.explicit()).isFalse();

    // Whole-token boundary: "iSh" does NOT match inside "Fish"
    InstrumentLogoRefDto noSubstringToken = InstrumentLogoService.resolve(null, null, "Fish Opportunities Fund", null,
        SpecialInvestmentInstruments.MUTUAL_FUND, catalog);
    assertThat(noSubstringToken).isNull();

    // Whole-token match on mutual fund and issuer-risk product
    InstrumentLogoRefDto mutualFund = InstrumentLogoService.resolve(null, null, "iSh Global Equity Fund", null,
        SpecialInvestmentInstruments.MUTUAL_FUND, catalog);
    assertThat(mutualFund).isNotNull();
    assertThat(mutualFund.idInstrumentLogo()).isEqualTo(10);

    InstrumentLogoRefDto issuerRisk = InstrumentLogoService.resolve(null, null, "Vanguard Tracker Certificate", null,
        SpecialInvestmentInstruments.ISSUER_RISK_PRODUCT, catalog);
    assertThat(issuerRisk).isNotNull();
    assertThat(issuerRisk.idInstrumentLogo()).isEqualTo(11);
  }

  @Test
  void stage3FallsBackToExactHostAndHostSuffixWhenNoNamePatternMatches() {
    CatalogSnapshot catalog = sampleCatalog();

    // Exact host match (ComStage ETF hosted on Lyxor)
    InstrumentLogoRefDto byHost = InstrumentLogoService.resolve(null, "LU0378438732", "ComStage DAX UCITS ETF",
        "https://www.lyxoretf.ch/en/retail/products/equity-etf", SpecialInvestmentInstruments.ETF, catalog);
    assertThat(byHost).isNotNull();
    assertThat(byHost.idInstrumentLogo()).isEqualTo(12);

    // Host suffix match without explicit scheme
    InstrumentLogoRefDto bySuffix = InstrumentLogoService.resolve(null, null, "Global All Cap ETF",
        "advisors.vanguard.com/investments/products/vt", SpecialInvestmentInstruments.ETF, catalog);
    assertThat(bySuffix).isNotNull();
    assertThat(bySuffix.idInstrumentLogo()).isEqualTo(11);
  }

  @Test
  void stage4ReturnsNullForDisallowedInstrumentsWithoutExplicitOrIsinMatch() {
    CatalogSnapshot catalog = sampleCatalog();

    for (SpecialInvestmentInstruments instrument : List.of(SpecialInvestmentInstruments.DIRECT_INVESTMENT,
        SpecialInvestmentInstruments.CFD, SpecialInvestmentInstruments.FOREX,
        SpecialInvestmentInstruments.NON_INVESTABLE_INDICES, SpecialInvestmentInstruments.PENSION_FUNDS)) {
      InstrumentLogoRefDto result = InstrumentLogoService.resolve(null, null, "iShares World",
          "https://www.ishares.com/x", instrument, catalog);
      assertThat(result).as("Instrument %s must not get automatic issuer logo", instrument).isNull();
    }
  }

  @Test
  void searchFindsIssuerFromFullEtfNameAndIncludesProductLinkResolvedLogoFirst() {
    CatalogSnapshot catalog = sampleCatalog();

    // Long ETF name finds short issuer "iShares" via name_patterns
    List<InstrumentLogoRefDto> etfSearch = InstrumentLogoService.search("iShares Core MSCI World UCITS ETF",
        "IE00B4L5Y983", null, null, null, SpecialInvestmentInstruments.ETF, catalog);
    assertThat(etfSearch).extracting(InstrumentLogoRefDto::idInstrumentLogo).containsExactly(10);

    // ProductLink-only ComStage match is included and ranked first on initial Dialog (D) open
    List<InstrumentLogoRefDto> productLinkSearch = InstrumentLogoService.search("ComStage DAX UCITS ETF",
        "LU0378438732", null, null, "https://www.lyxoretf.ch/de/product", SpecialInvestmentInstruments.ETF, catalog);
    assertThat(productLinkSearch).isNotEmpty();
    assertThat(productLinkSearch.getFirst().idInstrumentLogo()).isEqualTo(12);

    // Exact ISIN search finds user-uploaded DB-SVG too (for explicit selection in Dialog D), but rejects partial ISIN
    List<InstrumentLogoRefDto> exactDbIsin = InstrumentLogoService.search(null, "DE0009999991", null, null, null, null,
        catalog);
    assertThat(exactDbIsin).extracting(InstrumentLogoRefDto::idInstrumentLogo).containsExactly(100);

    List<InstrumentLogoRefDto> partialIsin = InstrumentLogoService.search(null, "DE000999", null, null, null, null,
        catalog);
    assertThat(partialIsin).isEmpty();
  }

  @Test
  void searchFindsCompanyLogoFromNameOfDerivedInstrument() {
    CatalogSnapshot catalog = CatalogSnapshot.fromEntries(List.of(
        LogoEntry.of(790, "US7170811035", "PFIZER", LogoScope.COMPANY, "US/US7170811035.svg", SHA, null, null, null),
        LogoEntry.of(791, "US0000000001", "PF", LogoScope.COMPANY, "US/US0000000001.svg", SHA, null, null, null),
        LogoEntry.of(792, "US0000000002", "PFIZERS", LogoScope.COMPANY, "US/US0000000002.svg", SHA, null, null,
            null)));

    // The prefilled name of the CFD contains the catalog name as a whole word
    assertThat(InstrumentLogoService.search("Pfizer Inc. CFD", null, null, null, null, null, catalog))
        .extracting(InstrumentLogoRefDto::idInstrumentLogo).containsExactly(790);
    // A part of the catalog name still finds it by substring, together with the longer name containing it
    assertThat(InstrumentLogoService.search("Pfizer", null, null, null, null, null, catalog))
        .extracting(InstrumentLogoRefDto::idInstrumentLogo).containsExactly(790, 792);
  }

  @Test
  void resolvesSecurityEntityDirectly() {
    CatalogSnapshot catalog = sampleCatalog();
    Assetclass etfClass = new Assetclass(AssetclassType.EQUITIES, SpecialInvestmentInstruments.ETF, "Aktien",
        "Equities");
    Security security = new Security();
    security.setName("Vanguard FTSE All-World UCITS ETF");
    security.setAssetClass(etfClass);

    InstrumentLogoRefDto resolved = InstrumentLogoService.resolve(security, catalog);
    assertThat(resolved).isNotNull();
    assertThat(resolved.idInstrumentLogo()).isEqualTo(11);
    assertThat(resolved.explicit()).isFalse();
  }
}
