package grafioschtrader.priceupdate.historyquote;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;

import grafiosch.repository.GlobalparametersJpaRepository;
import grafioschtrader.connector.instrument.IFeedConnector;
import grafioschtrader.entities.Historyquote;
import grafioschtrader.entities.Securitycurrency;
import grafioschtrader.gtnet.m2m.model.HistoryquoteRecordDTO;
import grafioschtrader.gtnet.m2m.model.InstrumentHistoryquoteDTO;
import grafioschtrader.reportviews.SecuritycurrencyPositionSummary;
import grafioschtrader.reportviews.historyquotequality.HistoryquoteQualityGrouped;
import grafioschtrader.reportviews.historyquotequality.HistoryquoteQualityHead;
import grafioschtrader.repository.ISecuritycurrencyService;
import grafioschtrader.repository.SecurityJpaRepository;
import grafioschtrader.repository.SecurityServiceAsyncExectuion;
import grafioschtrader.repository.SecuritycurrencyService;
import grafioschtrader.service.GTNetHistoryquoteService;
import grafioschtrader.service.GlobalparametersService;
import grafioschtrader.service.HistoryquoteExchangeResult;

/**
 * Decorator that integrates GTNet historical price exchange with connector-based loading.
 *
 * This class wraps a HistoryquoteThruConnector and intercepts calls to first query GTNet servers
 * for historical data, then falls back to connectors for unfilled instruments, and finally pushes
 * connector-fetched data back to interested GTNet suppliers.
 *
 * Flow:
 * <ol>
 *   <li>Query PUSH_OPEN GTNet servers (prioritized, randomized)</li>
 *   <li>Query OPEN GTNet servers for remaining unfilled instruments</li>
 *   <li>Connector fallback for remaining instruments</li>
 *   <li>Push connector-fetched data back to suppliers that expressed "want to receive"</li>
 * </ol>
 *
 * @param <S> Security or Currencypair
 */
public class HistoryquoteThruGTNet<S extends Securitycurrency<S>> implements IHistoryquoteLoad<S> {

  private static final Logger log = LoggerFactory.getLogger(HistoryquoteThruGTNet.class);

  private final HistoryquoteThruConnector<S> connectorThru;
  private final GTNetHistoryquoteService gtNetHistoryquoteService;
  private final GlobalparametersJpaRepository globalparametersJpaRepository;
  private final GlobalparametersService globalparametersService;
  private final ISecuritycurrencyService<S> securitycurrencyService;

  public HistoryquoteThruGTNet(HistoryquoteThruConnector<S> connectorThru,
      GTNetHistoryquoteService gtNetHistoryquoteService, GlobalparametersJpaRepository globalparametersJpaRepository,
      GlobalparametersService globalparametersService, ISecuritycurrencyService<S> securitycurrencyService) {
    this.connectorThru = connectorThru;
    this.gtNetHistoryquoteService = gtNetHistoryquoteService;
    this.globalparametersJpaRepository = globalparametersJpaRepository;
    this.globalparametersService = globalparametersService;
    this.securitycurrencyService = securitycurrencyService;
  }

  @Override
  public List<S> catchAllUpSecuritycurrencyHistoryquote(List<Integer> idsStockexchange) {
    // 1. Connector-first cold start: instruments with no historyquotes that still have retries left.
    //    This ensures the user can verify their connector configuration before GTNet is involved.
    List<S> catchUp = new ArrayList<>(connectorThru.delegateFillEmptyHistoryquote());

    // 2. GTNet-only fallback: instruments whose connector retry counter has reached gt.history.retry
    //    but is still below gt.history.retry + gt.gtnet.quote.retry, and whose owner has opted in via
    //    gtNetHistoricalRecv. Includes both empty-history and partial-history instruments — without this
    //    step, those entries are dropped from the partial-fill named query and stuck forever.
    if (globalparametersJpaRepository.isGTNetEnabled()) {
      catchUp.addAll(gtNetFallbackForExhaustedConnectors());
    }

    // Determine if this is an exchange-specific update (allows 1-day updates after exchange close)
    final boolean isExchangeSpecificUpdate = idsStockexchange != null && !idsStockexchange.isEmpty();

    // 3. Partial-fill path with GTNet integration for instruments that already have some history.
    HistoryquoteThruConnector.PartialFillData<S> partialFillData = connectorThru.getPartialFillData(idsStockexchange);
    catchUp.addAll(this.fillHistoryquoteForSecuritiesCurrencies(
        partialFillData.getHistorySecurityCurrencyList(),
        partialFillData.getCurrentDate(),
        isExchangeSpecificUpdate));

    return catchUp;
  }

