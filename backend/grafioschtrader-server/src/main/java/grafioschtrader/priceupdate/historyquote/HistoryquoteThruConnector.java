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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
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
import grafioschtrader.repository.ISecuritycurrencyService;
import grafioschtrader.repository.SecurityJpaRepository;
import grafioschtrader.types.AssetclassType;
import grafioschtrader.types.HistoryquoteCreateType;
import grafioschtrader.types.SpecialInvestmentInstruments;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.transaction.Transactional;

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
  @Transactional
  public S createHistoryQuotesAndSave(final ISecuritycurrencyService<S> securitycurrencyService, S securitycurrency,
      final Date fromDate, final Date toDate) {
    short restryHistoryLoad = securitycurrency.getRetryHistoryLoad();

    try {
      final IFeedConnector feedConnector = getConnectorHistoricalForSecuritycurrency(securitycurrency);
      boolean needGapFiller = securitycurrency instanceof Security security
          && security.getStockexchange().getIdIndexUpdCalendar() != null
              ? feedConnector.needHistoricalGapFiller(security)
              : false;
      if (feedConnector != null) {
        HistoryquoteDataChange hdc = createWithHistoryQuoteWithConnector(securitycurrencyService, securitycurrency,
            feedConnector, fromDate, toDate, needGapFiller);

        if (hdc.removeFromDate != null) {
          historyquoteEntityAccess.getHistoryquoteJpaRepository().deleteByIdSecuritycurrencyAndDateGreaterThanEqual(
              securitycurrency.getIdSecuritycurrency(), hdc.historyquotes.get(0).getDate());
          securitycurrency = securitycurrencyService.getJpaRepository()
              .findByIdSecuritycurrency(securitycurrency.getIdSecuritycurrency());
        }
        restryHistoryLoad = 0;
        addHistoryquotesToSecurity(securitycurrency, hdc.historyquotes, hdc.correctedFromDate, hdc.toDateCalc);

        if (needGapFiller) {
          securitycurrency = securitycurrencyService.getJpaRepository().save(securitycurrency);
          addHistoryquotesToSecurity(securitycurrency, securitycurrencyService.fillGap(securitycurrency), fromDate,
              toDate);
        }
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

    return securitycurrencyService.getJpaRepository().save(securitycurrency);
  }

  @Override
  public String getSecuritycurrencyHistoricalDownloadLinkAsUrlStr(S securitycurrency) {
    final IFeedConnector feedConnector = getConnectorHistoricalForSecuritycurrency(securitycurrency);
    if (ConnectorHelper.canAccessConnectorApiKey(feedConnector)) {
      if (securitycurrency instanceof Security security) {
        return (feedConnector == null) ? null : feedConnector.getSecurityHistoricalDownloadLink(security);
      } else {
        return (feedConnector == null) ? null
            : feedConnector.getCurrencypairHistoricalDownloadLink((Currencypair) securitycurrency);
      }
    } else {
      return null;
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

  private HistoryquoteDataChange createWithHistoryQuoteWithConnector(
      final ISecuritycurrencyService<S> securitycurrencyService, final S securitycurrency,
      final IFeedConnector feedConnector, final Date fromDate, final Date toDate, boolean needGapFiller) throws Exception {

    HistoryquoteDataChange hdc = new HistoryquoteDataChange(getCorrectedFromDate(securitycurrency, fromDate));
    hdc.toDateCalc = (toDate == null) ? new Date() : toDate;
    Date correctedFromDateGapFill = securitycurrency instanceof Security
        ? getFirstGapFillByAfterLastRealEOD((Security) securitycurrency, needGapFiller, hdc.correctedFromDate)
        : hdc.correctedFromDate;

    hdc.historyquotes = historyquoteEntityAccess.getHistoryQuote(securitycurrency,
        substractSomeDays(
            correctedFromDateGapFill.before(hdc.correctedFromDate) ? correctedFromDateGapFill : hdc.correctedFromDate,
            hdc.toDateCalc),
        hdc.toDateCalc, feedConnector);

    hdc.removeFromDate = removeFillGap(securitycurrency, hdc.historyquotes, needGapFiller, hdc.correctedFromDate,
        correctedFromDateGapFill);

    hdc.historyquotes = hdc.historyquotes.stream().parallel()
        .filter(historyquote -> hdc.removeFromDate != null || !historyquote.getDate().before(hdc.correctedFromDate))
        .peek(h -> h.setIdSecuritycurrency(securitycurrency.getIdSecuritycurrency())).collect(Collectors.toList());

    return hdc;

  }

  private Date removeFillGap(final S securitycurrency, List<Historyquote> historyquotes, boolean needGapFiller,
      Date correctedFromDate, Date correctedFromDateGapFill) {
    boolean hasGapFillRemoved = needGapFiller && correctedFromDateGapFill.before(correctedFromDate)
        && !historyquotes.isEmpty() && historyquotes.get(0).getDate().before(correctedFromDate);
    return hasGapFillRemoved ? historyquotes.get(0).getDate() : null;
  }

  /**
   * For the area of the recent EOD gap previously felt by the system, there could
   * now be one or more real EODs. Therefore, the data back to the most recent
   * real EOD is requested again by the connector.
   * 
   * @param security
   * @param needGapFiller
   * @param toDateCalc
   * @return
   */
  private Date getFirstGapFillByAfterLastRealEOD(Security security, boolean needGapFiller, Date correctedFromDate) {
    if (needGapFiller && security.getHistoryquoteList() != null) {
      int i = security.getHistoryquoteList().size();
      do {
        i -= 1;
      } while (i > 0
          && security.getHistoryquoteList().get(i).getCreateType() == HistoryquoteCreateType.FILL_GAP_BY_CONNECTOR);
      if (i < security.getHistoryquoteList().size() - 1) {
        return security.getHistoryquoteList().get(i + 1).getDate();
      }
    }
    return correctedFromDate;
  }

  /**
   * Some data provider cause an error, if there is not minimum of days between
   * the two dates. For this reason, some days are subtract form the older date.
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

    try (Stream<IHistoryquoteQualityFlat> hqfStream = groupedBy == HistoryquoteQualityGrouped.CONNECTOR_GROUPED
        ? securityJpaRepository.getHistoryquoteQualityConnectorFlat()
        : securityJpaRepository.getHistoryquoteQualityStockexchangeFlat()) {
      hqfStream.forEach(historyquoteQualityFlat -> {
        boolean isConnectGroup = groupedBy == HistoryquoteQualityGrouped.CONNECTOR_GROUPED;
        String readableName = null;
        String readableNamePrefix = "";
        IFeedConnector ifeedConnector = ConnectorHelper.getConnectorByConnectorId(this.feedConnectorbeans,
            historyquoteQualityFlat.getIdConnectorHistory(), IFeedConnector.FeedSupport.HISTORY);
        if (ifeedConnector == null) {
          ifeedConnector = ConnectorHelper.getConnectorByConnectorId(this.feedConnectorbeans,
              historyquoteQualityFlat.getIdConnectorHistory(), IFeedConnector.FeedSupport.INTRA);
          readableNamePrefix = "† - ";

        }
        readableName = ifeedConnector == null ? "†" : readableNamePrefix + ifeedConnector.getReadableName();

        groupValues[0] = isConnectGroup ? readableName : historyquoteQualityFlat.getStockexchangeName();
        groupValues[1] = isConnectGroup ? historyquoteQualityFlat.getStockexchangeName() : readableName;
        groupValues[2] = messages.getMessage(
            AssetclassType.getAssetClassTypeByValue(historyquoteQualityFlat.getCategoryType()).name(), null,
            userLocale);
        groupValues[2] = groupValues[2] + " / "
            + messages.getMessage(SpecialInvestmentInstruments
                .getSpecialInvestmentInstrumentsByValue(historyquoteQualityFlat.getSpecialInvestmentInstrument())
                .name(), null, userLocale);
        historyquoteQualityHead.addHistoryquoteQualityFlat(historyquoteQualityFlat, groupValues, 0, isConnectGroup);

      });
    }
    return historyquoteQualityHead;
  }

  private static class HistoryquoteDataChange {
    public Date correctedFromDate;
    public Date toDateCalc;
    public List<Historyquote> historyquotes;
    public Date removeFromDate;

    public HistoryquoteDataChange(Date correctedFromDate) {
      this.correctedFromDate = correctedFromDate;
    }
  }
}
