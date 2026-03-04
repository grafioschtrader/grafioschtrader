package grafioschtrader.priceupdate.historyquote;

import java.text.ParseException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.security.core.context.SecurityContextHolder;

import grafiosch.entities.Globalparameters;
import grafiosch.entities.User;
import grafioschtrader.GlobalParamKeyDefault;
import grafioschtrader.connector.ConnectorHelper;
import grafioschtrader.connector.instrument.IFeedConnector;
import grafioschtrader.connector.instrument.IFeedConnector.DownloadLink;
import grafioschtrader.connector.instrument.generic.GenericFeedConnector;
import grafioschtrader.dto.IHistoryquoteQualityFlat;
import grafioschtrader.entities.Currencypair;
import grafioschtrader.entities.Historyquote;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.Securitycurrency;
import grafioschtrader.reportviews.historyquotequality.HistoryquoteQualityGrouped;
import grafioschtrader.reportviews.historyquotequality.HistoryquoteQualityHead;
import grafioschtrader.repository.GenericConnectorEndpointJpaRepository;
import grafioschtrader.repository.ISecuritycurrencyService;
import grafioschtrader.repository.SecurityJpaRepository;
import grafioschtrader.service.GlobalparametersService;
import grafioschtrader.types.AssetclassType;
import grafioschtrader.types.HistoryquoteCreateType;
import grafioschtrader.types.SpecialInvestmentInstruments;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.transaction.Transactional;