  /**
   * GTNet-only fallback for instruments whose connector retries are exhausted (retryHistoryLoad >= gt.history.retry)
   * but who still have GTNet retries left (< gt.history.retry + gt.gtnet.quote.retry). Each instrument either gets
   * fresh data from GTNet (counter capped down to gt.history.retry, preserving the connector-failure signal) or has
   * its counter incremented by 1 toward the absolute exhaustion cap.
   */
  private List<S> gtNetFallbackForExhaustedConnectors() {
    short connectorCap = globalparametersService.getMaxHistoryRetry();
    short absoluteCap = (short) (connectorCap + globalparametersService.getGTNetQuoteRetry());
    List<SecurityCurrencyMaxHistoryquoteData<S>> exhausted = securitycurrencyService
        .findGTNetFallbackBandInstruments(connectorCap, absoluteCap);
    if (exhausted.isEmpty()) {
      return new ArrayList<>();
    }
    log.info("GTNet fallback: {} instruments in the GTNet-only retry band [{}, {})", exhausted.size(), connectorCap,
        absoluteCap);

    LocalDate currentDate = LocalDate.now();
    HistoryquoteExchangeResult<S> gtNetResult = gtNetHistoryquoteService
        .requestHistoryquotesFromBaseThru(exhausted, currentDate);

    List<S> saved = saveGTNetFilledData(gtNetResult, currentDate);

    // Bump the retry counter for the instruments GTNet did NOT serve (capped at absoluteCap).
    incrementRetryForUnfilledFallback(gtNetResult.getRemainingForConnector(), absoluteCap);

    return saved;
  }

  private void incrementRetryForUnfilledFallback(List<SecurityCurrencyMaxHistoryquoteData<S>> unfilled,
      short absoluteCap) {
    for (SecurityCurrencyMaxHistoryquoteData<S> data : unfilled) {
      S instrument = data.getSecurityCurrency();
      short next = (short) Math.min((int) instrument.getRetryHistoryLoad() + 1, (int) absoluteCap);
      if (next != instrument.getRetryHistoryLoad()) {
        instrument.setRetryHistoryLoad(next);
        securitycurrencyService.getJpaRepository().save(instrument);
      }
    }
  }

  @Override
  public <U extends SecuritycurrencyPositionSummary<S>> void reloadAsyncFullHistoryquote(
      SecurityServiceAsyncExectuion<S, U> securityServiceAsyncExectuion,
      SecuritycurrencyService<S, U> securitycurrencyService, S securitycurrency) {
    connectorThru.reloadAsyncFullHistoryquote(securityServiceAsyncExectuion, securitycurrencyService, securitycurrency);
  }

  @Override
  public S createHistoryQuotesAndSave(ISecuritycurrencyService<S> securitycurrencyService, S securitycurrency,
      LocalDate fromDate, LocalDate toDate) {
    return connectorThru.createHistoryQuotesAndSave(securitycurrencyService, securitycurrency, fromDate, toDate);
  }

  @Override
  public String getSecuritycurrencyHistoricalDownloadLinkAsUrlStr(S securitycurrency) {
    return connectorThru.getSecuritycurrencyHistoricalDownloadLinkAsUrlStr(securitycurrency);
  }

  @Override
  public String createDownloadLink(S securitycurrency, IFeedConnector feedConnector) {
    return connectorThru.createDownloadLink(securitycurrency, feedConnector);
  }

  @Override
  public HistoryquoteQualityHead getHistoryquoteQualityHead(HistoryquoteQualityGrouped groupedBy,
      SecurityJpaRepository securityJpaRepository, MessageSource messages) {
    return connectorThru.getHistoryquoteQualityHead(groupedBy, securityJpaRepository, messages);
  }

  /**
   * Fills historical quotes for securities/currencies with GTNet integration.
   * Uses global update mode (requires 2+ days difference).
   */
  @Override
  public List<S> fillHistoryquoteForSecuritiesCurrencies(
      List<SecurityCurrencyMaxHistoryquoteData<S>> historySecurityCurrencyList, LocalDate currentDate) {
    return fillHistoryquoteForSecuritiesCurrencies(historySecurityCurrencyList, currentDate, false);
  }

