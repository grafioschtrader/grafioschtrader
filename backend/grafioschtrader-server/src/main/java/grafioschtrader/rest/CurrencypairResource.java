package grafioschtrader.rest;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import java.text.ParseException;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import grafiosch.entities.User;
import grafiosch.rest.UpdateCreateJpaRepository;
import grafiosch.rest.UpdateCreateResource;
import grafioschtrader.GlobalConstants;
import grafioschtrader.connector.instrument.IFeedConnector;
import grafioschtrader.dto.CrossRateRequest;
import grafioschtrader.dto.CrossRateResponse;
import grafioschtrader.dto.ISecuritycurrencyIdDateClose;
import grafioschtrader.entities.Currencypair;
import grafioschtrader.reportviews.currencypair.CurrencypairWithHistoryquote;
import grafioschtrader.reportviews.currencypair.CurrencypairWithTransaction;
import grafioschtrader.repository.CurrencypairJpaRepository;
import grafioschtrader.repository.HistoryquoteJpaRepository;
import grafioschtrader.repository.TransactionJpaRepository;
import grafioschtrader.search.SecuritycurrencySearch;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping(RequestGTMappings.CURRENCYPAIR_MAP)
@Tag(name = Currencypair.TABNAME, description = "Controller for the currency pair")
public class CurrencypairResource extends UpdateCreateResource<Currencypair> {

  @Autowired
  private CurrencypairJpaRepository currencypairJpaRepository;

  @Autowired
  private HistoryquoteJpaRepository historyquoteJpaRepository;

  @Autowired
  private TransactionJpaRepository transactionJpaRepository;

