package grafioschtrader.rest;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import java.util.Date;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.format.annotation.DateTimeFormat.ISO;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import grafioschtrader.entities.Securityaccount;
import grafioschtrader.entities.User;
import grafioschtrader.reports.SecurityGroupByAssetclassSubCategoryReport;
import grafioschtrader.reports.SecurityGroupByAssetclassWithCashReport;
import grafioschtrader.reports.SecurityGroupByBaseReport;
import grafioschtrader.reports.SecurityPositionByCurrencyGrandSummaryReport;
import grafioschtrader.reportviews.securityaccount.SecurityPositionGrandSummary;
import grafioschtrader.repository.SecurityaccountJpaRepository;
import grafioschtrader.types.AssetclassType;
import grafioschtrader.types.SpecialInvestmentInstruments;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping(RequestMappings.SECURITYACCOUNT_MAP)
@Tag(name = Securityaccount.TABNAME, description = "Controller for security account")
public class SecurityaccountResource extends UpdateCreateResource<Securityaccount> {

  @Autowired
  private SecurityaccountJpaRepository securityaccountJpaRepository;

  @Autowired
  private SecurityPositionByCurrencyGrandSummaryReport securityPositionGrandSummaryReport;

  @Autowired
  private SecurityGroupByAssetclassWithCashReport securityGroupByAssetclassWithCashReport;

  @Autowired
  private AutowireCapableBeanFactory beanFactory;

  private final Logger log = LoggerFactory.getLogger(this.getClass());

