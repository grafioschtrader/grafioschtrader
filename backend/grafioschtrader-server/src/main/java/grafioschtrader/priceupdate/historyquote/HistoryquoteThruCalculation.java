package grafioschtrader.priceupdate.historyquote;

import java.text.ParseException;
import java.util.Date;
import java.util.List;

import org.springframework.context.MessageSource;

import grafioschtrader.entities.Historyquote;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.Securitycurrency;
import grafioschtrader.priceupdate.ThruCalculationHelper;
import grafioschtrader.reportviews.historyquotequality.HistoryquoteQualityGrouped;
import grafioschtrader.reportviews.historyquotequality.HistoryquoteQualityHead;
import grafioschtrader.repository.GlobalparametersJpaRepository;
import grafioschtrader.repository.HistoryquoteJpaRepository;
import grafioschtrader.repository.ISecuritycurrencyService;
import grafioschtrader.repository.SecurityDerivedLinkJpaRepository;
import grafioschtrader.repository.SecurityJpaRepository;

/**
 * Update calculation of derived security.
 *
 */
public class HistoryquoteThruCalculation<S extends Securitycurrency<Security>> extends BaseHistoryquoteThru<Security> {

  private final SecurityJpaRepository securityJpaRepository;
  private final HistoryquoteJpaRepository historyquoteJpaRepository;
  private final SecurityDerivedLinkJpaRepository securityDerivedLinkJpaRepository;

  public HistoryquoteThruCalculation(final SecurityJpaRepository securityJpaRepository,
      HistoryquoteJpaRepository historyquoteJpaRepository,
      SecurityDerivedLinkJpaRepository securityDerivedLinkJpaRepository,
      GlobalparametersJpaRepository globalparametersJpaRepository,
      IHistoryqouteEntityBaseAccess<Security> historyqouteEntityBaseAccess) {
    super(globalparametersJpaRepository, historyqouteEntityBaseAccess);
    this.securityJpaRepository = securityJpaRepository;
    this.historyquoteJpaRepository = historyquoteJpaRepository;
    this.securityDerivedLinkJpaRepository = securityDerivedLinkJpaRepository;

  }

  @Override
  public Security createHistoryQuotesAndSave(ISecuritycurrencyService<Security> securitycurrencyService, Security security,
      Date fromDate, Date toDate) {
    short restryHistoryLoad = security.getRetryHistoryLoad();
    try {
      final Date correctedFromDate = getCorrectedFromDate(security, fromDate);
      final Date toDateCalc = (toDate == null) ? new Date() : toDate;
      List<Historyquote> newHistoryquotes = ThruCalculationHelper.loadDataAndCreateHistoryquotes(
          securityDerivedLinkJpaRepository, historyquoteJpaRepository, security, correctedFromDate, toDateCalc);
      addHistoryquotesToSecurity(security, newHistoryquotes, correctedFromDate, toDateCalc);

    } catch (final ParseException pe) {
      restryHistoryLoad++;
    }
    security.setRetryHistoryLoad(restryHistoryLoad);

    return securitycurrencyService.getJpaRepository().save(security);
  }

  @Override
  protected List<Security> fillEmptyHistoryquote() {
    return catchUpEmptyHistoryquote(securityJpaRepository
        .findDerivedEmptyHistoryquoteByMaxRetryHistoryLoad(globalparametersJpaRepository.getMaxHistoryRetry()));
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

  

}