/**
 * Update or load historical prices thru the connector for securities or currency pair.
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
  private final GenericConnectorEndpointJpaRepository genericConnectorEndpointJpaRepository;

  public HistoryquoteThruConnector(EntityManager entityManager, GlobalparametersService globalparametersService,
      List<IFeedConnector> feedConnectorbeans, IHistoryquoteEntityAccess<S> historyquoteEntityAccess,
      Class<S> entityType, GenericConnectorEndpointJpaRepository genericConnectorEndpointJpaRepository) {
    super(globalparametersService, historyquoteEntityAccess);
    this.entityManager = entityManager;
    this.feedConnectorbeans = feedConnectorbeans;
    this.historyquoteEntityAccess = historyquoteEntityAccess;
    this.entityType = entityType;
    this.genericConnectorEndpointJpaRepository = genericConnectorEndpointJpaRepository;
  }

  @Override
  @Transactional
  public S createHistoryQuotesAndSave(final ISecuritycurrencyService<S> securitycurrencyService, S securitycurrency,
      final LocalDate fromDate, final LocalDate toDate) {
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
              securitycurrency.getIdSecuritycurrency(), hdc.historyquotes.getFirst().getDate());
          securitycurrency = securitycurrencyService.getJpaRepository()
              .findByIdSecuritycurrency(securitycurrency.getIdSecuritycurrency());
        }
        restryHistoryLoad = 0;
        markGenericEndpointUsed(feedConnector, securitycurrency);
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
      securitycurrency.setFullLoadTimestamp(LocalDateTime.now());
    }
    return securitycurrencyService.getJpaRepository().save(securitycurrency);
  }

  /**
   * Saves pre-fetched historical quotes (e.g., from GTNet) for a security/currency.
   * This method performs the same entity updates as createHistoryQuotesAndSave but uses provided historyquotes
   * instead of fetching from a connector.
   *
   * @param securitycurrencyService the service for saving
   * @param securitycurrency        the security or currency pair
   * @param historyquotes           the pre-fetched historical quotes to save
   * @param fromDate                the start date of the data range (null for full load)
   * @param toDate                  the end date of the data range (null for full load)
   * @return the saved security/currency with updated historyquotes
   */
  @Transactional
  public S savePrefetchedHistoryQuotes(final ISecuritycurrencyService<S> securitycurrencyService, S securitycurrency,
      final List<Historyquote> historyquotes, final LocalDate fromDate, final LocalDate toDate) {

    if (historyquotes == null || historyquotes.isEmpty()) {
      return securitycurrency;
    }

    // Re-fetch entity to attach it to the current Hibernate session.
    // The passed entity may have been loaded in a different (now closed) session,
    // causing LazyInitializationException when accessing historyquoteList.
    final Integer idSecuritycurrency = securitycurrency.getIdSecuritycurrency();
    securitycurrency = securitycurrencyService.getJpaRepository().findByIdSecuritycurrency(idSecuritycurrency);

    // Set idSecuritycurrency on each historyquote
    historyquotes.forEach(hq -> hq.setIdSecuritycurrency(idSecuritycurrency));

    // Reset retry counter on success
    securitycurrency.setRetryHistoryLoad((short) 0);

    // Add historyquotes to entity
    addHistoryquotesToSecurity(securitycurrency, historyquotes, fromDate, toDate);

    // Update full load timestamp if this was a full load
    if (fromDate == null && toDate == null) {
      securitycurrency.setFullLoadTimestamp(LocalDateTime.now());
    }

    return securitycurrencyService.getJpaRepository().save(securitycurrency);
  }

  @Override
  public String getSecuritycurrencyHistoricalDownloadLinkAsUrlStr(S securitycurrency) {
    final IFeedConnector feedConnector = getConnectorHistoricalForSecuritycurrency(securitycurrency);
    if (feedConnector != null) {
      return feedConnector.isDownloadLinkCreatedLazy().contains(DownloadLink.DL_LAZY_HISTORY) ? LINK_DOWNLOAD_LAZY
          : createDownloadLink(securitycurrency, feedConnector);
    }
    return null;
  }

  @Override
  public String createDownloadLink(S securitycurrency, IFeedConnector feedConnector) {
    if (ConnectorHelper.canAccessConnectorApiKey(feedConnector) && (feedConnector != null
        && !feedConnector.isDownloadLinkCreatedLazy().contains(DownloadLink.DL_HISTORY_FORCE_BACKEND))) {
      if (securitycurrency instanceof Security security) {
        return (feedConnector == null) ? null : feedConnector.getSecurityHistoricalDownloadLink(security);
      } else {
        return (feedConnector == null) ? null
            : feedConnector.getCurrencypairHistoricalDownloadLink((Currencypair) securitycurrency);
      }
    } else {
      return getDownlinkWithApiKey(securitycurrency, false);
    }
  }

  /**
   * Marks the generic connector endpoint as successfully used if the feed connector is a GenericFeedConnector.
   * Also transfers ownership to system (createdBy=0) if all endpoints of the connector have been used.
   */
  private void markGenericEndpointUsed(IFeedConnector feedConnector, S securitycurrency) {
    if (genericConnectorEndpointJpaRepository != null && feedConnector instanceof GenericFeedConnector gfc) {
      String instrumentType = securitycurrency instanceof Security ? "SECURITY" : "CURRENCY";
      Integer idConnector = gfc.getConnectorDef().getIdGenericConnector();
      int updated = genericConnectorEndpointJpaRepository.markEndpointUsedSuccessfully(
          idConnector, IFeedConnector.FeedSupport.FS_HISTORY.name(), instrumentType);
      if (updated > 0) {
        genericConnectorEndpointJpaRepository.transferOwnershipIfAllEndpointsUsed(idConnector);
      }
    }
  }

  private IFeedConnector getConnectorHistoricalForSecuritycurrency(final Securitycurrency<?> securitycurrency) {
    return ConnectorHelper.getConnectorByConnectorId(feedConnectorbeans, securitycurrency.getIdConnectorHistory(),
        IFeedConnector.FeedSupport.FS_HISTORY);
  }

  /**
   * Try to fill prices with all security or currencies without any history quote until now. Security or currency pair
   * must have a connector. Data is also persisted.
   *
   * @param type
   * @return
   */
  @Override
  protected List<S> fillEmptyHistoryquote() {
    final TypedQuery<S> query = entityManager
        .createQuery("SELECT s FROM " + entityType.getSimpleName() + " s WHERE s.idConnectorHistory IS NOT NULL "
            + "AND s.retryHistoryLoad < ?1 AND NOT EXISTS (SELECT h FROM s.historyquoteList h)", entityType)
        .setParameter(1, globalparametersService.getMaxHistoryRetry());
    return catchUpEmptyHistoryquote(query.getResultList());
  }

  /**
   * Delegation method for decorators to call fillEmptyHistoryquote.
   * Used by HistoryquoteThruGTNet to properly integrate GTNet into the flow.
   *
   * @return list of securities/currencies that were filled from empty state
   */
  public List<S> delegateFillEmptyHistoryquote() {
    return fillEmptyHistoryquote();
  }

  /**
   * Gets the list of securities/currencies that need partial history updates.
   * Used by HistoryquoteThruGTNet to properly integrate GTNet into the flow.
   *
   * @param idsStockexchange list of stock exchange IDs to filter by (null = all exchanges)
   * @return the corrected calendar for EOD date calculation, and the list of instruments needing updates
   */
  public PartialFillData<S> getPartialFillData(List<Integer> idsStockexchange) {
    final LocalDate currentDate = correctToDateForDayAfterUpdate(
        idsStockexchange == null || idsStockexchange.size() == 0);
    final List<SecurityCurrencyMaxHistoryquoteData<S>> historySecurityCurrencyList = historyquoteEntityAccess
        .getMaxHistoryquoteResult(globalparametersService.getMaxHistoryRetry(), this, idsStockexchange);
    return new PartialFillData<>(currentDate, historySecurityCurrencyList);
  }

  /**
   * Corrects date for day-after-update scenarios (Sunday/Monday adjustments).
   * Exposed for decorator usage.
   */
  private LocalDate correctToDateForDayAfterUpdate(boolean adjustForDayAfterUpd) {
    LocalDate currentDate = LocalDate.now();
    if (adjustForDayAfterUpd && (currentDate.getDayOfWeek() == DayOfWeek.SUNDAY
        || currentDate.getDayOfWeek() == DayOfWeek.MONDAY)) {
      currentDate = currentDate.minusDays(currentDate.getDayOfWeek().getValue() % 7);
    }
    return currentDate;
  }

  /**
   * Data class holding partial fill information for decorator pattern support.
   */
  public static class PartialFillData<S extends Securitycurrency<S>> {
    private final LocalDate currentDate;
    private final List<SecurityCurrencyMaxHistoryquoteData<S>> historySecurityCurrencyList;

    public PartialFillData(LocalDate currentDate, List<SecurityCurrencyMaxHistoryquoteData<S>> historySecurityCurrencyList) {
      this.currentDate = currentDate;
      this.historySecurityCurrencyList = historySecurityCurrencyList;
    }

    public LocalDate getCurrentDate() {
      return currentDate;
    }

    public List<SecurityCurrencyMaxHistoryquoteData<S>> getHistorySecurityCurrencyList() {
      return historySecurityCurrencyList;
    }
  }

  private HistoryquoteDataChange createWithHistoryQuoteWithConnector(
      final ISecuritycurrencyService<S> securitycurrencyService, final S securitycurrency,
      final IFeedConnector feedConnector, final LocalDate fromDate, final LocalDate toDate, boolean needGapFiller)
      throws Exception {

    HistoryquoteDataChange hdc = new HistoryquoteDataChange(getCorrectedFromDate(securitycurrency, fromDate));
    hdc.toDateCalc = (toDate == null) ? LocalDate.now() : toDate;
    LocalDate correctedFromDateGapFill = securitycurrency instanceof Security
        ? getFirstGapFillByAfterLastRealEOD((Security) securitycurrency, needGapFiller, hdc.correctedFromDate)
        : hdc.correctedFromDate;

    hdc.historyquotes = historyquoteEntityAccess.getHistoryQuote(securitycurrency,
        substractSomeDays(
            correctedFromDateGapFill.isBefore(hdc.correctedFromDate) ? correctedFromDateGapFill : hdc.correctedFromDate,
            hdc.toDateCalc),
        hdc.toDateCalc, feedConnector);

    hdc.removeFromDate = removeFillGap(securitycurrency, hdc.historyquotes, needGapFiller, hdc.correctedFromDate,
        correctedFromDateGapFill);

    hdc.historyquotes = hdc.historyquotes.stream().parallel()
        .filter(historyquote -> hdc.removeFromDate != null || !historyquote.getDate().isBefore(hdc.correctedFromDate))
        .peek(h -> h.setIdSecuritycurrency(securitycurrency.getIdSecuritycurrency())).collect(Collectors.toList());

    return hdc;

  }

  private LocalDate removeFillGap(final S securitycurrency, List<Historyquote> historyquotes, boolean needGapFiller,
      LocalDate correctedFromDate, LocalDate correctedFromDateGapFill) {
    boolean hasGapFillRemoved = needGapFiller && correctedFromDateGapFill.isBefore(correctedFromDate)
        && !historyquotes.isEmpty() && historyquotes.get(0).getDate().isBefore(correctedFromDate);
    return hasGapFillRemoved ? historyquotes.get(0).getDate() : null;
  }

  /**
   * For the area of the recent EOD gap previously felt by the system, there could now be one or more real EODs.
   * Therefore, the data back to the most recent real EOD is requested again by the connector.
   *
   * @param security
   * @param needGapFiller
   * @param toDateCalc
   * @return
   */
  private LocalDate getFirstGapFillByAfterLastRealEOD(Security security, boolean needGapFiller, LocalDate correctedFromDate) {
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
   * Some data provider cause an error, if there is not minimum of days between the two dates. For this reason, some
   * days are subtract form the older date.
   *
   * @param fromDate
   * @param toDate
   * @return
   */
  private LocalDate substractSomeDays(final LocalDate fromDate, final LocalDate toDate) {
    LocalDate adjustedFromDate = fromDate;
    if (ChronoUnit.DAYS.between(toDate, LocalDate.now()) < 3) {
      final long daysBetweenFromTo = ChronoUnit.DAYS.between(adjustedFromDate, toDate);
      if (daysBetweenFromTo <= 2) {
        adjustedFromDate = adjustedFromDate.minusDays(5);
      }
    }
    return adjustedFromDate;
  }

  @Override
  public HistoryquoteQualityHead getHistoryquoteQualityHead(HistoryquoteQualityGrouped groupedBy,
      SecurityJpaRepository securityJpaRepository, MessageSource messages) {

    Optional<Globalparameters> globalparameters = globalparametersService
        .getGlobalparametersByProperty(GlobalParamKeyDefault.GLOB_KEY_HISTORYQUOTE_QUALITY_UPDATE_DATE);

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
            historyquoteQualityFlat.getIdConnectorHistory(), IFeedConnector.FeedSupport.FS_HISTORY);
        if (ifeedConnector == null) {
          ifeedConnector = ConnectorHelper.getConnectorByConnectorId(this.feedConnectorbeans,
              historyquoteQualityFlat.getIdConnectorHistory(), IFeedConnector.FeedSupport.FS_INTRA);
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
    public LocalDate correctedFromDate;
    public LocalDate toDateCalc;
    public List<Historyquote> historyquotes;
    public LocalDate removeFromDate;

    public HistoryquoteDataChange(LocalDate correctedFromDate) {
      this.correctedFromDate = correctedFromDate;
    }
  }
}