  /**
   * Delete security account
   *
   * @param idSecuritycashaccount
   * @return
   */
  @DeleteMapping(value = "/{idSecuritycashaccount}", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<Void> deleteSecurityaccount(@PathVariable final Integer idSecuritycashaccount) {
    log.debug("Delete by id Securityaccount : {}", idSecuritycashaccount);
    final User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    securityaccountJpaRepository.deleteSecurityaccount(idSecuritycashaccount, user.getIdTenant());

    return ResponseEntity.ok().build();
  }

  @Override
  protected UpdateCreateJpaRepository<Securityaccount> getUpdateCreateJpaRepository() {
    return securityaccountJpaRepository;
  }

  /// Report tenant
  ///////////////////////////////////////////////////////////////

  @GetMapping(value = "/tenantsecurityaccountsummary/assetclasstypewithcash", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<SecurityPositionGrandSummary> getSecurityGroupByAssetclassWithCashReportByTenant(
      @RequestParam() final boolean includeClosedPosition,
      @RequestParam() @DateTimeFormat(iso = ISO.DATE) final Date untilDate) throws Exception {
    return new ResponseEntity<>(securityGroupByAssetclassWithCashReport
        .getSecurityPositionGrandSummaryIdTenant(includeClosedPosition, untilDate), HttpStatus.OK);
  }

  @GetMapping(value = "/tenantsecurityaccountsummary/currency", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<SecurityPositionGrandSummary> getSecurityPositionSummaryTenant(
      @RequestParam() final boolean includeClosedPosition,
      @RequestParam() @DateTimeFormat(iso = ISO.DATE) final Date untilDate) throws Exception {
    return new ResponseEntity<>(
        securityPositionGrandSummaryReport.getSecurityPositionGrandSummaryIdTenant(includeClosedPosition, untilDate),
        HttpStatus.OK);
  }

  @GetMapping(value = "/tenantsecurityaccountsummary/assetclasstype", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<SecurityPositionGrandSummary> getSecurityPositionSummaryByAssetclassTypeAndTenant(
      @RequestParam() final boolean includeClosedPosition,
      @RequestParam() @DateTimeFormat(iso = ISO.DATE) final Date untilDate) throws Exception {

    return new ResponseEntity<>(
        getDynamicGroupReport(AssetclassType.class, SecurityGroupByBaseReport.ASSETCLASS_CATEGORY_FIELD_NAME)
            .getSecurityPositionGrandSummaryIdTenant(includeClosedPosition, untilDate),
        HttpStatus.OK);
  }

  @GetMapping(value = "tenantsecurityaccountsummary/specialinvestmentinstrument", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<SecurityPositionGrandSummary> getSecurityPositionSummaryBySpecInvestInstAndTenant(
      @RequestParam() final boolean includeClosedPosition,
      @RequestParam() @DateTimeFormat(iso = ISO.DATE) final Date untilDate) throws Exception {
    return new ResponseEntity<>(getDynamicGroupReport(SpecialInvestmentInstruments.class,
        SecurityGroupByBaseReport.ASSETCLASS_SPEC_INVEST_INST_FIELD_NAME)
            .getSecurityPositionGrandSummaryIdTenant(includeClosedPosition, untilDate),
        HttpStatus.OK);
  }

  @GetMapping(value = "/tenantsecurityaccountsummary/subcategorynls", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<SecurityPositionGrandSummary> getSecurityPositionSummaryBySubCategoryNLSAndTenant(
      @RequestParam() final boolean includeClosedPosition,
      @RequestParam() @DateTimeFormat(iso = ISO.DATE) final Date untilDate) throws Exception {
    return new ResponseEntity<>(
        getAssetclassSubCategoryReport().getSecurityPositionGrandSummaryIdTenant(includeClosedPosition, untilDate),
        HttpStatus.OK);
  }

  @GetMapping(value = "tenantsecurityaccountsummary/idassetclass", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<SecurityPositionGrandSummary> getSecurityPositionSummaryByAssetclassAndTenant(
      @RequestParam() final boolean includeClosedPosition,
      @RequestParam() @DateTimeFormat(iso = ISO.DATE) final Date untilDate) throws Exception {
    return new ResponseEntity<>(
        getDynamicGroupReport(Integer.class, SecurityGroupByBaseReport.ASSETCLASS_ID_ASSETCLASS_FIELD_NAME)
            .getSecurityPositionGrandSummaryIdTenant(includeClosedPosition, untilDate),
        HttpStatus.OK);
  }

  /// Report portfolio
  ///////////////////////////////////////////////////////////////
  @GetMapping(value = "/{idPortfolio}/portfoliosecurityaccountsummary/currency", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<SecurityPositionGrandSummary> getSecurityPositionSummaryPortfolio(
      @PathVariable final Integer idPortfolio, @RequestParam() final boolean includeClosedPosition,
      @RequestParam() @DateTimeFormat(iso = ISO.DATE) final Date untilDate) throws Exception {
    return new ResponseEntity<>(securityPositionGrandSummaryReport
        .getSecurityPositionGrandSummaryIdPortfolio(idPortfolio, includeClosedPosition, untilDate), HttpStatus.OK);
  }

  @GetMapping(value = "/{idPortfolio}/portfoliosecurityaccountsummary/assetclasstype", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<SecurityPositionGrandSummary> getSecurityPositionSummaryByAssetclassTypeAndPortfolio(
      @PathVariable final Integer idPortfolio, @RequestParam() final boolean includeClosedPosition,
      @RequestParam() @DateTimeFormat(iso = ISO.DATE) final Date untilDate) throws Exception {
    return new ResponseEntity<>(
        getDynamicGroupReport(AssetclassType.class, SecurityGroupByBaseReport.ASSETCLASS_CATEGORY_FIELD_NAME)
            .getSecurityPositionGrandSummaryIdPortfolio(idPortfolio, includeClosedPosition, untilDate),
        HttpStatus.OK);
  }

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

  @GetMapping(value = "/{idPortfolio}/portfoliosecurityaccountsummary/subcategorynls", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<SecurityPositionGrandSummary> getSecurityPositionSummaryBySubCategoryNLSAndPortfolio(
      @PathVariable final Integer idPortfolio, @RequestParam() final boolean includeClosedPosition,
      @RequestParam() @DateTimeFormat(iso = ISO.DATE) final Date untilDate) throws Exception {
    return new ResponseEntity<>(getAssetclassSubCategoryReport().getSecurityPositionGrandSummaryIdPortfolio(idPortfolio,
        includeClosedPosition, untilDate), HttpStatus.OK);
  }

  @GetMapping(value = "/{idPortfolio}/portfoliosecurityaccountsummary/idassetclass", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<SecurityPositionGrandSummary> getSecurityPositionSummaryByAssetclassAndPortfolio(
      @PathVariable final Integer idPortfolio, @RequestParam() final boolean includeClosedPosition,
      @RequestParam() @DateTimeFormat(iso = ISO.DATE) final Date untilDate) throws Exception {
    return new ResponseEntity<>(
        getDynamicGroupReport(Integer.class, SecurityGroupByBaseReport.ASSETCLASS_ID_ASSETCLASS_FIELD_NAME)
            .getSecurityPositionGrandSummaryIdPortfolio(idPortfolio, includeClosedPosition, untilDate),
        HttpStatus.OK);
  }

  /// Report security account
  ///////////////////////////////////////////////////////////////

  @GetMapping(value = "/{idSecurityaccount}/securityaccountsummary/currency", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<SecurityPositionGrandSummary> getPostionSummarySecurityaccount(
      @PathVariable final Integer idSecurityaccount, @RequestParam() final boolean includeClosedPosition,
      @RequestParam() @DateTimeFormat(iso = ISO.DATE) final Date untilDate) throws Exception {
    return new ResponseEntity<>(securityPositionGrandSummaryReport.getSecurityPositionGrandSummaryIdSecurityaccount(
        idSecurityaccount, includeClosedPosition, untilDate), HttpStatus.OK);
  }

  @GetMapping(value = "/{idSecurityaccount}/securityaccountsummary/assetclasstype", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<SecurityPositionGrandSummary> getPostionSummarySecurityaccountByAssetclassTypeAndSecurityaccount(
      @PathVariable final Integer idSecurityaccount, @RequestParam() final boolean includeClosedPosition,
      @RequestParam() @DateTimeFormat(iso = ISO.DATE) final Date untilDate) throws Exception {
    return new ResponseEntity<>(
        getDynamicGroupReport(AssetclassType.class, SecurityGroupByBaseReport.ASSETCLASS_CATEGORY_FIELD_NAME)
            .getSecurityPositionGrandSummaryIdSecurityaccount(idSecurityaccount, includeClosedPosition, untilDate),
        HttpStatus.OK);
  }

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

  @GetMapping(value = "/{idSecurityaccount}/securityaccountsummary/subcategorynls", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<SecurityPositionGrandSummary> getSecurityPositionSummaryBySubCategoryNLSAndSecurityaccount(
      @PathVariable final Integer idSecurityaccount, @RequestParam() final boolean includeClosedPosition,
      @RequestParam() @DateTimeFormat(iso = ISO.DATE) final Date untilDate) throws Exception {
    return new ResponseEntity<>(getAssetclassSubCategoryReport().getSecurityPositionGrandSummaryIdSecurityaccount(
        idSecurityaccount, includeClosedPosition, untilDate), HttpStatus.OK);
  }

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

}
