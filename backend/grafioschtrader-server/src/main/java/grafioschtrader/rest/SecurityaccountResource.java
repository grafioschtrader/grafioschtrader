package grafioschtrader.rest;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import java.util.Date;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.format.annotation.DateTimeFormat.ISO;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import grafiosch.rest.UpdateCreateDeleteWithTenantJpaRepository;
import grafiosch.rest.UpdateCreateDeleteWithTenantResource;
import grafioschtrader.GlobalConstants;
import grafioschtrader.entities.Securityaccount;
import grafioschtrader.reports.SecurityGroupByAssetclassSubCategoryReport;
import grafioschtrader.reports.SecurityGroupByAssetclassWithCashReport;
import grafioschtrader.reports.SecurityGroupByBaseReport;
import grafioschtrader.reports.SecurityPositionByCurrencyGrandSummaryReport;
import grafioschtrader.reportviews.securityaccount.SecurityPositionGrandSummary;
import grafioschtrader.repository.SecurityaccountJpaRepository;
import grafioschtrader.types.AssetclassType;
import grafioschtrader.types.SpecialInvestmentInstruments;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping(RequestGTMappings.SECURITYACCOUNT_MAP)
@Tag(name = Securityaccount.TABNAME, description = "Controller for security account")
public class SecurityaccountResource extends UpdateCreateDeleteWithTenantResource<Securityaccount> {

  @Autowired
  private SecurityaccountJpaRepository securityaccountJpaRepository;

  @Autowired
  private SecurityPositionByCurrencyGrandSummaryReport securityPositionGrandSummaryReport;

  @Autowired
  private SecurityGroupByAssetclassWithCashReport securityGroupByAssetclassWithCashReport;

  @Autowired
  private AutowireCapableBeanFactory beanFactory;

  public SecurityaccountResource() {
    super(Securityaccount.class);
  }

  /*
   * private final Logger log = LoggerFactory.getLogger(this.getClass());
   * 
   * 
   * @DeleteMapping(value = "/{idSecuritycashaccount}", produces = APPLICATION_JSON_VALUE) public ResponseEntity<Void>
   * deleteSecurityaccount(@PathVariable final Integer idSecuritycashaccount) {
   * log.debug("Delete by id Securityaccount : {}", idSecuritycashaccount); final User user = (User)
   * SecurityContextHolder.getContext().getAuthentication().getDetails();
   * securityaccountJpaRepository.deleteSecurityaccount(idSecuritycashaccount, user.getIdTenant());
   * 
   * return ResponseEntity.ok().build(); }
   */
  @Override
  protected UpdateCreateDeleteWithTenantJpaRepository<Securityaccount> getUpdateCreateJpaRepository() {
    return securityaccountJpaRepository;
  }

