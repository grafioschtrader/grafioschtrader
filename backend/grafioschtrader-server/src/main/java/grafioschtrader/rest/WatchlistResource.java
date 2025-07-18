
package grafioschtrader.rest;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import java.util.List;
import java.util.concurrent.ExecutionException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import grafiosch.dto.TenantLimit;
import grafiosch.entities.User;
import grafiosch.rest.UpdateCreateDeleteWithTenantJpaRepository;
import grafiosch.rest.UpdateCreateDeleteWithTenantResource;
import grafioschtrader.GlobalConstants;
import grafioschtrader.dto.IntraHistoricalWatchlistProblem;
import grafioschtrader.dto.WatchlistSecurityStatus;
import grafioschtrader.entities.Watchlist;
import grafioschtrader.reports.WatchlistReport;
import grafioschtrader.reportviews.securitycurrency.SecuritycurrencyGroup;
import grafioschtrader.reportviews.securitycurrency.SecuritycurrencyLists;
import grafioschtrader.reportviews.securitycurrency.SecuritycurrencyUDFGroup;
import grafioschtrader.repository.WatchlistJpaRepository;
import grafioschtrader.search.SecuritycurrencySearch;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping(RequestGTMappings.WATCHLIST_MAP)
@Tag(name = Watchlist.TABNAME, description = "Controller for watchlist")
public class WatchlistResource extends UpdateCreateDeleteWithTenantResource<Watchlist> {

  @Autowired
  private WatchlistJpaRepository watchlistJpaRepository;

  @Autowired
  private WatchlistReport watchlistReport;

  public WatchlistResource() {
    super(Watchlist.class);
  }