  @Operation(summary = "Get currency pair by security currency ID", description = """
      Retrieves a currency pair associated with the given currency ID. Returns null if no matching currency pair is found.
      """, tags = {
      Currencypair.TABNAME })
  @GetMapping(value = "/{idSecuritycurrency}", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<Currencypair> getCurrencypairByIdSecuritycurrency(
      @PathVariable final Integer idSecuritycurrency) {
    return new ResponseEntity<>(currencypairJpaRepository.findById(idSecuritycurrency).orElse(null), HttpStatus.OK);
  }

  @Operation(summary = "Returns all currency pairs", description = "", tags = { Currencypair.TABNAME })
  @GetMapping(produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<List<Currencypair>> getAllCurrencypairs() {
    return new ResponseEntity<>(currencypairJpaRepository.findAll(), HttpStatus.OK);
  }

  @Operation(summary = "Return of an existing or newly created currency pair.", description = "If the currency pair does not exist yet, it will be created. Possibly helpful for transactions.", tags = {
      Currencypair.TABNAME })
  @GetMapping(value = "/{baseCurrency}/{quoteCurrency}", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<Currencypair> findOrCreateCurrencypairByFromAndToCurrency(
      @Parameter(description = "Base currency as three-letter ISO currency code", required = true) @PathVariable final String baseCurrency,
      @Parameter(description = "Quote currency as three-letter ISO currency code", required = true) @PathVariable final String quoteCurrency) {
    return new ResponseEntity<>(
        currencypairJpaRepository.findOrCreateCurrencypairByFromAndToCurrency(baseCurrency, quoteCurrency, true),
        HttpStatus.OK);
  }

  @Operation(summary = "Get currency pair with the most recent historical quote up to a given date", description = """
      Retrieves a currency pair based on the provided base and quote currency codes, along with the most
      recent available closing price on or before the specified date.
      The historical quote is determined using the latest available date (MAX(date)) less than or equal
      to the given date from historical quote data. If no matching currency pair is found, only null
      quote data is returned.""", tags = { Currencypair.TABNAME })
  @GetMapping(value = "/{baseCurrency}/{quoteCurrency}/{dateString}", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<CurrencypairWithHistoryquote> getCurrencypairWithHistoryquoteByIdSecuritycurrencyAndDate(
      @PathVariable final String baseCurrency, @PathVariable final String quoteCurrency,
      @PathVariable final String dateString) throws ParseException {
    ISecuritycurrencyIdDateClose historyquote = null;
    final Currencypair currencypair = this.currencypairJpaRepository.findByFromCurrencyAndToCurrency(baseCurrency,
        quoteCurrency);
    if (currencypair != null) {
      historyquote = historyquoteJpaRepository.getCertainOrOlderDayInHistorquoteByIdSecuritycurrency(
          currencypair.getIdSecuritycurrency(), dateString, false);
    }
    final CurrencypairWithHistoryquote currencypairWithHistoryquote = new CurrencypairWithHistoryquote(currencypair,
        historyquote);
    return new ResponseEntity<>(currencypairWithHistoryquote, HttpStatus.OK);
  }

  @Operation(summary = "Returns all connectors of data provider with it supported capabilities", description = "", tags = {
      Currencypair.TABNAME })
  @GetMapping(value = "/feedConnectors", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<List<IFeedConnector>> getFeedConnectors() {
    return new ResponseEntity<>(currencypairJpaRepository.getFeedConnectors(true), HttpStatus.OK);
  }

  /*
   * // NOT USED
   *
   * @GetMapping(value = "/usedCurrencypairs", produces = APPLICATION_JSON_VALUE) public
   * ResponseEntity<List<Currencypair>> getUsedCurrencypairs() { return new
   * ResponseEntity<>(currencypairJpaRepository.getAllUsedCurrencypairs(), HttpStatus.OK); }
   */

  @Override
  protected UpdateCreateJpaRepository<Currencypair> getUpdateCreateJpaRepository() {
    return currencypairJpaRepository;
  }

  @Operation(summary = "Searches securities by a search criteria", description = "", tags = { Currencypair.TABNAME })
  @GetMapping(value = "/search", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<List<Currencypair>> searchByCriteria(final SecuritycurrencySearch securitycurrencySearch) {
    return new ResponseEntity<>(currencypairJpaRepository.searchByCriteria(securitycurrencySearch), HttpStatus.OK);
  }

  @Operation(summary = "Get required currency pairs for cross rate calculation", description = """
      Returns all necessary currency pairs and their latest close prices for converting
      security currencies via the tenant’s main currency. Supports indirect paths with
      up to three currencies.""", tags = { Currencypair.TABNAME })
  @GetMapping(value = "/crossrate", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<CrossRateResponse> getCurrencypairForCrossRate(final CrossRateRequest crossRateRequest) {
    return new ResponseEntity<>(currencypairJpaRepository.getCurrencypairForCrossRate(crossRateRequest), HttpStatus.OK);
  }

  ////////////////////////////////////////////////////////////
  // User depended Request
  ////////////////////////////////////////////////////////////
  @Operation(summary = "Gel all used currency pairs in transactions. That means currency pairs which werse used in all transactions.", description = "A client could merge transactions with this currencypairs", tags = {
      Currencypair.TABNAME })
  @GetMapping(value = "/tenant", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<List<Currencypair>> getCurrencypairInTransactionByTenant() {
    final Integer idTenant = ((User) SecurityContextHolder.getContext().getAuthentication().getDetails()).getIdTenant();
    return new ResponseEntity<>(currencypairJpaRepository.getCurrencypairInTransactionByTenant(idTenant),
        HttpStatus.OK);
  }

  @Operation(summary = "Returns all transactions for a certain currency pair of a tenant.", description = "", tags = {
      Currencypair.TABNAME })
  @GetMapping(value = "/tenant/{idCurrencypair}", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<CurrencypairWithTransaction> findOrCreateCurrencypairByFromAndToCurrency(
      @PathVariable final Integer idCurrencypair,
      @Parameter(description = "True it will add some transactions", required = true) @RequestParam() final boolean forchart) {
    final User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    return new ResponseEntity<>(
        transactionJpaRepository.getTransactionForCurrencyPair(user.getIdTenant(), idCurrencypair, forchart),
        HttpStatus.OK);
  }

  @Operation(summary = "Get currency pairs used in transactions of a portfolio", description = """
      Returns all currency pairs that have been involved in transactions within the specified portfolio,
      filtered by the tenant associated with the current authenticated user.
      The response includes only those currency pairs that are actually used in transactions and
      belong to the portfolio identified by the provided ID.
      """, tags = { Currencypair.TABNAME })
  @GetMapping(value = "/{idPortfolio}/portfolio", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<List<Currencypair>> getTransactionCurrencypairsByPortfolioId(
      @PathVariable final Integer idPortfolio) {
    final Integer idTenant = ((User) SecurityContextHolder.getContext().getAuthentication().getDetails()).getIdTenant();
    return new ResponseEntity<>(
        currencypairJpaRepository.getCurrencypairInTransactionByPortfolioId(idPortfolio, idTenant), HttpStatus.OK);
  }

  @Override
  protected String getPrefixEntityLimit() {
    return GlobalConstants.GT_LIMIT_DAY;
  }

}
