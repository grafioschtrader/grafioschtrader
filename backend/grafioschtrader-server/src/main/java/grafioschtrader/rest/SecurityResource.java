package grafioschtrader.rest;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import java.lang.reflect.InvocationTargetException;
import java.text.ParseException;
import java.time.LocalDate;
import java.util.Date;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.format.annotation.DateTimeFormat.ISO;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import grafioschtrader.connector.instrument.IFeedConnector;
import grafioschtrader.dto.HisotryqouteLinearFilledSummary;
import grafioschtrader.dto.InstrumentStatisticsResult;
import grafioschtrader.dto.SecurityCurrencypairDerivedLinks;
import grafioschtrader.entities.Auditable;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.User;
import grafioschtrader.priceupdate.historyquote.HistoryquoteQualityService;
import grafioschtrader.reports.SecruityTransactionsReport;
import grafioschtrader.reports.SecruityTransactionsReport.SecruityTransactionsReportOptions;
import grafioschtrader.reportviews.historyquotequality.HistoryquoteQualityGrouped;
import grafioschtrader.reportviews.historyquotequality.HistoryquoteQualityHead;
import grafioschtrader.reportviews.historyquotequality.HistoryquoteQualityIds;
import grafioschtrader.reportviews.historyquotequality.IHistoryquoteQualityWithSecurityProp;
import grafioschtrader.reportviews.securityaccount.SecurityOpenPositionPerSecurityaccount;
import grafioschtrader.reportviews.transaction.SecurityTransactionSummary;
import grafioschtrader.repository.SecurityDerivedLinkJpaRepository;
import grafioschtrader.repository.SecurityJpaRepository;
import grafioschtrader.search.SecuritycurrencySearch;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping(RequestMappings.SECURITY_MAP)
@Tag(name = Security.TABNAME, description = "Controller for security")
public class SecurityResource extends UpdateCreateDeleteAuditResource<Security> {

  @Autowired
  private SecurityJpaRepository securityJpaRepository;

  @Autowired
  private SecruityTransactionsReport secruityTransactionsReport;

  @Autowired
  private HistoryquoteQualityService historyquoteQuality;

  @Autowired
  private SecurityDerivedLinkJpaRepository securityDerivedLinkJpaRepository;