  /**
   * Fills historical quotes for securities/currencies with GTNet integration.
   *
   * Flow:
   * 1. Query GTNet servers for instruments with gtNetHistoricalRecv enabled
   * 2. Save GTNet-filled data through proper connector save flow
   * 3. Fall back to connectors for unfilled and non-GTNet instruments
   * 4. Push connector-fetched data back to interested GTNet suppliers
   *
   * @param historySecurityCurrencyList list of securities/currencies with their maximum historical quote dates
   * @param currentDate current calendar for determining the update range
   * @param isExchangeSpecificUpdate true for exchange-specific updates (requires 1+ day difference),
   *                                  false for global daily updates (requires 2+ days difference)
   * @return list of updated securities or currency pairs
   */
  @Override
  public List<S> fillHistoryquoteForSecuritiesCurrencies(
      List<SecurityCurrencyMaxHistoryquoteData<S>> historySecurityCurrencyList, LocalDate currentDate,
      boolean isExchangeSpecificUpdate) {

    if (!globalparametersJpaRepository.isGTNetEnabled() || historySecurityCurrencyList.isEmpty()) {
      // GTNet disabled - pass through to connector with correct flag
      return connectorThru.fillHistoryquoteForSecuritiesCurrencies(historySecurityCurrencyList, currentDate,
          isExchangeSpecificUpdate);
    }

    log.info("Starting GTNet-integrated historyquote load for {} instruments", historySecurityCurrencyList.size());

    // 1. Query GTNet first (returns data WITHOUT storing)
    HistoryquoteExchangeResult<S> gtNetResult = gtNetHistoryquoteService
        .requestHistoryquotesFromBaseThru(historySecurityCurrencyList, currentDate);

    // 2. Save GTNet-filled data through proper flow
    List<S> gtNetCatchUp = saveGTNetFilledData(gtNetResult, currentDate);

    // 3. Connector fallback for remaining instruments with correct flag
    List<S> connectorCatchUp = connectorThru.fillHistoryquoteForSecuritiesCurrencies(
        gtNetResult.getRemainingForConnector(), currentDate, isExchangeSpecificUpdate);

    // 4. Push connector-fetched data back to interested suppliers
    if (gtNetResult.hasWantToReceive() && !connectorCatchUp.isEmpty()) {
      gtNetHistoryquoteService.pushConnectorDataToGTNet(connectorCatchUp, gtNetResult.getWantToReceiveMap());
    }

    // Combine results: GTNet-filled + connector-filled
    List<S> allCatchUp = new ArrayList<>(gtNetCatchUp);
    allCatchUp.addAll(connectorCatchUp);

    log.info("GTNet-integrated historyquote load complete: {} GTNet filled, {} connector filled",
        gtNetCatchUp.size(), connectorCatchUp.size());

    return allCatchUp;
  }

  /**
   * Saves GTNet-filled historyquote data via the connector's "preserve retry" save flow. The retry counter is capped
   * at gt.history.retry on success rather than reset to zero — GTNet success must not erase the connector-failure
   * signal that monitoring depends on. Instruments whose counter is already below the cap are unaffected.
   *
   * @param gtNetResult the result from GTNet exchange containing filled instruments and their data
   * @param currentDate the target end date for historyquote loading
   * @return list of successfully saved instruments
   */
  private List<S> saveGTNetFilledData(HistoryquoteExchangeResult<S> gtNetResult, LocalDate currentDate) {
    List<S> savedInstruments = new ArrayList<>();
    short connectorCap = globalparametersService.getMaxHistoryRetry();

    for (SecurityCurrencyMaxHistoryquoteData<S> filled : gtNetResult.getFilledByGTNet()) {
      S securitycurrency = filled.getSecurityCurrency();
      InstrumentHistoryquoteDTO dto = gtNetResult.getReceivedDataFor(securitycurrency);

      if (dto != null && dto.getRecords() != null && !dto.getRecords().isEmpty()) {
        try {
          // Convert DTO records to Historyquote entities
          List<Historyquote> historyquotes = convertToHistoryquotes(dto.getRecords(),
              securitycurrency.getIdSecuritycurrency());

          // Calculate dates
          LocalDate fromDate = filled.getDate() != null ? filled.getDate().plusDays(1) : null;
          LocalDate toDate = currentDate;

          // Save while preserving the connector-failure signal (counter capped at gt.history.retry, never reset to 0).
          S saved = connectorThru.savePrefetchedHistoryQuotesAsFallback(securitycurrencyService, securitycurrency,
              historyquotes, fromDate, toDate, connectorCap);
          savedInstruments.add(saved);
        } catch (Exception e) {
          log.warn("Failed to save GTNet historyquotes for {}: {}", securitycurrency.getIdSecuritycurrency(),
              e.getMessage());
        }
      }
    }

    return savedInstruments;
  }

  /**
   * Converts HistoryquoteRecordDTOs to Historyquote entities.
   */
  private List<Historyquote> convertToHistoryquotes(List<HistoryquoteRecordDTO> records, Integer idSecuritycurrency) {
    List<Historyquote> historyquotes = new ArrayList<>();
    for (HistoryquoteRecordDTO record : records) {
      if (record.getDate() != null && record.getClose() != null) {
        Historyquote hq = new Historyquote();
        hq.setIdSecuritycurrency(idSecuritycurrency);
        hq.setDate(record.getDate());
        hq.setOpen(record.getOpen());
        hq.setHigh(record.getHigh());
        hq.setLow(record.getLow());
        hq.setClose(record.getClose());
        hq.setVolume(record.getVolume());
        historyquotes.add(hq);
      }
    }
    return historyquotes;
  }

}
