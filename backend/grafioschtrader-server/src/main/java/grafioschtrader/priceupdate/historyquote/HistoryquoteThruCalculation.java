package grafioschtrader.priceupdate.historyquote;

import java.text.ParseException;
import java.time.LocalDate;
import java.util.List;

import org.springframework.context.MessageSource;

import grafioschtrader.connector.instrument.IFeedConnector;
import grafioschtrader.entities.Historyquote;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.Securitycurrency;
import grafioschtrader.priceupdate.ThruCalculationHelper;
import grafioschtrader.reportviews.historyquotequality.HistoryquoteQualityGrouped;
import grafioschtrader.reportviews.historyquotequality.HistoryquoteQualityHead;
import grafioschtrader.repository.HistoryquoteJpaRepository;
import grafioschtrader.repository.ISecuritycurrencyService;
import grafioschtrader.repository.SecurityDerivedLinkJpaRepository;
import grafioschtrader.repository.SecurityJpaRepository;
import grafioschtrader.service.GlobalparametersService;

/**
 * Update calculation of derived security.
 */
public class HistoryquoteThruCalculation<S extends Securitycurrency<Security>> extends BaseHistoryquoteThru<Security> {

  private final SecurityJpaRepository securityJpaRepository;
  private final HistoryquoteJpaRepository historyquoteJpaRepository;
  private final SecurityDerivedLinkJpaRepository securityDerivedLinkJpaRepository;

  public HistoryquoteThruCalculation(final SecurityJpaRepository securityJpaRepository,
      HistoryquoteJpaRepository historyquoteJpaRepository,
      SecurityDerivedLinkJpaRepository securityDerivedLinkJpaRepository,
      GlobalparametersService globalparametersService,
      IHistoryqouteEntityBaseAccess<Security> historyqouteEntityBaseAccess) {
    super(globalparametersService, historyqouteEntityBaseAccess);
    this.securityJpaRepository = securityJpaRepository;
    this.historyquoteJpaRepository = historyquoteJpaRepository;
    this.securityDerivedLinkJpaRepository = securityDerivedLinkJpaRepository;

  }

  @Override
  public Security createHistoryQuotesAndSave(ISecuritycurrencyService<Security> securitycurrencyService,
      Security security, LocalDate fromDate, LocalDate toDate) {
    short retryHistoryLoad = security.getRetryHistoryLoad();
    try {
      final LocalDate correctedFromDate = getCorrectedFromDate(security, fromDate);
      final LocalDate toDateCalc = (toDate == null) ? LocalDate.now() : toDate;
      List<Historyquote> newHistoryquotes = ThruCalculationHelper.loadDataAndCreateHistoryquotes(
          securityDerivedLinkJpaRepository, historyquoteJpaRepository, security, correctedFromDate, toDateCalc);
      LocalDate maxDate = correctedFromDate.minusDays(1);
      newHistoryquotes.addAll(ThruCalculationHelper.fillGaps(securityDerivedLinkJpaRepository, historyquoteJpaRepository, security, maxDate));
      addHistoryquotesToSecurity(security, newHistoryquotes, correctedFromDate, toDateCalc);
    } catch (final ParseException pe) {
      retryHistoryLoad++;
    }
    security.setRetryHistoryLoad(retryHistoryLoad);

    return securitycurrencyService.getJpaRepository().save(security);
  }

  @Override
  protected List<Security> fillEmptyHistoryquote() {
    return catchUpEmptyHistoryquote(securityJpaRepository
        .findDerivedEmptyHistoryquoteByMaxRetryHistoryLoad(globalparametersService.getMaxHistoryRetry()));
  }

  @Override
  public String getSecuritycurrencyHistoricalDownloadLinkAsUrlStr(Security securitycurrency) {
    return null;
  }

  @Override
  public HistoryquoteQualityHead getHistoryquoteQualityHead(HistoryquoteQualityGrouped groupedBy,
      SecurityJpaRepository securityJpaRepository, MessageSource messages) {
    return null;
  }

  @Override
  public String createDownloadLink(Security securitycurrency, IFeedConnector feedConnector) {
    return null;
  }

}