  @Operation(summary = "Returns a security by its Id", description = "Only public securities and the user private security will be returned", tags = {
      Security.TABNAME })
  @GetMapping(value = "/{idSecuritycurrency}", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<Security> getSecurityByIdSecuritycurrency(@PathVariable final Integer idSecuritycurrency) {
    final User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    Security security = securityJpaRepository
        .findByIdTenantPrivateIsNullOrIdTenantPrivateAndIdSecuritycurrency(idSecuritycurrency, user.getIdTenant());
    return new ResponseEntity<>(security, HttpStatus.OK);
  }

  @GetMapping(value = "/algounused/{idAlgoAssetclassSecurity}", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<List<Security>> getSecuritiesByIdAssetclass(
      @PathVariable final Integer idAlgoAssetclassSecurity) {
    final User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    List<Security> securities = securityJpaRepository.getUnusedSecurityForAlgo(user.getIdTenant(),
        idAlgoAssetclassSecurity);
    return new ResponseEntity<>(securities, HttpStatus.OK);
  }

  @Operation(summary = "Returns the a list of security accounts which holds a certain security. Also the the security is included.", description = "Useful when choosing the security account for transactions on open positions", tags = {
      Security.TABNAME })
  @GetMapping(value = "/{idSecuritycurrency}/date/{dateString}/{before}", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<SecurityOpenPositionPerSecurityaccount> getOpenPositionByIdSecuritycurrencyAndIdTenant(
      @Parameter(description = "The ID of security", required = true) @PathVariable final Integer idSecuritycurrency,
      @Parameter(description = "Date in format yyyyMMddHHmm for which the holdings requreid", required = true) @PathVariable final String dateString,
      @Parameter(description = "True if the holdins required before this existing transaction", required = true) @PathVariable final boolean before,
      @Parameter(description = "ID of transaction when exists otherwise null", required = true) @RequestParam(required = false) final Integer idTransaction,
      @Parameter(description = "ID of the open margin position", required = true) @RequestParam(required = false) final Integer idOpenMarginTransaction)
      throws ParseException {

    final User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    final SecurityOpenPositionPerSecurityaccount sopps = secruityTransactionsReport
        .getOpenPositionByIdTenantAndIdSecuritycurrency(user.getIdTenant(), idSecuritycurrency, dateString, before,
            idTransaction, idOpenMarginTransaction);
    return new ResponseEntity<>(sopps, HttpStatus.OK);
  }

  @Operation(summary = "Returns all securities of a watchlist which are tradable", description = "", tags = {
      Security.TABNAME })
  @GetMapping(value = "/watchlist/{idWatchlist}", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<List<Security>> getTradableSecuritiesByTenantAndIdWatschlist(
      @PathVariable final Integer idWatchlist) throws ParseException {
    final List<Security> securities = securityJpaRepository.getTradableSecuritiesByTenantAndIdWatschlist(idWatchlist);
    return new ResponseEntity<>(securities, HttpStatus.OK);
  }

  /*
   * @GetMapping(value = "/active/{dateString}", produces =
   * APPLICATION_JSON_VALUE) public ResponseEntity<List<Security>>
   * findActiveSecurityOrderByName(@PathVariable final String dateString) throws
   * ParseException { final List<Security> securities =
   * securityJpaRepository.findByActiveToDateGreaterThanEqualOrderByName(
   * dateString); return new ResponseEntity<>(securities, HttpStatus.OK); }
   */

  @Operation(summary = "Returns all connectors of data provider with it supported capabilities", description = "", tags = {
      Security.TABNAME })
  @GetMapping(value = "/feedConnectors", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<List<IFeedConnector>> getFeedConnectors() {
    return new ResponseEntity<>(securityJpaRepository.getFeedConnectors(false), HttpStatus.OK);
  }

  @Override
  protected UpdateCreateJpaRepository<Security> getUpdateCreateJpaRepository() {
    return securityJpaRepository;
  }

  // TODO not used yet
  @GetMapping(value = "/reloadhistoryquote/{idSecuritycurrency}", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<String> reloadAsyncFullHistoryquote(@PathVariable final Integer idSecuritycurrency) {
    securityJpaRepository.reloadAsyncFullHistoryquoteExternal(idSecuritycurrency);
    return new ResponseEntity<>("Take many minutes", HttpStatus.OK);
  }

  @Operation(summary = "Returns all transactions for specified security", description = "Chart is shown with split adjusted data, for that reason transactions data is also adjusted to match it charts historical data", tags = {
      Security.TABNAME })
  @GetMapping(value = "/tenantsecurity/{idSecuritycurrency}", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<SecurityTransactionSummary> getTransactionsByIdTenantAndIdSecurity(
      @PathVariable final Integer idSecuritycurrency,
      @Parameter(description = "True if it is a chart, means adjust data", required = true) @RequestParam() final boolean forchart) {

    final User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    final Set<SecruityTransactionsReportOptions> secruityTransactionsReportOptions = forchart
        ? EnumSet.of(SecruityTransactionsReportOptions.QUTATION_SPLIT_CORRECTION)
        : EnumSet.noneOf(SecruityTransactionsReportOptions.class);
    return new ResponseEntity<>(secruityTransactionsReport.getTransactionsByIdTenantAndIdSecurityAndClearSecurity(
        user.getIdTenant(), idSecuritycurrency, new Date(), secruityTransactionsReportOptions), HttpStatus.OK);
  }

  @Operation(summary = "Returns the transactions for specified security in specified portfolio", description = "Chart is shown with split adjusted data, for that reason transactions data is also adjusted to match it charts historical data", tags = {
      Security.TABNAME })
  @GetMapping(value = "/{idPortfolio}/portfoliosecurity/{idSecuritycurrency}", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<SecurityTransactionSummary> getTransactionsByIdPortfolioAndIdSecurity(
      @PathVariable final Integer idPortfolio, @PathVariable final Integer idSecuritycurrency,
      @Parameter(description = "True if it is a chart, means adjust data", required = true) @RequestParam() final boolean forchart) {

    final Set<SecruityTransactionsReportOptions> secruityTransactionsReportOptions = forchart
        ? EnumSet.of(SecruityTransactionsReportOptions.QUTATION_SPLIT_CORRECTION)
        : EnumSet.noneOf(SecruityTransactionsReportOptions.class);
    return new ResponseEntity<>(secruityTransactionsReport.getTransactionsByIdPortfolioAndIdSecurityAndClearSecurity(
        idPortfolio, idSecuritycurrency, new Date(), secruityTransactionsReportOptions), HttpStatus.OK);
  }

  @Operation(summary = "Returns the transactions for specified security in specified security account", description = "Chart is shown with split adjusted data, for that reason transactions data is also adjusted to match it charts historical data", tags = {
      Security.TABNAME })
  @GetMapping(value = "securityaccountsecurity/{idSecuritycurrency}", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<SecurityTransactionSummary> getTransactionsByIdSecurityaccountsAndIdSecurity(
      @PathVariable final Integer idSecuritycurrency, @RequestParam() final List<Integer> idsSecurityaccount,
      @Parameter(description = "True if it is a chart, means adjust data", required = true) @RequestParam() final boolean forchart) {

    final Set<SecruityTransactionsReportOptions> secruityTransactionsReportOptions = forchart
        ? EnumSet.of(SecruityTransactionsReportOptions.QUTATION_SPLIT_CORRECTION)
        : EnumSet.noneOf(SecruityTransactionsReportOptions.class);
    return new ResponseEntity<>(
        secruityTransactionsReport.getTransactionsByIdSecurityaccountsAndIdSecurityAndClearSecurity(idsSecurityaccount,
            idSecuritycurrency, new Date(), secruityTransactionsReportOptions),
        HttpStatus.OK);
  }

  @Operation(summary = "Searches securities  by a s search criteria", description = "", tags = { Security.TABNAME })
  @GetMapping(value = "/search", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<List<Security>> searchByCriteria(final SecuritycurrencySearch securitycurrencySearch) {
    return new ResponseEntity<>(securityJpaRepository.searchByCriteria(securitycurrencySearch), HttpStatus.OK);
  }

  @Operation(summary = "Returns the completeness summary of historical EOD data", description = "Securities are not included", tags = {
      Security.TABNAME })
  @GetMapping(value = "/historyquotequality", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<HistoryquoteQualityHead> getHistoryquoteQualityHead(
      @Parameter(description = "Data is grouped by stockexchange or connector", required = true) @RequestParam() final HistoryquoteQualityGrouped groupedBy) {
    return new ResponseEntity<>(securityJpaRepository.getHistoryquoteQualityHead(groupedBy), HttpStatus.OK);
  }

  @Operation(summary = "Returns the completeness summary of historical EOD data", description = "", tags = {
      Security.TABNAME })
  @GetMapping(value = "/historyquotequalityids", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<List<IHistoryquoteQualityWithSecurityProp>> getHistoryquoteQualityByIds(
      final HistoryquoteQualityIds hqi) {
    return new ResponseEntity<>(securityJpaRepository.getHistoryquoteQualityByIds(hqi.idConnectorHistory,
        hqi.idStockexchange, hqi.categoryType, hqi.specialInvestmentInstrument), HttpStatus.OK);
  }

  @Operation(summary = "Some historical quotes of a specified security may missing some days. The missing days are determined using the trading calendar and filled with data.", description = "The gap/s is filled linar", tags = {
      Security.TABNAME })
  @PostMapping(value = "/{idSecuritycurreny}/fillgapes", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<HisotryqouteLinearFilledSummary> fillHistoryquoteGapsLinear(
      @PathVariable() Integer idSecuritycurreny,
      @Parameter(description = "True if move existing weekend quotes to a missing friday", required = true) @RequestBody final boolean moveWeekendToFriday) {
    return new ResponseEntity<>(historyquoteQuality.fillHistoryquoteGapsLinear(idSecuritycurreny, moveWeekendToFriday),
        HttpStatus.OK);
  }

  @Operation(summary = "Return of annual return over specified periods.", description = "The result in currency of the instrument and the main currency of the client.", tags = {
      Security.TABNAME })
  @GetMapping(value = "/{idSecuritycurrency}/securitystatistics", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<InstrumentStatisticsResult> getAnnualisedPerformance(
      @PathVariable final Integer idSecuritycurrency, @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) final LocalDate dateFrom, 
      @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate dateTo)
      throws IllegalAccessException, InvocationTargetException, NoSuchMethodException {
    return new ResponseEntity<>(securityJpaRepository.getSecurityStatisticsReturnResult(idSecuritycurrency, dateFrom, dateTo),
        HttpStatus.OK);
  }

  @Operation(summary = "For a derived instrument it returns base instruments", 
      description = "", tags = {Security.TABNAME })
  @GetMapping(value = "/{idSecuritycurrency}/derivedlinks", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<SecurityCurrencypairDerivedLinks> getDerivedInstrumensLinksForSecurity(
      @PathVariable final Integer idSecuritycurrency) {
    final User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    return new ResponseEntity<>(
        securityDerivedLinkJpaRepository.getDerivedInstrumentsLinksForSecurity(idSecuritycurrency, user.getIdTenant()),
        HttpStatus.OK);
  }

  @Override
  protected boolean hasRightsForEditingEntity(User user, Security newEntity, Security existingEntity,
      Auditable parentEntity) {
    return securityJpaRepository.checkUserCanChangeDerivedFields(user, newEntity, existingEntity);
  }

}
