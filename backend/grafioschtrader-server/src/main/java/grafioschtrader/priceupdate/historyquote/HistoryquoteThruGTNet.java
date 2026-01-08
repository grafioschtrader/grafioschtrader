package grafioschtrader.priceupdate.historyquote;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;

import grafioschtrader.connector.instrument.IFeedConnector;
import grafioschtrader.entities.Securitycurrency;
import grafioschtrader.reportviews.historyquotequality.HistoryquoteQualityGrouped;
import grafioschtrader.reportviews.historyquotequality.HistoryquoteQualityHead;
import grafioschtrader.reportviews.SecuritycurrencyPositionSummary;
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
  private final GlobalparametersService globalparametersService;

  public HistoryquoteThruGTNet(HistoryquoteThruConnector<S> connectorThru,
      GTNetHistoryquoteService gtNetHistoryquoteService, GlobalparametersService globalparametersService) {
    this.connectorThru = connectorThru;
    this.gtNetHistoryquoteService = gtNetHistoryquoteService;
    this.globalparametersService = globalparametersService;
  }

  @Override
  public List<S> catchAllUpSecuritycurrencyHistoryquote(List<Integer> idsStockexchange) {
    // Delegate to connectorThru which handles the overall flow
    // The GTNet integration happens in fillHistoryquoteForSecuritiesCurrencies
    return connectorThru.catchAllUpSecuritycurrencyHistoryquote(idsStockexchange);
  }

  @Override
  public <U extends SecuritycurrencyPositionSummary<S>> void reloadAsyncFullHistoryquote(
      SecurityServiceAsyncExectuion<S, U> securityServiceAsyncExectuion,
      SecuritycurrencyService<S, U> securitycurrencyService, S securitycurrency) {
    connectorThru.reloadAsyncFullHistoryquote(securityServiceAsyncExectuion, securitycurrencyService, securitycurrency);
  }

  @Override
  public S createHistoryQuotesAndSave(ISecuritycurrencyService<S> securitycurrencyService, S securitycurrency,
      Date fromDate, Date toDate) {
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
   *
   * Flow:
   * 1. Query GTNet servers for instruments with gtNetHistoricalRecv enabled
   * 2. Fall back to connectors for unfilled and non-GTNet instruments
   * 3. Push connector-fetched data back to interested GTNet suppliers
   */
  @Override
  public List<S> fillHistoryquoteForSecuritiesCurrencies(
      List<SecurityCurrencyMaxHistoryquoteData<S>> historySecurityCurrencyList, Calendar currentCalendar) {

    if (!globalparametersService.isGTNetEnabled() || historySecurityCurrencyList.isEmpty()) {
      // GTNet disabled - pass through to connector
      return connectorThru.fillHistoryquoteForSecuritiesCurrencies(historySecurityCurrencyList, currentCalendar);
    }

    log.info("Starting GTNet-integrated historyquote load for {} instruments", historySecurityCurrencyList.size());

    // 1. Query GTNet first
    HistoryquoteExchangeResult<S> gtNetResult = gtNetHistoryquoteService
        .requestHistoryquotesFromBaseThru(historySecurityCurrencyList, currentCalendar);

    // 2. Connector fallback for remaining instruments
    List<S> connectorCatchUp = connectorThru.fillHistoryquoteForSecuritiesCurrencies(
        gtNetResult.getRemainingForConnector(), currentCalendar);

    // 3. Push connector-fetched data back to interested suppliers
    if (gtNetResult.hasWantToReceive() && !connectorCatchUp.isEmpty()) {
      gtNetHistoryquoteService.pushConnectorDataToGTNet(connectorCatchUp, gtNetResult.getWantToReceiveMap());
    }

    // Combine results: GTNet-filled + connector-filled
    List<S> allCatchUp = new ArrayList<>();

    // Extract securitycurrency from GTNet-filled data
    for (SecurityCurrencyMaxHistoryquoteData<S> filled : gtNetResult.getFilledByGTNet()) {
      allCatchUp.add(filled.getSecurityCurrency());
    }
    allCatchUp.addAll(connectorCatchUp);

    log.info("GTNet-integrated historyquote load complete: {} GTNet filled, {} connector filled",
        gtNetResult.getFilledByGTNet().size(), connectorCatchUp.size());

    return allCatchUp;
  }
}