  //============================================================================
  //TENANT LEVEL REPORTS
  //============================================================================
  @Operation(summary = "Get tenant security positions grouped by asset class including cash holdings", description = """
      Generates a comprehensive portfolio report that groups security positions by asset class type and includes cash
      account holdings as pseudo-securities. Cash holdings are classified as CURRENCY_CASH (main currency) or
      CURRENCY_FOREIGN (foreign currencies). Provides complete portfolio allocation analysis with multi-currency
      support and normalization to tenant's main currency.""", tags = { Securityaccount.TABNAME })
  @GetMapping(value = "/tenantsecurityaccountsummary/assetclasstypewithcash", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<SecurityPositionGrandSummary> getSecurityGroupByAssetclassWithCashReportByTenant(
      @RequestParam() final boolean includeClosedPosition,
      @RequestParam() @DateTimeFormat(iso = ISO.DATE) final Date untilDate) throws Exception {
    return new ResponseEntity<>(securityGroupByAssetclassWithCashReport
        .getSecurityPositionGrandSummaryIdTenant(includeClosedPosition, untilDate), HttpStatus.OK);
  }

  @Operation(summary = "Get tenant security positions grouped by trading currency", description = """
      Creates position summaries for all tenant portfolios grouped by their trading currency. Provides currency
      exposure analysis and facilitates foreign exchange risk assessment. Each currency group shows total holdings,
      current values, and performance metrics in that specific currency.""", tags = { Securityaccount.TABNAME })
  @GetMapping(value = "/tenantsecurityaccountsummary/currency", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<SecurityPositionGrandSummary> getSecurityPositionSummaryTenant(
      @RequestParam() final boolean includeClosedPosition,
      @RequestParam() @DateTimeFormat(iso = ISO.DATE) final Date untilDate) throws Exception {
    return new ResponseEntity<>(
        securityPositionGrandSummaryReport.getSecurityPositionGrandSummaryIdTenant(includeClosedPosition, untilDate),
        HttpStatus.OK);
  }

  @Operation(summary = "Get tenant security positions grouped by asset class type", description = """
      Groups tenant security positions by broad investment categories such as EQUITIES, FIXED_INCOME, MONEY_MARKET,
      COMMODITIES, REAL_ESTATE, MULTI_ASSET, CONVERTIBLE_BOND, CREDIT_DERIVATIVE, and CURRENCY_PAIR. Excludes cash
      positions for focus on invested assets only.""", tags = { Securityaccount.TABNAME })
  @GetMapping(value = "/tenantsecurityaccountsummary/assetclasstype", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<SecurityPositionGrandSummary> getSecurityPositionSummaryByAssetclassTypeAndTenant(
      @RequestParam() final boolean includeClosedPosition,
      @RequestParam() @DateTimeFormat(iso = ISO.DATE) final Date untilDate) throws Exception {
    return new ResponseEntity<>(
        getDynamicGroupReport(AssetclassType.class, SecurityGroupByBaseReport.ASSETCLASS_CATEGORY_FIELD_NAME)
            .getSecurityPositionGrandSummaryIdTenant(includeClosedPosition, untilDate),
        HttpStatus.OK);
  }

  @Operation(summary = "Get tenant security positions grouped by special investment instrument", description = """
      Groups positions by specific investment vehicle types such as DIRECT_INVESTMENT, ETF, MUTUAL_FUND,
      PENSION_FUNDS, CFD, FOREX, ISSUER_RISK_PRODUCT, and NON_INVESTABLE_INDICES. Enables analysis of how
      investments are structured across different instrument types within asset classes.""", tags = {
      Securityaccount.TABNAME })
  @GetMapping(value = "tenantsecurityaccountsummary/specialinvestmentinstrument", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<SecurityPositionGrandSummary> getSecurityPositionSummaryBySpecInvestInstAndTenant(
      @RequestParam() final boolean includeClosedPosition,
      @RequestParam() @DateTimeFormat(iso = ISO.DATE) final Date untilDate) throws Exception {
    return new ResponseEntity<>(getDynamicGroupReport(SpecialInvestmentInstruments.class,
        SecurityGroupByBaseReport.ASSETCLASS_SPEC_INVEST_INST_FIELD_NAME)
            .getSecurityPositionGrandSummaryIdTenant(includeClosedPosition, untilDate),
        HttpStatus.OK);
  }

  @Operation(summary = "Get tenant security positions grouped by asset class subcategory with localization", description = """
      Groups positions by detailed asset class subcategories that provide more granular classification than main
      asset classes. Subcategories are displayed in the user's preferred language for enhanced accessibility and
      regional compliance requirements. Examples include 'emerging markets' or 'developed countries' within
      EQUITIES.""", tags = {
      Securityaccount.TABNAME })
  @GetMapping(value = "/tenantsecurityaccountsummary/subcategorynls", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<SecurityPositionGrandSummary> getSecurityPositionSummaryBySubCategoryNLSAndTenant(
      @RequestParam() final boolean includeClosedPosition,
      @RequestParam() @DateTimeFormat(iso = ISO.DATE) final Date untilDate) throws Exception {
    return new ResponseEntity<>(
        getAssetclassSubCategoryReport().getSecurityPositionGrandSummaryIdTenant(includeClosedPosition, untilDate),
        HttpStatus.OK);
  }

  @Operation(summary = "Get tenant security positions grouped by unique asset class identifier", description = """
      Groups positions by unique asset class ID for detailed analysis and cross-referencing with asset class master
      data. Useful for technical integrations and detailed portfolio analytics where specific asset class
      identification is required.""", tags = { Securityaccount.TABNAME })
  @GetMapping(value = "tenantsecurityaccountsummary/idassetclass", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<SecurityPositionGrandSummary> getSecurityPositionSummaryByAssetclassAndTenant(
      @RequestParam() final boolean includeClosedPosition,
      @RequestParam() @DateTimeFormat(iso = ISO.DATE) final Date untilDate) throws Exception {
    return new ResponseEntity<>(
        getDynamicGroupReport(Integer.class, SecurityGroupByBaseReport.ASSETCLASS_ID_ASSETCLASS_FIELD_NAME)
            .getSecurityPositionGrandSummaryIdTenant(includeClosedPosition, untilDate),
        HttpStatus.OK);
  }

  //============================================================================
  //PORTFOLIO LEVEL REPORTS
  //============================================================================
  @Operation(summary = "Get portfolio security positions grouped by trading currency",
      description = """
          Creates position summaries for a specific portfolio grouped by trading currency. Provides portfolio-level 
          currency exposure analysis and enables assessment of foreign exchange risk within the selected portfolio. 
          Validates user access to the requested portfolio.""",
      tags = {Securityaccount.TABNAME}
  )
  @GetMapping(value = "/{idPortfolio}/portfoliosecurityaccountsummary/currency", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<SecurityPositionGrandSummary> getSecurityPositionSummaryPortfolio(
      @PathVariable final Integer idPortfolio, @RequestParam() final boolean includeClosedPosition,
      @RequestParam() @DateTimeFormat(iso = ISO.DATE) final Date untilDate) throws Exception {
    return new ResponseEntity<>(securityPositionGrandSummaryReport
        .getSecurityPositionGrandSummaryIdPortfolio(idPortfolio, includeClosedPosition, untilDate), HttpStatus.OK);
  }

  @Operation(summary = "Get portfolio security positions grouped by asset class type",
      description = """
          Groups security positions within a specific portfolio by broad investment categories. Provides portfolio-level 
          asset allocation analysis showing distribution across EQUITIES, FIXED_INCOME, COMMODITIES, and other major 
          asset classes. Excludes cash positions.""",
      tags = {Securityaccount.TABNAME}
  )
  @GetMapping(value = "/{idPortfolio}/portfoliosecurityaccountsummary/assetclasstype", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<SecurityPositionGrandSummary> getSecurityPositionSummaryByAssetclassTypeAndPortfolio(
      @PathVariable final Integer idPortfolio, @RequestParam() final boolean includeClosedPosition,
      @RequestParam() @DateTimeFormat(iso = ISO.DATE) final Date untilDate) throws Exception {
    return new ResponseEntity<>(
        getDynamicGroupReport(AssetclassType.class, SecurityGroupByBaseReport.ASSETCLASS_CATEGORY_FIELD_NAME)
            .getSecurityPositionGrandSummaryIdPortfolio(idPortfolio, includeClosedPosition, untilDate),
        HttpStatus.OK);
  }

  @Operation(summary = "Get portfolio security positions grouped by special investment instrument",
      description = """
          Groups positions within a specific portfolio by investment vehicle types such as ETFs, mutual funds, direct 
          investments, CFDs, and other instruments. Enables analysis of investment structure and instrument 
          diversification within the portfolio.""",
      tags = {Securityaccount.TABNAME}
  )
  @GetMapping(value = "/{idPortfolio}/portfoliosecurityaccountsummary/specialinvestmentinstrument", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<SecurityPositionGrandSummary> getSecurityPositionSummaryBySpecInvestInstAndPortfolio(
      @PathVariable final Integer idPortfolio, @RequestParam() final boolean includeClosedPosition,
      @RequestParam() @DateTimeFormat(iso = ISO.DATE) final Date untilDate) throws Exception {
    return new ResponseEntity<>(
        getDynamicGroupReport(SpecialInvestmentInstruments.class,
            SecurityGroupByBaseReport.ASSETCLASS_SPEC_INVEST_INST_FIELD_NAME)
                .getSecurityPositionGrandSummaryIdPortfolio(idPortfolio, includeClosedPosition, untilDate),
        HttpStatus.OK);
  }

  @Operation(summary = "Get portfolio security positions grouped by asset class subcategory with localization",
      description = """
          Groups positions within a specific portfolio by detailed asset class subcategories displayed in the user's 
          preferred language. Provides granular portfolio allocation analysis beyond main asset classes, such as 
          geographical or sector-based subdivisions.""",
      tags = {Securityaccount.TABNAME}
  )
  @GetMapping(value = "/{idPortfolio}/portfoliosecurityaccountsummary/subcategorynls", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<SecurityPositionGrandSummary> getSecurityPositionSummaryBySubCategoryNLSAndPortfolio(
      @PathVariable final Integer idPortfolio, @RequestParam() final boolean includeClosedPosition,
      @RequestParam() @DateTimeFormat(iso = ISO.DATE) final Date untilDate) throws Exception {
    return new ResponseEntity<>(getAssetclassSubCategoryReport().getSecurityPositionGrandSummaryIdPortfolio(idPortfolio,
        includeClosedPosition, untilDate), HttpStatus.OK);
  }

  @Operation(summary = "Get portfolio security positions grouped by unique asset class identifier",
      description = """
          Groups positions within a specific portfolio by unique asset class ID for detailed technical analysis and 
          cross-referencing. Useful for portfolio analytics requiring specific asset class identification and master 
          data integration.""",
      tags = {Securityaccount.TABNAME}
  )
  @GetMapping(value = "/{idPortfolio}/portfoliosecurityaccountsummary/idassetclass", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<SecurityPositionGrandSummary> getSecurityPositionSummaryByAssetclassAndPortfolio(
      @PathVariable final Integer idPortfolio, @RequestParam() final boolean includeClosedPosition,
      @RequestParam() @DateTimeFormat(iso = ISO.DATE) final Date untilDate) throws Exception {
    return new ResponseEntity<>(
        getDynamicGroupReport(Integer.class, SecurityGroupByBaseReport.ASSETCLASS_ID_ASSETCLASS_FIELD_NAME)
            .getSecurityPositionGrandSummaryIdPortfolio(idPortfolio, includeClosedPosition, untilDate),
        HttpStatus.OK);
  }
  
  // ============================================================================
  // SECURITY ACCOUNT LEVEL REPORTS
  // ============================================================================
  
  
  @Operation(summary = "Get security account positions grouped by trading currency",
      description = """
          Creates position summaries for a specific security account grouped by trading currency. Provides account-level 
          granularity for detailed position analysis, including all transactions, adjustments, and valuations for 
          securities held within the specified account.""",
      tags = {Securityaccount.TABNAME}
  )
  @GetMapping(value = "/{idSecurityaccount}/securityaccountsummary/currency", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<SecurityPositionGrandSummary> getPostionSummarySecurityaccount(
      @PathVariable final Integer idSecurityaccount, @RequestParam() final boolean includeClosedPosition,
      @RequestParam() @DateTimeFormat(iso = ISO.DATE) final Date untilDate) throws Exception {
    return new ResponseEntity<>(securityPositionGrandSummaryReport.getSecurityPositionGrandSummaryIdSecurityaccount(
        idSecurityaccount, includeClosedPosition, untilDate), HttpStatus.OK);
  }

  @Operation(summary = "Get security account positions grouped by asset class type",
      description = """
          Groups positions within a specific security account by broad investment categories. Provides detailed 
          account-level asset allocation analysis showing how the account's holdings are distributed across different 
          asset classes.""",
      tags = {Securityaccount.TABNAME}
  )
  @GetMapping(value = "/{idSecurityaccount}/securityaccountsummary/assetclasstype", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<SecurityPositionGrandSummary> getPostionSummarySecurityaccountByAssetclassTypeAndSecurityaccount(
      @PathVariable final Integer idSecurityaccount, @RequestParam() final boolean includeClosedPosition,
      @RequestParam() @DateTimeFormat(iso = ISO.DATE) final Date untilDate) throws Exception {
    return new ResponseEntity<>(
        getDynamicGroupReport(AssetclassType.class, SecurityGroupByBaseReport.ASSETCLASS_CATEGORY_FIELD_NAME)
            .getSecurityPositionGrandSummaryIdSecurityaccount(idSecurityaccount, includeClosedPosition, untilDate),
        HttpStatus.OK);
  }

  @Operation(summary = "Get security account positions grouped by special investment instrument",
      description = """
          Groups positions within a specific security account by investment vehicle types. Enables detailed analysis of 
          how investments within the account are structured across different instrument types such as ETFs, mutual 
          funds, direct holdings, and derivatives.""",
      tags = {Securityaccount.TABNAME}
  )
  @GetMapping(value = "/{idSecurityaccount}/securityaccountsummary/specialinvestmentinstrument", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<SecurityPositionGrandSummary> getPostionSummarySecurityaccountBySpecInvestInstAndSecurityaccount(
      @PathVariable final Integer idSecurityaccount, @RequestParam() final boolean includeClosedPosition,
      @RequestParam() @DateTimeFormat(iso = ISO.DATE) final Date untilDate) throws Exception {
    return new ResponseEntity<>(
        getDynamicGroupReport(SpecialInvestmentInstruments.class,
            SecurityGroupByBaseReport.ASSETCLASS_SPEC_INVEST_INST_FIELD_NAME)
                .getSecurityPositionGrandSummaryIdSecurityaccount(idSecurityaccount, includeClosedPosition, untilDate),
        HttpStatus.OK);
  }
  
  @Operation(summary = "Get security account positions grouped by asset class subcategory with localization",
      description = """
          Groups positions within a specific security account by detailed asset class subcategories displayed in the 
          user's preferred language. Provides the most granular level of account position analysis with enhanced 
          accessibility through localized subcategory names.""",
      tags = {Securityaccount.TABNAME}
  )
  @GetMapping(value = "/{idSecurityaccount}/securityaccountsummary/subcategorynls", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<SecurityPositionGrandSummary> getSecurityPositionSummaryBySubCategoryNLSAndSecurityaccount(
      @PathVariable final Integer idSecurityaccount, @RequestParam() final boolean includeClosedPosition,
      @RequestParam() @DateTimeFormat(iso = ISO.DATE) final Date untilDate) throws Exception {
    return new ResponseEntity<>(getAssetclassSubCategoryReport().getSecurityPositionGrandSummaryIdSecurityaccount(
        idSecurityaccount, includeClosedPosition, untilDate), HttpStatus.OK);
  }

  @Operation(summary = "Get security account positions grouped by unique asset class identifier",
      description = """
          Groups positions within a specific security account by unique asset class ID for detailed technical analysis. 
          Provides the most granular level of position analysis with specific asset class identification for detailed 
          portfolio analytics and master data integration.""",
      tags = {Securityaccount.TABNAME}
  )
  @GetMapping(value = "/{idSecurityaccount}/securityaccountsummary/idassetclass", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<SecurityPositionGrandSummary> getSecurityPositionSummaryByAssetclassAndSecurityaccount(
      @PathVariable final Integer idSecurityaccount, @RequestParam() final boolean includeClosedPosition,
      @RequestParam() @DateTimeFormat(iso = ISO.DATE) final Date untilDate) throws Exception {
    return new ResponseEntity<>(
        getDynamicGroupReport(Integer.class, SecurityGroupByBaseReport.ASSETCLASS_ID_ASSETCLASS_FIELD_NAME)
            .getSecurityPositionGrandSummaryIdSecurityaccount(idSecurityaccount, includeClosedPosition, untilDate),
        HttpStatus.OK);
  }

  private <T> SecurityGroupByBaseReport<T> getDynamicGroupReport(T type, String fieldName) {
    SecurityGroupByBaseReport<T> securityGroupByBaseReport = new SecurityGroupByBaseReport<>(fieldName);
    beanFactory.autowireBean(securityGroupByBaseReport);
    return securityGroupByBaseReport;
  }

  private SecurityGroupByAssetclassSubCategoryReport getAssetclassSubCategoryReport() {
    SecurityGroupByAssetclassSubCategoryReport securityGroupByAssetclassSubCategoryReport = new SecurityGroupByAssetclassSubCategoryReport();
    beanFactory.autowireBean(securityGroupByAssetclassSubCategoryReport);
    return securityGroupByAssetclassSubCategoryReport;
  }

  @Override
  protected String getPrefixEntityLimit() {
    return GlobalConstants.GT_LIMIT_DAY;
  }

}
