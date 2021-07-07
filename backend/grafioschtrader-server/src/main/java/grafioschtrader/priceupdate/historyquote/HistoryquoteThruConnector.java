package grafioschtrader.priceupdate.historyquote;

import java.text.ParseException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.security.core.context.SecurityContextHolder;

import grafioschtrader.connector.ConnectorHelper;
import grafioschtrader.connector.instrument.IFeedConnector;
import grafioschtrader.dto.IHistoryquoteQualityFlat;
import grafioschtrader.entities.Currencypair;
import grafioschtrader.entities.Globalparameters;
import grafioschtrader.entities.Historyquote;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.Securitycurrency;
import grafioschtrader.entities.User;
import grafioschtrader.reportviews.historyquotequality.HistoryquoteQualityGrouped;
import grafioschtrader.reportviews.historyquotequality.HistoryquoteQualityHead;
import grafioschtrader.repository.GlobalparametersJpaRepository;
import grafioschtrader.repository.SecurityJpaRepository;
import grafioschtrader.types.AssetclassType;
import grafioschtrader.types.SpecialInvestmentInstruments;

/**
 * Update or load historical prices thru the connector for securities or
 * currency pair.
 *
 *
 * @param <S>
 */
public class HistoryquoteThruConnector<S extends Securitycurrency<S>> extends BaseHistoryquoteThru<S> {

  private final Logger log = LoggerFactory.getLogger(this.getClass());

  private final EntityManager entityManager;

  private final List<IFeedConnector> feedConnectorbeans;
  private final IHistoryquoteEntityAccess<S> historyquoteEntityAccess;
  private final Class<S> entityType;

  public HistoryquoteThruConnector(EntityManager entityManager,
      GlobalparametersJpaRepository globalparametersJpaRepository, List<IFeedConnector> feedConnectorbeans,
      IHistoryquoteEntityAccess<S> historyquoteEntityAccess, Class<S> entityType) {
    super(globalparametersJpaRepository, historyquoteEntityAccess);
    this.entityManager = entityManager;
    this.feedConnectorbeans = feedConnectorbeans;
    this.historyquoteEntityAccess = historyquoteEntityAccess;
    this.entityType = entityType;
  }

  @Override
  public S createHistoryQuotesAndSave(final JpaRepository<S, Integer> jpaRepository, final S securitycurrency,
      final Date fromDate, final Date toDate) {
    short restryHistoryLoad = securitycurrency.getRetryHistoryLoad();

    try {
      final IFeedConnector feedConnector = getConnectorHistoricalForSecuritycurrency(securitycurrency);
      if (feedConnector != null) {
        createWithHistoryQuoteWithConnector(securitycurrency, feedConnector, fromDate, toDate);
        restryHistoryLoad = 0;
      }
    } catch (final ParseException pe) {
      log.error(pe.getMessage() + "Offset: " + pe.getErrorOffset(), pe);
      restryHistoryLoad++;
    } catch (final Exception ex) {
      restryHistoryLoad++;
      log.error(ex.getMessage() + " " + securitycurrency, ex);
    }

    securitycurrency.setRetryHistoryLoad(restryHistoryLoad);
    if (fromDate == null && toDate == null) {
      securitycurrency.setFullLoadTimestamp(new Date(System.currentTimeMillis()));
    }

    return jpaRepository.save(securitycurrency);
  }

  @Override
  public String getSecuritycurrencyHistoricalDownloadLinkAsUrlStr(S securitycurrency) {
    final IFeedConnector feedConnector = getConnectorHistoricalForSecuritycurrency(securitycurrency);
    if (securitycurrency instanceof Security) {
      return (feedConnector == null) ? null
          : feedConnector.getSecurityHistoricalDownloadLink((Security) securitycurrency);
    } else {
      return (feedConnector == null) ? null
          : feedConnector.getCurrencypairHistoricalDownloadLink((Currencypair) securitycurrency);
    }
  }

  private IFeedConnector getConnectorHistoricalForSecuritycurrency(final Securitycurrency<?> securitycurrency) {
    return ConnectorHelper.getConnectorByConnectorId(feedConnectorbeans, securitycurrency.getIdConnectorHistory(),
        IFeedConnector.FeedSupport.HISTORY);
  }

  /**
   * Try to fill prices with all security or currencies without any history quote
   * until now. Security or currency pair must have a connector. Data is also
   * persisted.
   *
   * @param type
   * @return
   */
  @Override
  protected List<S> fillEmptyHistoryquote() {
    final TypedQuery<S> query = entityManager
        .createQuery("SELECT s FROM " + entityType.getSimpleName() + " s WHERE s.idConnectorHistory IS NOT NULL "
            + "AND s.retryHistoryLoad < ?1 AND NOT EXISTS (SELECT h FROM s.historyquoteList h)", entityType)
        .setParameter(1, globalparametersJpaRepository.getMaxHistoryRetry());
    return catchUpEmptyHistoryquote(query.getResultList());
  }

