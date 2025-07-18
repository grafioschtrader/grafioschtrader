package grafioschtrader.repository;

import java.lang.reflect.InvocationTargetException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.commons.beanutils.BeanUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

import grafiosch.BaseConstants;
import grafiosch.common.DateHelper;
import grafiosch.common.UserAccessHelper;
import grafiosch.entities.TaskDataChange;
import grafiosch.entities.User;
import grafiosch.repository.TaskDataChangeJpaRepository;
import grafiosch.types.TaskDataExecPriority;
import grafioschtrader.GlobalConstants;
import grafioschtrader.connector.ConnectorHelper;
import grafioschtrader.connector.instrument.IFeedConnector;
import grafioschtrader.connector.instrument.IFeedConnector.FeedSupport;
import grafioschtrader.dto.InstrumentStatisticsResult;
import grafioschtrader.entities.Historyquote;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.SecurityDerivedLink;
import grafioschtrader.entities.Securitysplit;
import grafioschtrader.priceupdate.ThruCalculationHelper;
import grafioschtrader.priceupdate.historyquote.BaseHistoryquoteThru;
import grafioschtrader.priceupdate.historyquote.HistoryquoteThruCalculation;
import grafioschtrader.priceupdate.historyquote.HistoryquoteThruConnector;
import grafioschtrader.priceupdate.historyquote.IHistoryquoteLoad;
import grafioschtrader.priceupdate.historyquote.SecurityCurrencyMaxHistoryquoteData;
import grafioschtrader.priceupdate.intraday.IIntradayLoad;
import grafioschtrader.priceupdate.intraday.IntradayThruCalculation;
import grafioschtrader.priceupdate.intraday.IntradayThruConnector;
import grafioschtrader.reports.InstrumentStatisticsSummary;
import grafioschtrader.reportviews.historyquotequality.HistoryquoteQualityGrouped;
import grafioschtrader.reportviews.historyquotequality.HistoryquoteQualityHead;
import grafioschtrader.reportviews.securityaccount.SecurityPositionSummary;
import grafioschtrader.reportviews.securitycurrency.SecuritycurrencyPosition;
import grafioschtrader.repository.SecurityJpaRepository.SplitAdjustedHistoryquotes;
import grafioschtrader.repository.SecurityJpaRepository.SplitAdjustedHistoryquotesResult;
import grafioschtrader.rest.RequestGTMappings;
import grafioschtrader.search.SecuritySearchBuilder;
import grafioschtrader.search.SecuritycurrencySearch;
import grafioschtrader.types.AssetclassType;
import grafioschtrader.types.HistoryquoteCreateType;
import grafioschtrader.types.TaskTypeExtended;
import jakarta.annotation.PostConstruct;