  @Operation(summary = "Returns all watchlists without securities", description = "Empty watchlist check", tags = {
      Watchlist.TABNAME })
  @GetMapping(value = "/tenant", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<List<Watchlist>> getWatchlistsByTenant() {
    final User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    return new ResponseEntity<>(watchlistJpaRepository.findByIdTenantOrderByName(user.getIdTenant()), HttpStatus.OK);
  }

  @Override
  protected UpdateCreateDeleteWithTenantJpaRepository<Watchlist> getUpdateCreateJpaRepository() {
    return watchlistJpaRepository;
  }

  @Operation(summary = "Returns if watchlists has security or not", description = "Empty watchlist check", tags = {
      Watchlist.TABNAME })
  @GetMapping(value = "/hassecurity", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<List<WatchlistSecurityStatus>> getWatchlistsOfTenantHasSecurity() {
      final Integer idTenant = ((User) SecurityContextHolder.getContext().getAuthentication().getDetails()).getIdTenant();
      return new ResponseEntity<>(watchlistJpaRepository.watchlistsOfTenantHasSecurity(idTenant), HttpStatus.OK);
  }

  @Operation(summary = "Returns Id's of watchlist which contains the specified security", description = "May be used for moving a security to another wachlist, a security can only once exist in a watchlist", tags = {
      Watchlist.TABNAME })
  @GetMapping(value = "/existssecurity/{idSecuritycurrency}", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<List<Integer>> getAllWatchlistsWithSecurityByIdSecuritycurrency(
      @PathVariable final Integer idSecuritycurrency) {
    final Integer idTenant = ((User) SecurityContextHolder.getContext().getAuthentication().getDetails()).getIdTenant();
    return new ResponseEntity<>(
        watchlistJpaRepository.getAllWatchlistsWithSecurityByIdSecuritycurrency(idTenant, idSecuritycurrency),
        HttpStatus.OK);
  }

  @Operation(summary = "Searches securities or currency pairs which are not in the specified watchlist by a s search criteria", description = "", tags = {
      Watchlist.TABNAME })
  @GetMapping(value = "/{idWatchlist}/search", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<SecuritycurrencyLists> searchByCriteria(
      @Parameter(description = "Id of watchlist", required = true) @PathVariable final Integer idWatchlist,
      @Parameter(description = "Search criteria", required = true) final SecuritycurrencySearch securitycurrencySearch) {
    return new ResponseEntity<>(watchlistJpaRepository.searchByCriteria(idWatchlist, securitycurrencySearch),
        HttpStatus.OK);
  }

  @Operation(summary = "Attempts to update the intraday quote data even though retry counter has reached its limit", description = "", tags = {
      Watchlist.TABNAME })
  @GetMapping(value = "{idWatchlist}/tryuptodateintradata", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<SecuritycurrencyLists> tryUpToIntradayDataWhenRetryIntraLoadGreaterThan0(
      @PathVariable final Integer idWatchlist) {
    return new ResponseEntity<>(watchlistJpaRepository.tryUpToIntradayDataWhenRetryIntraLoadGreaterThan0(idWatchlist),
        HttpStatus.OK);
  }

  @Operation(summary = "Attempts to update the historical quote data even though retry counter has reached its limit", description = "", tags = {
      Watchlist.TABNAME })
  @GetMapping(value = "{idWatchlist}/tryuptodatehistoricaldata", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<SecuritycurrencyLists> tryUpToDateHistoricalDataWhenRetryHistoryLoadGreaterThan0(
      @PathVariable final Integer idWatchlist) {
    return new ResponseEntity<>(
        watchlistJpaRepository.tryUpToDateHistoricalDataWhenRetryHistoryLoadGreaterThan0(idWatchlist), HttpStatus.OK);
  }

  @Operation(summary = "Return the two limits for instruments in watchlist/s", description = "One limits the number of securities and currency pairs in a wachtlist "
      + "and the other how many securities or currency pairs can watched all together", tags = { Watchlist.TABNAME })
  @GetMapping(value = "{idWatchlist}/limitsecuritiescurrencies", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<TenantLimit[]> getSecuritiesCurrenciesWatchlistLimits(@PathVariable final Integer idWatchlist) {
    return new ResponseEntity<>(watchlistJpaRepository.getSecuritiesCurrenciesWachlistLimits(idWatchlist),
        HttpStatus.OK);
  }

  /////////////////////////////////////////////////////////////
  // Modify content of Watchlist - Report
  /////////////////////////////////////////////////////////////
  @PutMapping(value = "{idWatchlist}/addSecuritycurrency", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<Watchlist> addSecuritycurrenciesToWatchlist(@PathVariable final Integer idWatchlist,
      @RequestBody final SecuritycurrencyLists securitycurrencyLists) {
    return ResponseEntity.ok()
        .body(watchlistJpaRepository.addSecuritycurrenciesToWatchlist(idWatchlist, securitycurrencyLists));
  }

  @PutMapping(value = "{idWatchlistSource}/moveto/{idWatchlistTarget}/securitycurrency/{idSecuritycurrency}", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<Boolean> moveSecuritycurrency(@PathVariable final Integer idWatchlistSource,
      @PathVariable final Integer idWatchlistTarget, @PathVariable final Integer idSecuritycurrency) {
    return ResponseEntity.ok()
        .body(watchlistJpaRepository.moveSecuritycurrency(idWatchlistSource, idWatchlistTarget, idSecuritycurrency));
  }

  @Operation(summary = "Remove a currency pair from specified security and deletet it", description = "It can only deleted if the security is not referenced", tags = {
      Watchlist.TABNAME })
  @DeleteMapping(value = "{idWatchlist}/removeDeleteSecurity/{idSecuritycurrency}", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<Watchlist> removeSecurityFromWatchlistAndDelete(@PathVariable final Integer idWatchlist,
      @PathVariable final Integer idSecuritycurrency) {
    return ResponseEntity.ok()
        .body(watchlistJpaRepository.removeSecurityFromWatchlistAndDelete(idWatchlist, idSecuritycurrency));
  }

  @Operation(summary = "Adding active instruments with price data problems. Both intraday and historical price data.", description = "The watchlist must not yet contain any instruments.", tags = {
      Watchlist.TABNAME })
  @PatchMapping(value = "{idWatchlist}/pricedataproblems", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<Watchlist> addInstrumentsWithPriceDataProblems(@PathVariable final Integer idWatchlist,
      @Valid @RequestBody final IntraHistoricalWatchlistProblem ihwp) {
    return new ResponseEntity<>(watchlistJpaRepository.addInstrumentsWithPriceDataProblems(idWatchlist, ihwp),
        HttpStatus.OK);
  }

  @Operation(summary = "Remove a currency pair from specified watchlist and deletet it", description = "It can only deleted if the currency pair is not referenced", tags = {
      Watchlist.TABNAME })
  @DeleteMapping(value = "{idWatchlist}/removeDeleteCurrencypair/{idSecuritycurrency}", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<Watchlist> removeCurrencypairFromWatchlistAndDelete(@PathVariable final Integer idWatchlist,
      @PathVariable final Integer idSecuritycurrency) {
    return ResponseEntity.ok()
        .body(watchlistJpaRepository.removeCurrencypairFromWatchlistAndDelete(idWatchlist, idSecuritycurrency));
  }

  @Operation(summary = "Remove a security from specified watchlist", description = "", tags = { Watchlist.TABNAME })
  @DeleteMapping(value = "{idWatchlist}/removeSecurity/{idSecuritycurrency}", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<Watchlist> removeSecurityFromWatchlist(@PathVariable final Integer idWatchlist,
      @PathVariable final Integer idSecuritycurrency) {
    return ResponseEntity.ok()
        .body(watchlistJpaRepository.removeSecurityFromWatchlist(idWatchlist, idSecuritycurrency));
  }

  @Operation(summary = "Remove a currency pair from specified watchlist", description = "", tags = {
      Watchlist.TABNAME })
  @DeleteMapping(value = "{idWatchlist}/removeCurrencypair/{idSecuritycurrency}", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<Watchlist> removeCurrencypairFromWatchlist(@PathVariable final Integer idWatchlist,
      @PathVariable final Integer idSecuritycurrency) {
    return ResponseEntity.ok()
        .body(watchlistJpaRepository.removeCurrencypairFromWatchlist(idWatchlist, idSecuritycurrency));
  }

  @Operation(summary = "Remove many currency pairs and or securities from watchlist", description = "", tags = {
      Watchlist.TABNAME })
  @DeleteMapping(value = "{idWatchlist}/removemultiple", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<Integer> removeMultipleFromWatchlist(@PathVariable final Integer idWatchlist,
      @RequestParam() final List<Integer> idsSecuritycurrencies) {
    return new ResponseEntity<>(watchlistJpaRepository.removeMultipleFromWatchlist(idWatchlist, idsSecuritycurrencies),
        HttpStatus.OK);
  }

  /////////////////////////////////////////////////////////////
  // Get Watchlist - Report
  /////////////////////////////////////////////////////////////

  @Operation(summary = "Returns the content of watchlist without updated prices", description = "First call this to get the content of a watchlist fast", tags = {
      Watchlist.TABNAME })
  @GetMapping(value = "/{idWatchlist}", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<SecuritycurrencyGroup> getWatchlistwithoutUpdate(@PathVariable final Integer idWatchlist) {
    return new ResponseEntity<>(watchlistReport.getWatchlistWithoutUpdate(idWatchlist), HttpStatus.OK);
  }

  @Operation(summary = "Returns the content of watchlist with updated prices", description = "This operation can take many seconds", tags = {
      Watchlist.TABNAME })
  @GetMapping(value = "/{idWatchlist}/quote", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<SecuritycurrencyGroup> updateLastPriceByIdWatchlist(@PathVariable final Integer idWatchlist,
      @RequestParam() final Integer daysTimeFrame) {
    final User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    return new ResponseEntity<>(
        watchlistReport.getWatchlistwithPeriodPerformance(idWatchlist, user.getIdTenant(), daysTimeFrame),
        HttpStatus.OK);
  }

  @Operation(summary = "Returns the content of a watchlist with the setting of youngest date of history quote. "
      + "This should help to detect non working historical data feeds.", description = "", tags = { Watchlist.TABNAME })
  @GetMapping(value = "/{idWatchlist}/maxhistoryquote", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<SecuritycurrencyGroup> getWatchlistWithoutUpdateAndMaxHistoryquote(
      @PathVariable final Integer idWatchlist) throws InterruptedException, ExecutionException {
    return new ResponseEntity<>(watchlistReport.getWatchlistWithoutUpdateAndMaxHistoryquote(idWatchlist),
        HttpStatus.OK);
  }

  @Operation(summary = "Return of a watchlist with the user-defined data.", tags = { Watchlist.TABNAME })
  @GetMapping(value = "/{idWatchlist}/udf", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<SecuritycurrencyUDFGroup> getWatchlistWithUDFData(@PathVariable final Integer idWatchlist)
      throws InterruptedException, ExecutionException {
    return new ResponseEntity<>(watchlistReport.getWatchlistWithUDFData(idWatchlist), HttpStatus.OK);
  }

  @Operation(summary = "Returns the content of a watchlist which includes if the security has ever have splits or dividends", description = "", tags = {
      Watchlist.TABNAME })
  @GetMapping(value = "/{idWatchlist}/dividendsplit", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<SecuritycurrencyGroup> getWatchlistForSplitAndDividend(@PathVariable final Integer idWatchlist)
      throws InterruptedException, ExecutionException {
    return new ResponseEntity<>(watchlistReport.getWatchlistForSplitAndDividend(idWatchlist), HttpStatus.OK);
  }

  @Operation(summary = """
      The data sources with API keys are only delivered to the frontend for administrators.
      All other users must use the following functionality. The content is created in the backend and returned to the user in the frontend.
      In this case, either historical or intraday price data.""", tags = {
      Watchlist.TABNAME })
  @GetMapping(value = RequestGTMappings.SECURITY_DATAPROVIDER_INTRA_HISTORICAL_RESPONSE + "{idSecuritycurrency}")
  public String getDataProviderIntraHistoricalResponseForUser(@PathVariable final Integer idSecuritycurrency,
      @Parameter(description = "True when for intraday otherwise false", required = true) @RequestParam() final boolean isIntraday,
      @Parameter(description = "True when whenn security, false for currency", required = true) @RequestParam() final boolean isSecurity) {
    return watchlistJpaRepository.getDataProviderResponseForUser(idSecuritycurrency, isIntraday, isSecurity);
  }

  @Operation(summary = """
      The creation of a download link for historical or intraday data can take a long time,
      as the data source is contacted during the creation process.
      In this case, the download link is only created if the user requests it in the frontend.""", tags = {
      Watchlist.TABNAME })
  @GetMapping(value = "/dataproviderlink/{idSecuritycurrency}", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<String> getDataProviderLinkForUser(@PathVariable final Integer idSecuritycurrency,
      @Parameter(description = "True when for intraday otherwise false", required = true) @RequestParam() final boolean isIntraday,
      @Parameter(description = "True when whenn security, false for currency", required = true) @RequestParam() final boolean isSecurity) {
    return new ResponseEntity<>(
        watchlistJpaRepository.getDataProviderLinkForUser(idSecuritycurrency, isIntraday, isSecurity), HttpStatus.OK);
  }

  @Operation(summary = """
      The data sources with API keys are only delivered to the frontend for administrators.
      All other users must use the following functionality. The content is created in the backend and returned to the user in the frontend.
      In this case, either splits or dividends.""", tags = {
      Watchlist.TABNAME })
  @GetMapping(value = RequestGTMappings.SECURITY_DATAPROVIDER_DIV_SPLIT_HISTORICAL_RESPONSE + "{idSecuritycurrency}")
  public String getDataProviderDivSplitResponseForUser(@PathVariable final Integer idSecuritycurrency,
      @Parameter(description = "True when for dividen otherwise false", required = true) @RequestParam() final boolean isDiv) {
    return watchlistJpaRepository.getDataProviderDivSplitResponseForUser(idSecuritycurrency, isDiv);
  }

  @Override
  protected String getPrefixEntityLimit() {
    return GlobalConstants.GT_LIMIT_DAY;
  }

}