  private void createWithHistoryQuoteWithConnector(final S securitycurrency, final IFeedConnector feedConnector,
      final Date fromDate, final Date toDate) throws Exception {

    final Date correctedFromDate = getCorrectedFromDate(securitycurrency, fromDate);
    final Date toDateCalc = (toDate == null) ? new Date() : toDate;

    List<Historyquote> historyquotes = historyquoteEntityAccess.getHistoryQuote(securitycurrency,
        substractSomeDays(correctedFromDate, toDateCalc), toDateCalc, feedConnector);

    historyquotes = historyquotes.stream().parallel()
        .filter(historyquote -> !historyquote.getDate().before(correctedFromDate)).collect(Collectors.toList());

    historyquotes.stream()
        .forEach(historyquote -> historyquote.setIdSecuritycurrency(securitycurrency.getIdSecuritycurrency()));

    addHistoryquotesToSecurity(securitycurrency, historyquotes, correctedFromDate, toDateCalc);
  }

  /**
   * Some data provider cause an error, if there is no match between the two
   * dates. For this reason, some days are subtract form the older Date.
   *
   * @param fromDate
   * @param toDate
   * @return
   */
  private Date substractSomeDays(final Date fromDate, final Date toDate) {

    final LocalDate toDateLocal = toDate.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
    LocalDate fromDateLocal = fromDate instanceof java.sql.Date ? ((java.sql.Date) fromDate).toLocalDate()
        : fromDate.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
    if (toDateLocal.until(LocalDate.now(), ChronoUnit.DAYS) < 3) {
      final long daysBeetweenFromTo = fromDateLocal.until(toDateLocal, ChronoUnit.DAYS);
      if (daysBeetweenFromTo <= 2) {
        fromDateLocal = fromDateLocal.minusDays(5);
      }
    }
    return Date.from(fromDateLocal.atStartOfDay(ZoneId.systemDefault()).toInstant());
  }

  @Override
  public HistoryquoteQualityHead getHistoryquoteQualityHead(HistoryquoteQualityGrouped groupedBy,
      SecurityJpaRepository securityJpaRepository, MessageSource messages) {

    Optional<Globalparameters> globalparameters = globalparametersJpaRepository
        .findById(Globalparameters.GLOB_KEY_HISTORYQUOTE_QUALITY_UPDATE_DATE);

    HistoryquoteQualityHead historyquoteQualityHead = new HistoryquoteQualityHead("head",
        globalparameters.isPresent() ? globalparameters.get().getPropertyDate() : null);
    final User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    Locale userLocale = user.createAndGetJavaLocale();
    String[] groupValues = new String[3];

    Stream<IHistoryquoteQualityFlat> hqfStream = groupedBy == HistoryquoteQualityGrouped.CONNECTOR_GROUPED
        ? securityJpaRepository.getHistoryquoteQualityConnectorFlat()
        : securityJpaRepository.getHistoryquoteQualityStockexchangeFlat();

    hqfStream.forEach(historyquoteQualityFlat -> {
      boolean isConnectGroup = groupedBy == HistoryquoteQualityGrouped.CONNECTOR_GROUPED;
      groupValues[0] = isConnectGroup
          ? ConnectorHelper.getConnectorByConnectorId(this.feedConnectorbeans,
              historyquoteQualityFlat.getIdConnectorHistory(), IFeedConnector.FeedSupport.HISTORY).getReadableName()
          : historyquoteQualityFlat.getStockexchangeName();
      groupValues[1] = isConnectGroup ? historyquoteQualityFlat.getStockexchangeName()
          : ConnectorHelper.getConnectorByConnectorId(this.feedConnectorbeans,
              historyquoteQualityFlat.getIdConnectorHistory(), IFeedConnector.FeedSupport.HISTORY).getReadableName();
      groupValues[2] = messages.getMessage(
          AssetclassType.getAssetClassTypeByValue(historyquoteQualityFlat.getCategoryType()).name(), null, userLocale);
      groupValues[2] = groupValues[2] + " / "
          + messages.getMessage(SpecialInvestmentInstruments
              .getSpecialInvestmentInstrumentsByValue(historyquoteQualityFlat.getSpecialInvestmentInstrument()).name(),
              null, userLocale);
      historyquoteQualityHead.addHistoryquoteQualityFlat(historyquoteQualityFlat, groupValues, 0, isConnectGroup);

    });
    return historyquoteQualityHead;
  }

}