public class SecurityJpaRepositoryImpl extends SecuritycurrencyService<Security, SecurityPositionSummary>
    implements IPositionCloseOnLatestPrice<Security, SecurityPositionSummary>, SecurityJpaRepositoryCustom {

  private IHistoryquoteLoad<Security> historyquoteThruCalculation;
  private IIntradayLoad<Security> intradayThruCalculation;

  @Autowired
  private SecurityJpaRepository securityJpaRepository;

  @Autowired
  protected CurrencypairJpaRepository currencypairJpaRepository;

  @Autowired
  protected StockexchangeJpaRepository stockexchangeJpaRepository;

  @Autowired
  private SecurityDerivedLinkJpaRepository securityDerivedLinkJpaRepository;

  @Autowired
  private HistoryquotePeriodJpaRepository historyquotePeriodJpaRepository;

  @Autowired
  private TenantJpaRepository tenantJpaRepository;

  @Autowired
  private TaskDataChangeJpaRepository taskDataChangeJpaRepository;

  @Autowired
  private MessageSource messages;

  @Autowired
  protected JdbcTemplate jdbcTemplate;

  // Circular Dependency -> Lazy
  private HoldSecurityaccountSecurityJpaRepository holdSecurityaccountSecurityRepository;

  @Autowired
  public void setHoldSecurityaccountSecurityRepository(
      @Lazy final HoldSecurityaccountSecurityJpaRepository holdSecurityaccountSecurityRepository) {
    this.holdSecurityaccountSecurityRepository = holdSecurityaccountSecurityRepository;
  }

  @PostConstruct
  private void postConstruct() {
    historyquoteThruConnector = new HistoryquoteThruConnector<>(entityManager, globalparametersService,
        feedConnectorbeans, this, Security.class);
    historyquoteThruCalculation = new HistoryquoteThruCalculation<>(securityJpaRepository, historyquoteJpaRepository,
        securityDerivedLinkJpaRepository, globalparametersService, this);
    intradayThruConnector = new IntradayThruConnector<>(securityJpaRepository, globalparametersService,
        feedConnectorbeans, this);
    intradayThruCalculation = new IntradayThruCalculation<>(globalparametersService, securityJpaRepository,
        securityDerivedLinkJpaRepository);
  }

  ////////////////////////////////////////////////////////////////
  // Historical prices
  ////////////////////////////////////////////////////////////////

  @Override
  public List<Security> catchAllUpSecurityHistoryquote(List<Integer> idsStockexchange) {
    List<Security> securities = historyquoteThruConnector.catchAllUpSecuritycurrencyHistoryquote(idsStockexchange);
    securities.addAll(historyquoteThruCalculation.catchAllUpSecuritycurrencyHistoryquote(null));
    if (idsStockexchange == null || idsStockexchange.isEmpty()) {
      stockexchangeJpaRepository.updateHistoricalUpdateWithNowForAll();
    } else {
      stockexchangeJpaRepository.updateHistoricalUpdateWithNowByIdsStockexchange(idsStockexchange);
    }
    return securities;
  }

  @Override
  public void reloadAsyncFullHistoryquote(Security securitycurrency) {
    historyquoteThruConnector.reloadAsyncFullHistoryquote(securityServiceAsyncExectuion, this, securitycurrency);
  }

  @Override
  public void reloadAsyncFullHistoryquoteExternal(Integer idSecuritycurrency) {
    final User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    final Security security = securityJpaRepository.getReferenceById(idSecuritycurrency);
    if (!UserAccessHelper.hasRightsOrPrivilegesForEditingOrDelete(user, security)) {
      throw new SecurityException(BaseConstants.LIMIT_SECURITY_BREACH);
    }
    historyquoteThruConnector.reloadAsyncFullHistoryquote(securityServiceAsyncExectuion, this, security);
  }

  @Override
  public List<Security> tryUpToDateHistoricalDataWhenRetryHistoryLoadGreaterThan0(Integer idTenant,
      Integer idWatchlist) {
    return historyquoteThruConnector.fillHistoryquoteForSecuritiesCurrencies(
        securityJpaRepository.findByIdTenantAndIdWatchlistWhenRetryHistroyGreaterThan0(idTenant, idWatchlist),
        DateHelper.getCalendar(new Date()));
  }

  @Override
  @Transactional
  public HistoryquoteQualityHead getHistoryquoteQualityHead(HistoryquoteQualityGrouped groupedBy) {
    return this.historyquoteThruConnector.getHistoryquoteQualityHead(groupedBy, securityJpaRepository, messages);
  }

  /// call back for historyquoteThruConnector
  /////////////////////////////////////////////
  @Override
  public List<Historyquote> getHistoryQuote(final Security security, final Date fromDate, final Date toDate,
      final IFeedConnector feedConnector) throws Exception {
    return feedConnector.getEodSecurityHistory(security, fromDate, toDate);
  }

  @Override
  @Transactional
  @Modifying
  public Security rebuildSecurityCurrencypairHisotry(Security security) {
    historyquoteJpaRepository.removeAllSecurityHistoryquote(security.getIdSecuritycurrency());
    return getHistorquoteLoad(security).createHistoryQuotesAndSave(securityJpaRepository, security, null, null);
  }

  @Override
  @Transactional
  @Modifying
  public Security catchUpSecurityCurrencypairHisotry(Security security, final Date fromDate, final Date toDate) {
    security = securityJpaRepository.findByIdSecuritycurrency(security.getIdSecuritycurrency());
    return getHistorquoteLoad(security).createHistoryQuotesAndSave(securityJpaRepository, security, fromDate, toDate);
  }

  @Override
  protected boolean historyNeedToBeReloaded(final Security securityCurrencyChanged, final Security targetSecurity) {
    if (securityCurrencyChanged.getActiveToDate().getTime() <= System.currentTimeMillis()) {
      return false;
    }
    if (securityCurrencyChanged.isDerivedInstrument()) {
      return hasDerivedFieldsChanged(securityCurrencyChanged, targetSecurity)
          || activeFromDateWasSetToOlder(securityCurrencyChanged, targetSecurity);
    } else {
      return !(Objects.equals(securityCurrencyChanged.getIdConnectorHistory(), targetSecurity.getIdConnectorHistory())
          && Objects.equals(securityCurrencyChanged.getUrlHistoryExtend(), targetSecurity.getUrlHistoryExtend()))
          || activeFromDateWasSetToOlder(securityCurrencyChanged, targetSecurity);
    }
  }

  /**
   * Checks if the active from date was set to an earlier date than the existing security.
   * Used to determine if history data needs to be reloaded when the active period is extended backwards.
   * 
   * @param securityCurrencyChanged the security with potential changes
   * @param targetSecurity the original security to compare against
   * @return true if active from date was moved to an earlier date, false otherwise
   */
  private boolean activeFromDateWasSetToOlder(final Security securityCurrencyChanged, final Security targetSecurity) {
    return securityCurrencyChanged.getActiveFromDate().before(targetSecurity.getActiveFromDate());
  }


  /**
   * Checks if the derived fields of a security have changed between two instances. Derived fields include formula, the
   * base linked security/currency ID, and the set of security derived links.
   *
   * @param securityCurrencyChanged The security instance with potential changes.
   * @param targetSecurity          The original security instance to compare against.
   * @return {@code true} if any of the derived fields have changed, {@code false} otherwise.
   */
  private boolean hasDerivedFieldsChanged(final Security securityCurrencyChanged, final Security targetSecurity) {
    boolean isEqual = Objects.equals(securityCurrencyChanged.getFormulaPrices(), targetSecurity.getFormulaPrices())
        && Objects.equals(securityCurrencyChanged.getIdLinkSecuritycurrency(),
            targetSecurity.getIdLinkSecuritycurrency())
        && securityCurrencyChanged.getSecurityDerivedLinks().length == targetSecurity.getSecurityDerivedLinks().length;

    Arrays.sort(securityCurrencyChanged.getSecurityDerivedLinks(),
        Comparator.comparingInt(SecurityDerivedLink::getIdLinkSecuritycurrency));
    Arrays.sort(targetSecurity.getSecurityDerivedLinks(),
        Comparator.comparingInt(SecurityDerivedLink::getIdLinkSecuritycurrency));

    for (int i = 0; isEqual && i < securityCurrencyChanged.getSecurityDerivedLinks().length; i++) {
      isEqual = securityCurrencyChanged.getSecurityDerivedLinks()[i]
          .equals(targetSecurity.getSecurityDerivedLinks()[i]);
    }
    return !isEqual;
  }

  @Override
  public void setDividendDownloadLink(SecuritycurrencyPosition<Security> securitycurrencyPosition) {
    if (securitycurrencyPosition.securitycurrency.getIdConnectorDividend() != null) {
      IFeedConnector feedConnector = ConnectorHelper.getConnectorByConnectorId(feedConnectorbeans,
          securitycurrencyPosition.securitycurrency.getIdConnectorDividend(), IFeedConnector.FeedSupport.FS_DIVIDEND);
      securitycurrencyPosition.dividendUrl = ConnectorHelper.canAccessConnectorApiKey(feedConnector)
          ? feedConnector.getDividendHistoricalDownloadLink(securitycurrencyPosition.securitycurrency)
          : getDownlinkDivSplitWithApiKey(securitycurrencyPosition.securitycurrency, true);
    }
  }

  @Override
  public void setSplitDownloadLink(SecuritycurrencyPosition<Security> securitycurrencyPosition) {
    if (securitycurrencyPosition.securitycurrency.getIdConnectorSplit() != null) {
      IFeedConnector feedConnector = ConnectorHelper.getConnectorByConnectorId(feedConnectorbeans,
          securitycurrencyPosition.securitycurrency.getIdConnectorSplit(), IFeedConnector.FeedSupport.FS_SPLIT);
      securitycurrencyPosition.splitUrl = ConnectorHelper.canAccessConnectorApiKey(feedConnector)
          ? feedConnector.getSplitHistoricalDownloadLink(securitycurrencyPosition.securitycurrency)
          : getDownlinkDivSplitWithApiKey(securitycurrencyPosition.securitycurrency, false);
    }
  }

  /**
   * The URL for accessing data providers with an API key cannot be returned to unauthorized users. Therefore, this
   * method returns a link to this backend. The backend can then use this link to execute the request with the provider
   * itself and return the result to the frontend. This is used to handle links for dividend and split data.
   * 
   * @param security The security which download link is required
   * @param isDiv    true for dividend data and false for stock split data * @return A String representing a relative
   *                 URL that redirects the request through the backend to the appropriate data provider, ensuring API
   *                 keys are not exposed.
   */
  public String getDownlinkDivSplitWithApiKey(Security security, boolean isDiv) {
    return GlobalConstants.PREFIX_FOR_DOWNLOAD_REDIRECT_TO_BACKEND + RequestGTMappings.WATCHLIST_MAP
        + RequestGTMappings.SECURITY_DATAPROVIDER_DIV_SPLIT_HISTORICAL_RESPONSE + security.getIdSecuritycurrency()
        + "?isDiv=" + isDiv;
  }

  @Override
  protected IHistoryquoteLoad<Security> getHistorquoteLoad(Security security) {
    return security.isDerivedInstrument() ? historyquoteThruCalculation : historyquoteThruConnector;
  }

  @Override
  public String getDataProviderResponseForUser(final Integer idSecuritycurrency, final boolean isIntraday) {
    ConnectorData<Security> ct = getConnectorData(idSecuritycurrency, isIntraday, securityJpaRepository);
    return ct.feedConnector
        .getContentOfPageRequest(isIntraday ? ct.feedConnector.getSecurityIntradayDownloadLink(ct.securitycurrency)
            : ct.feedConnector.getSecurityHistoricalDownloadLink(ct.securitycurrency));
  }

  @Override
  public String getDataProviderLinkForUser(final Integer idSecuritycurrency, final boolean isIntraday) {
    ConnectorData<Security> ct = getConnectorData(idSecuritycurrency, isIntraday, securityJpaRepository);
    return isIntraday ? intradayThruConnector.createDownloadLink(ct.securitycurrency, ct.feedConnector)
        : historyquoteThruConnector.createDownloadLink(ct.securitycurrency, ct.feedConnector);
  }

  @Override
  public String getDivSplitProviderResponseForUser(final Integer idSecuritycurrency, final boolean isDiv) {
    Security security = securityJpaRepository.getReferenceById(idSecuritycurrency);
    IFeedConnector feedConnector = ConnectorHelper.getConnectorByConnectorId(feedConnectorbeans,
        security.getIdConnectorDividend(),
        isDiv ? IFeedConnector.FeedSupport.FS_DIVIDEND : IFeedConnector.FeedSupport.FS_SPLIT);
    return feedConnector.getContentOfPageRequest(isDiv ? feedConnector.getDividendHistoricalDownloadLink(security)
        : feedConnector.getSplitHistoricalDownloadLink(security));
  }

  ////////////////////////////////////////////////////////////////
  // Intraday prices
  ////////////////////////////////////////////////////////////////

  @Override
  public void updateAllLastPrices() {
    List<Security> securities = securityJpaRepository.findAll();
    Date now = new Date();
    intradayThruConnector.updateLastPriceOfSecuritycurrency(securities.stream()
        .filter(s -> !s.isDerivedInstrument() && !now.after(s.getActiveToDate()) && !now.before(s.getActiveFromDate())
            && s.getRetryIntraLoad() < globalparametersService.getMaxIntraRetry() && s.getIdConnectorIntra() != null)
        .collect(Collectors.toList()), true);
    intradayThruCalculation.updateLastPriceOfSecuritycurrency(
        securities.stream().filter(Security::isDerivedInstrument).collect(Collectors.toList()), true);
  }

  @Override
  public List<Security> updateLastPriceByList(List<Security> securities) {
    return updateLastPriceByList(securities, false);
  }

  private List<Security> updateLastPriceByList(List<Security> securities, boolean singleThread) {
    List<Security> securitiesRc = intradayThruConnector.updateLastPriceOfSecuritycurrency(
        securities.stream().filter(s -> !s.isDerivedInstrument()).collect(Collectors.toList()), singleThread);
    securitiesRc.addAll(intradayThruCalculation.updateLastPriceOfSecuritycurrency(
        securities.stream().filter(Security::isDerivedInstrument).collect(Collectors.toList()), singleThread));
    return securitiesRc;
  }

  @Override
  protected Security updateLastPriceSecurityCurrency(final Security security, final short maxIntraRetry,
      final int scIntradayUpdateTimeout) {
    return getIntradayLoad(security).updateLastPriceSecurityCurrency(security, maxIntraRetry, scIntradayUpdateTimeout);
  }

  /// call back for intradayThruConnector
  /////////////////////////////////////////////
  @Override
  public void updateIntraSecurityCurrency(final Security securitycurrency, final IFeedConnector feedConcector)
      throws Exception {
    feedConcector.updateSecurityLastPrice(securitycurrency);
  }

  protected IIntradayLoad<Security> getIntradayLoad(Security security) {
    return security.isDerivedInstrument() ? intradayThruCalculation : intradayThruConnector;
  }

  ////////////////////////////////////////////////////////////////
  // General procedures
  ////////////////////////////////////////////////////////////////
  @Override
  public SecurityCurrencypairJpaRepository<Security> getJpaRepository() {
    return securityJpaRepository;
  }

  @Override
  public void calculatePositionClose(final SecurityPositionSummary securityPositionSummary, final Double lastPrice) {
    securityPositionSummary.calcGainLossByPrice(lastPrice);
  }

  @Override
  public List<SecurityPositionSummary> processOpenPositionsWithActualPrice(final Date untilDate,
      final Map<Security, SecurityPositionSummary> summarySecurityMap) {

    final List<SecurityPositionSummary> securityPositionSummaryList = summarySecurityMap.entrySet().stream()
        .filter(map -> map.getValue().units != 0).map(map -> map.getValue()).collect(Collectors.toList());
    if (!securityPositionSummaryList.isEmpty()) {
      this.securityJpaRepository.calcGainLossBasedOnDateOrNewestPrice(securityPositionSummaryList, untilDate);
    }
    return securityPositionSummaryList;
  }

  @Override
  public void calcGainLossBasedOnDateOrNewestPrice(final List<SecurityPositionSummary> securitycurrencyPositionSummary,
      final Date untilDate) {
    super.calcGainLossBasedOnDateOrNewestPrice(securitycurrencyPositionSummary, this, untilDate);
  }

  @Override
  public List<SecurityCurrencyMaxHistoryquoteData<Security>> getMaxHistoryquoteResult(final short maxHistoryRetry,
      BaseHistoryquoteThru<Security> baseHistoryquoteThru, List<Integer> idsStockexchange) {
    if (baseHistoryquoteThru instanceof HistoryquoteThruCalculation) {
      return securityJpaRepository.getMaxHistoryquoteWithCalculation(maxHistoryRetry);
    } else {
      return idsStockexchange != null && idsStockexchange.size() > 0
          ? securityJpaRepository.getMaxHistoryquoteWithConnectorForExchange(maxHistoryRetry, idsStockexchange)
          : securityJpaRepository.getMaxHistoryquoteWithConnector(maxHistoryRetry);
    }
  }

  @Override
  public List<Security> searchByCriteria(final SecuritycurrencySearch securitycurrencySearch) {
    final User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    return searchBuilderWithExclusion(null, null, securitycurrencySearch, user.getIdTenant());
  }

  @Override
  public List<Security> searchBuilderWithExclusion(final Integer idWatchlist, Integer idCorrelationSet,
      final SecuritycurrencySearch securitycurrencySearch, final Integer idTenant) {
    if (securitycurrencySearch.getAssetclassType() == AssetclassType.CURRENCY_PAIR) {
      return Collections.emptyList();
    } else {
      List<Integer> idSecurityList = null;
      if (securitycurrencySearch.getWithHoldings()) {
        idSecurityList = this.holdSecurityaccountSecurityRepository.getIdSecurityByIdTenantWithHoldings(idTenant);
      }
      return this.securityJpaRepository.findAll(
          new SecuritySearchBuilder(idWatchlist, idCorrelationSet, securitycurrencySearch, idTenant, idSecurityList));
    }
  }

  @Override
  public List<Security> tryUpToIntradayDataWhenRetryIntraLoadGreaterThan0(Integer idTenant, Integer idWatchlist) {
    return intradayThruConnector.updateLastPriceOfSecuritycurrency(securityJpaRepository
        .findWithConnectorByIdTenantAndIdWatchlistWhenRetryIntraGreaterThan0(idTenant, idWatchlist), (short) -1, false);
  }

  @Override
  public List<Security> getTradableSecuritiesByTenantAndIdWatschlist(Integer idWatchlist) throws ParseException {
    final User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    return securityJpaRepository.getTradableSecuritiesByTenantAndIdWatschlist(user.getIdTenant(), idWatchlist);

  }

  @Override
  public List<Security> findByActiveToDateGreaterThanEqualOrderByName(final String dateString) throws ParseException {
    final User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    final Date untilDate = new SimpleDateFormat(GlobalConstants.SHORT_STANDARD_DATE_FORMAT).parse(dateString);
    return securityJpaRepository.findByActiveToDateGreaterThanEqualAndIdTenantPrivateIsNullOrIdTenantPrivateOrderByName(
        untilDate, user.getIdTenant());
  }

  @Override
  public SplitAdjustedHistoryquotesResult isLatestSplitHistoryquotePossibleAdjusted(Security security,
      List<Securitysplit> securitysplits) throws Exception {

    SplitAdjustedHistoryquotesResult sahr = new SplitAdjustedHistoryquotesResult(
        SplitAdjustedHistoryquotes.NOT_DETCTABLE, null);
    Optional<Securitysplit> maxSecuritysplitOpt = securitysplits.stream()
        .max(Comparator.comparing(Securitysplit::getSplitDate));
    if (!maxSecuritysplitOpt.isEmpty()) {
      Securitysplit youngestSplit = maxSecuritysplitOpt.get();

      Date fromDate = DateHelper.setTimeToZeroAndAddDay(youngestSplit.getSplitDate(),
          GlobalConstants.SPLIT_DAYS_LOCK_BACK_START_DATE * -1);
      Date toDate = DateHelper.setTimeToZeroAndAddDay(youngestSplit.getSplitDate(),
          GlobalConstants.SPLIT_DAYS_LOOK_BACK_END_DATE_BEFORE_SPLIT * -1);

      List<Historyquote> hqConnectorList = getDataByConnnector(security, fromDate, toDate);
      List<Historyquote> hqPersistentList = historyquoteJpaRepository
          .findByIdSecuritycurrencyAndDateBetweenOrderByDate(security.getIdSecuritycurrency(), fromDate, toDate);

      Map<Date, Historyquote> hqPersistentMap = hqPersistentList.stream()
          .collect(Collectors.toMap(Historyquote::getDate, Function.identity()));

      int differentCloseCount = 0;
      int theSameCloseCount = 0;
      for (Historyquote hqConnector : hqConnectorList) {
        Historyquote hqPersistent = hqPersistentMap.get(hqConnector.getDate());
        if (hqPersistent != null) {
          if (hqPersistent.getClose() != hqConnector.getClose()) {
            differentCloseCount++;
          } else {
            theSameCloseCount++;
          }
        }
      }
      sahr.sah = (differentCloseCount > 0 && theSameCloseCount == 0) ? SplitAdjustedHistoryquotes.ADJUSTED_NOT_LOADED
          : SplitAdjustedHistoryquotes.NOT_DETCTABLE;
      if (sahr.sah == SplitAdjustedHistoryquotes.NOT_DETCTABLE) {
        sahr.addDaysForNextAttempt = getNextAttemptInDaysForSplitHistorical(security, youngestSplit.getSplitDate());
      }
    }
    return sahr;
  }

  private Integer getNextAttemptInDaysForSplitHistorical(Security security, Date splitDate) throws Exception {
    IFeedConnector feedConnector = ConnectorHelper.getConnectorByConnectorId(feedConnectorbeans,
        security.getIdConnectorHistory(), IFeedConnector.FeedSupport.FS_HISTORY);
    return feedConnector.getNextAttemptInDaysForSplitHistorical(splitDate);
  }

  private List<Historyquote> getDataByConnnector(Security security, Date fromDate, Date toDate) throws Exception {
    IFeedConnector feedConnector = ConnectorHelper.getConnectorByConnectorId(feedConnectorbeans,
        security.getIdConnectorHistory(), IFeedConnector.FeedSupport.FS_HISTORY);
    return getHistoryQuote(security, fromDate, toDate, feedConnector);
  }

  @Override
  protected Security beforeSave(Security security, Security existingSecurity, User user) throws Exception {
    Security cloneSecurity = null;
    if (security.getIdTenantPrivate() != null && !user.getIdTenant().equals(security.getIdTenantPrivate())) {
      throw new SecurityException(BaseConstants.CLIENT_SECURITY_BREACH);
    }
    checkAssetclassAndSpezInstrumentForExisting(security, existingSecurity);
    security.clearProperties();
    ThruCalculationHelper.checkFormulaAgainstInstrumetLinks(security, user.getLocaleStr());
    if (existingSecurity != null && existingSecurity.isDerivedInstrument()) {
      setSecurityDerivedLinks(security, existingSecurity);
    }
    if (existingSecurity != null) {
      try {
        cloneSecurity = new Security();
        BeanUtils.copyProperties(cloneSecurity, existingSecurity);
      } catch (IllegalAccessException | InvocationTargetException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
    }

    return cloneSecurity;
  }

  private void checkAssetclassAndSpezInstrumentForExisting(Security security, Security existingSecurity) {
    if (existingSecurity != null && !security.isDerivedInstrument()) {
      if ((existingSecurity.getAssetClass().getSpecialInvestmentInstrument() != security.getAssetClass()
          .getSpecialInvestmentInstrument()
          || existingSecurity.getAssetClass().getCategoryType() != security.getAssetClass().getCategoryType())
          && securityJpaRepository.hasSecurityTransaction(existingSecurity.getIdSecuritycurrency())) {
        throw new IllegalArgumentException("Property financial instrument and asset class can no longer be changed!");
      }
    }
  }

  @Override
  public void checkAndClearSecuritycurrencyConnectors(final Security security) {
    super.checkAndClearSecuritycurrencyConnectors(security);
    if (security.getIdConnectorDividend() != null) {
      FeedSupport fd = IFeedConnector.FeedSupport.FS_DIVIDEND;
      IFeedConnector fc = ConnectorHelper.getConnectorByConnectorId(feedConnectorbeans,
          security.getIdConnectorDividend(), fd);
      fc.checkAndClearSecuritycurrencyUrlExtend(security, fd);
    }
    if (security.getIdConnectorSplit() != null) {
      FeedSupport fd = IFeedConnector.FeedSupport.FS_SPLIT;
      IFeedConnector fc = ConnectorHelper.getConnectorByConnectorId(feedConnectorbeans, security.getIdConnectorSplit(),
          fd);
      fc.checkAndClearSecuritycurrencyUrlExtend(security, fd);
    }
  }

  @Override
  protected void afterSave(Security security, Security securityBefore, User user, boolean historyAccessHasChanged) {
    if (historyAccessHasChanged) {
      taskDataChangeJpaRepository.save(new TaskDataChange(TaskTypeExtended.SECURITY_LOAD_HISTORICAL_INTRA_PRICE_DATA,
          TaskDataExecPriority.PRIO_NORMAL, LocalDateTime.now(), security.getIdSecuritycurrency(),
          Security.class.getSimpleName()));
    }

    if ((securityBefore != null
        && !(StringUtils.equals(security.getIdConnectorSplit(), securityBefore.getIdConnectorSplit())
            && StringUtils.equals(security.getUrlSplitExtend(), securityBefore.getUrlSplitExtend())))
        || (securityBefore == null && security.getIdConnectorSplit() != null)) {
      // Split connector has changed
      taskDataChangeJpaRepository.save(
          new TaskDataChange(TaskTypeExtended.SECURITY_SPLIT_UPDATE_FOR_SECURITY, TaskDataExecPriority.PRIO_NORMAL,
              LocalDateTime.now().plusMinutes(5), security.getIdSecuritycurrency(), Security.class.getSimpleName()));
    }

    if ((securityBefore != null
        && !(StringUtils.equals(security.getIdConnectorDividend(), securityBefore.getIdConnectorDividend())
            && StringUtils.equals(security.getUrlDividendExtend(), securityBefore.getUrlDividendExtend())))
        || (securityBefore == null && security.getIdConnectorDividend() != null)) {
      // Dividend connector has changed
      taskDataChangeJpaRepository.save(
          new TaskDataChange(TaskTypeExtended.SECURITY_DIVIDEND_UPDATE_FOR_SECURITY, TaskDataExecPriority.PRIO_NORMAL,
              LocalDateTime.now().plusMinutes(5), security.getIdSecuritycurrency(), Security.class.getSimpleName()));
    }
  }

  private void setSecurityDerivedLinks(Security security, Security existingSecurity) {
    List<SecurityDerivedLink> securityDerivedLinks = securityDerivedLinkJpaRepository
        .findByIdEmIdSecuritycurrencyOrderByIdEmIdSecuritycurrency(existingSecurity.getIdSecuritycurrency());
    existingSecurity
        .setSecurityDerivedLinks(securityDerivedLinks.toArray(new SecurityDerivedLink[securityDerivedLinks.size()]));
    for (int i = 0; i < security.getSecurityDerivedLinks().length; i++) {
      security.getSecurityDerivedLinks()[i].setIdSecuritycurrency(security.getIdSecuritycurrency());
    }
  }

  @Override
  public boolean checkUserCanChangeDerivedFields(User user, Security security, Security existingSecurity) {
    boolean hasRights = UserAccessHelper.hasRightsOrPrivilegesForEditingOrDelete(user, existingSecurity);
    if (!hasRights && existingSecurity != null && existingSecurity.isDerivedInstrument()) {
      setSecurityDerivedLinks(security, existingSecurity);
      if (!UserAccessHelper.hasRightsOrPrivilegesForEditingOrDelete(user, existingSecurity)
          && hasDerivedFieldsChanged(security, existingSecurity)) {
        throw new SecurityException(BaseConstants.FILED_EDIT_SECURITY_BREACH);
      }
    }
    return hasRights;
  }

  @Override
  protected Security afterMainEntitySaved(Security security, Security beforSaveSecuritycurrency) {
    if (security.isDerivedInstrument()) {
      securityDerivedLinkJpaRepository.deleteByIdEmIdSecuritycurrency(security.getIdSecuritycurrency());
      List<SecurityDerivedLink> securityDerivedLinks = Arrays
          .asList(beforSaveSecuritycurrency.getSecurityDerivedLinks());
      securityDerivedLinks
          .forEach(securityDerivedLink -> securityDerivedLink.setIdSecuritycurrency(security.getIdSecuritycurrency()));
      securityDerivedLinkJpaRepository.saveAll(Arrays.asList(beforSaveSecuritycurrency.getSecurityDerivedLinks()));
    } else if (security.getStockexchange().isNoMarketValue()) {
      historyquotePeriodJpaRepository.adjustHistoryquotePeriod(security);
    }
    return security;
  }

  @Override
  public InstrumentStatisticsResult getSecurityStatisticsReturnResult(Integer idSecuritycurrency, LocalDate dateFrom,
      LocalDate dateTo) throws IllegalAccessException, InvocationTargetException, NoSuchMethodException {
    var securityStatisticsSummary = new InstrumentStatisticsSummary(securityJpaRepository, tenantJpaRepository,
        currencypairJpaRepository);
    securityStatisticsSummary.prepareSecurityCurrencypairs(idSecuritycurrency);
    return new InstrumentStatisticsResult(securityStatisticsSummary.getAnnualisedSecurityPerformance(),
        securityStatisticsSummary.getStandardDeviation(jdbcTemplate, dateFrom, dateTo, false));
  }

  @Override
  public List<Historyquote> fillGap(Security security) {
    List<Date> missingDates = historyquoteJpaRepository.getMissingEODForSecurityByUpdCalendarIndex(
        security.getStockexchange().getIdIndexUpdCalendar(), security.getIdSecuritycurrency());
    List<Historyquote> historyquotesFill = new ArrayList<>();
    if (!missingDates.isEmpty()) {
      int missingDateCounter = 0;
      int historyIdxFirst = Collections.binarySearch(security.getHistoryquoteList(),
          new Historyquote(missingDates.get(0)), (h1, h2) -> h1.getDate().compareTo(h2.getDate())) * -1 - 1;
      if (historyIdxFirst > 0) {
        fillGapEODAfterFirstHistoryquote(security, historyquotesFill, historyIdxFirst, missingDates,
            missingDateCounter);
      } else {
        fillGapEODBeforeFirstHistoryquote(security, historyquotesFill, missingDates, missingDateCounter);
      }
    }
    return historyquotesFill;
  }

  /**
   * Fills gaps in End-Of-Day (EOD) history quotes for a security before its first recorded history quote. This method
   * is used when missing trading days are identified before the earliest available history quote for the security. It
   * assumes the closing price of the first available quote for subsequent missing dates until it reaches a point where
   * existing data takes over or all missing dates before the first quote are filled.
   *
   * @param security           The security for which to fill history quote gaps.
   * @param historyquotesFill  A list to which the newly created {@link Historyquote} objects will be added.
   * @param missingDates       A sorted list of dates for which EOD data is missing.
   * @param missingDateCounter The starting index in {@code missingDates} to process.
   */
  private void fillGapEODBeforeFirstHistoryquote(Security security, List<Historyquote> historyquotesFill,
      List<Date> missingDates, int missingDateCounter) {
    do {
      historyquotesFill
          .add(new Historyquote(security.getIdSecuritycurrency(), HistoryquoteCreateType.FILL_GAP_BY_CONNECTOR,
              missingDates.get(missingDateCounter), security.getHistoryquoteList().get(0).getClose()));
      missingDateCounter++;
    } while (missingDateCounter < missingDates.size()
        && security.getHistoryquoteList().get(0).getDate().after(missingDates.get(missingDateCounter)));
    fillGapEODAfterFirstHistoryquote(security, historyquotesFill, 0, missingDates, missingDateCounter);
  }

  /**
   * Fills gaps in End-Of-Day (EOD) history quotes for a security after its first recorded history quote or within
   * existing recorded history quotes. This method iterates through missing dates and uses the closing price of the last
   * known history quote to fill the subsequent missing dates until the next available recorded quote is encountered or
   * all missing dates are filled.
   *
   * @param security           The security for which to fill history quote gaps.
   * @param historyquotesFill  A list to which the newly created {@link Historyquote} objects will be added.
   * @param historyIdx         The starting index in the security's existing {@code historyquoteList} to consider.
   * @param missingDates       A sorted list of dates for which EOD data is missing.
   * @param missingDateCounter The starting index in {@code missingDates} to process.
   */
  private void fillGapEODAfterFirstHistoryquote(Security security, List<Historyquote> historyquotesFill, int historyIdx,
      List<Date> missingDates, int missingDateCounter) {
    List<Historyquote> hql = security.getHistoryquoteList();
    while (missingDateCounter < missingDates.size()) {
      if (historyIdx == hql.size()
          || historyIdx < hql.size() && missingDates.get(missingDateCounter).before(hql.get(historyIdx).getDate())) {
        historyquotesFill
            .add(new Historyquote(security.getIdSecuritycurrency(), HistoryquoteCreateType.FILL_GAP_BY_CONNECTOR,
                missingDates.get(missingDateCounter), hql.get(historyIdx - 1).getClose()));
        missingDateCounter++;
      } else {
        historyIdx = Collections.binarySearch(hql, new Historyquote(missingDates.get(missingDateCounter)),
            (h1, h2) -> h1.getDate().compareTo(h2.getDate())) * -1 - 1;
      }
    }
  }

  @Override
  public HistoryquoteJpaRepository getHistoryquoteJpaRepository() {
    return historyquoteJpaRepository;
  }

  @Override
  @Transactional
  public Map<Integer, String> getSecurityCurrencyPairInfo() {
    return securityJpaRepository.getAllTaskDataChangeSecurityCurrencyPairInfoWithId()
        .collect(Collectors.toMap(isi -> isi.getIdSecuritycurrency(), isi -> isi.getTooltip()));
  }

}
