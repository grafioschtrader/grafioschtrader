/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package grafioschtrader.repository;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.commons.lang3.SerializationUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.context.SecurityContextHolder;

import grafioschtrader.common.DataHelper;
import grafioschtrader.common.DateHelper;
import grafioschtrader.common.PropertyAlwaysUpdatable;
import grafioschtrader.common.PropertySelectiveUpdatableOrWhenNull;
import grafioschtrader.connector.ConnectorHelper;
import grafioschtrader.connector.instrument.IFeedConnector;
import grafioschtrader.connector.instrument.IFeedConnector.FeedSupport;
import grafioschtrader.dto.ISecuritycurrencyIdDateClose;
import grafioschtrader.dto.ValueKeyHtmlSelectOptions;
import grafioschtrader.entities.Securitycurrency;
import grafioschtrader.entities.User;
import grafioschtrader.priceupdate.historyquote.IHistoryquoteEntityAccess;
import grafioschtrader.priceupdate.historyquote.IHistoryquoteLoad;
import grafioschtrader.priceupdate.intraday.IIntradayEntityAccess;
import grafioschtrader.priceupdate.intraday.IIntradayLoad;
import grafioschtrader.reportviews.SecuritycurrencyPositionSummary;
import grafioschtrader.reportviews.securityaccount.SecurityPositionSummary;
import grafioschtrader.reportviews.securitycurrency.SecuritycurrencyPosition;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;


public abstract class SecuritycurrencyService<S extends Securitycurrency<S>, U extends SecuritycurrencyPositionSummary<S>>
    extends BaseRepositoryImpl<S>
    implements IHistoryquoteEntityAccess<S>, IIntradayEntityAccess<S>, ISecuritycurrencyService<S> {

  @Autowired
  protected SecurityServiceAsyncExectuion<S, U> securityServiceAsyncExectuion;

  @Autowired
  protected GlobalparametersJpaRepository globalparametersJpaRepository;

  @Autowired
  protected HistoryquoteJpaRepository historyquoteJpaRepository;
 
  @PersistenceContext
  protected EntityManager entityManager;

  protected IHistoryquoteLoad<S> historyquoteThruConnector;

  protected IIntradayLoad<S> intradayThruConnector;

  @Autowired(required = false)
  public List<IFeedConnector> feedConnectorbeans = new ArrayList<>();

 // public abstract JpaRepository<S, Integer> getJpaRepository();
  
  
  /**
   * Checks if the data provider was changed by the user and the EOD must be
   * reloaded.
   *
   * @param securityCurrencyChanged
   * @param securitycurreny2
   * @return
   */
  protected abstract boolean historyNeedToBeReloaded(S securityCurrencyChanged, S securitycurreny2);

  protected abstract IHistoryquoteLoad<S> getHistorquoteLoad(S securitycurrency);

  private boolean asyncHistoryquotes;

  @Value("${gt.security.async.historyquotes}")
  public void setApiKey(boolean asyncHistoryquotes) {
    this.asyncHistoryquotes = asyncHistoryquotes;
  }

  protected S createWithHistoryQuote(final S securitycurrency) throws Exception {
    return getHistorquoteLoad(securitycurrency).createHistoryQuotesAndSave(this, securitycurrency, null,
        null);
  }

 
  protected void reloadAsyncHistoryquotes(S createEditSecuritycurrency) {
    getHistorquoteLoad(createEditSecuritycurrency).reloadAsyncFullHistoryquote(securityServiceAsyncExectuion, this,
        createEditSecuritycurrency);
  }

  protected void afterFullLoad(final S securitycurrency) throws Exception {
  }

  @Override
  public void setSecuritycurrencyHistoricalDownloadLink(final SecuritycurrencyPosition<S> securitycurrencyPosition) {
    securitycurrencyPosition.historicalUrl = getHistorquoteLoad(securitycurrencyPosition.securitycurrency)
        .getSecuritycurrencyHistoricalDownloadLinkAsUrlStr(securitycurrencyPosition.securitycurrency);
  }

  protected S updateLastPriceSecurityCurrency(final S securitycurrency, final short maxIntraRetry,
      final int scIntradayUpdateTimeout) {
    return intradayThruConnector.updateLastPriceSecurityCurrency(securitycurrency, maxIntraRetry,
        scIntradayUpdateTimeout);
  }

  @Override
  public void setSecuritycurrencyIntradayDownloadLink(final SecuritycurrencyPosition<S> securitycurrencyPosition) {
    securitycurrencyPosition.intradayUrl = intradayThruConnector
        .getSecuritycurrencyIntraDownloadLinkAsUrlStr(securitycurrencyPosition.securitycurrency);
  }

  ////////////////////////////////////////////////////////////////
  // Gain and loose calculation
  ////////////////////////////////////////////////////////////////
  public void calcGainLossBasedOnDateOrNewestPrice(final U securitycurrencyPositionSummary,
      final IPositionCloseOnLatestPrice<S, U> positionCloseOnLatestPrice, final Date untilDate) {
    final List<U> securitycurrencyPositionSummaryList = new ArrayList<>();
    securitycurrencyPositionSummaryList.add(securitycurrencyPositionSummary);
    calcGainLossBasedOnDateOrNewestPrice(securitycurrencyPositionSummaryList, positionCloseOnLatestPrice, untilDate);
  }

  public void calcGainLossBasedOnDateOrNewestPrice(final List<U> securitycurrencyPositionSummaryList,
      final IPositionCloseOnLatestPrice<S, U> positionCloseOnLatestPrice, final Date untilDate) {
    final List<Integer> idSecurityList = securitycurrencyPositionSummaryList.stream()
        .map(openSecurityPosition -> openSecurityPosition.securitycurrency.getIdSecuritycurrency())
        .collect(Collectors.toList());

    if (!idSecurityList.isEmpty()) {
      final List<ISecuritycurrencyIdDateClose> queryResult = historyquoteJpaRepository
          .getIdDateCloseByIdsAndDate(idSecurityList, untilDate);
      final Map<Integer, ISecuritycurrencyIdDateClose> historyquotes = queryResult.stream()
          .collect(Collectors.toMap(ISecuritycurrencyIdDateClose::getIdSecuritycurrency, Function.identity()));
      for (final U securityPositionSummary : securitycurrencyPositionSummaryList) {
        final ISecuritycurrencyIdDateClose historyquote = historyquotes
            .get(securityPositionSummary.securitycurrency.getIdSecuritycurrency());

        Double price = securityPositionSummary.securitycurrency.getSLast();
        Date date = securityPositionSummary.securitycurrency.getSTimestamp();
        if (historyquote != null
            && (price == null || (historyquote.getDate().after(securityPositionSummary.securitycurrency.getSTimestamp())
                || untilDate.before(
                    DateHelper.setTimeToZeroAndAddDay(securityPositionSummary.securitycurrency.getSTimestamp(), 0))))) {
          price = historyquote.getClose();
          date = historyquote.getDate();
        }
        if (price == null) {
          price = (securityPositionSummary.closePrice != null) ? securityPositionSummary.closePrice : 0.0;
        }
        price = (securityPositionSummary instanceof SecurityPositionSummary)
            ? price * ((SecurityPositionSummary) securityPositionSummary).closePriceFactor
            : price;
        positionCloseOnLatestPrice.calculatePositionClose(securityPositionSummary, price);
        securityPositionSummary.closePrice = price;
        securityPositionSummary.closeDate = date;
      }
    }
  }

  @Override
  public List<IFeedConnector> getFeedConnectors(final boolean isCurrency) {
    return feedConnectorbeans.stream()
        .filter(
            (fc -> fc.isActivated() && (isCurrency && fc.supportsCurrency() || !isCurrency && fc.supportsSecurity())))
        .sorted(Comparator.comparing(IFeedConnector::getReadableName, String::compareToIgnoreCase))
        .collect(Collectors.toList());
  }

  @Override
  public List<ValueKeyHtmlSelectOptions> getAllFeedConnectorsAsKeyValue(FeedSupport feedSupport) {
    return feedConnectorbeans.stream().filter(f -> f.getSecuritycurrencyFeedSupport().containsKey(feedSupport))
        .sorted(Comparator.comparing(IFeedConnector::getReadableName, String::compareToIgnoreCase))
        .map(f -> new ValueKeyHtmlSelectOptions(f.getID(), f.getReadableName())).collect(Collectors.toList());
  }

  @Override
  @Transactional
  public S saveOnlyAttributes(final S securitycurrency, S existingEntity,
      final Set<Class<? extends Annotation>> updatePropertyLevelClasses) throws Exception {
    S createEditSecuritycurrency = existingEntity;
    final User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    boolean historyAccessHasChanged = securitycurrency.getIdSecuritycurrency() == null && asyncHistoryquotes;
    checkAndClearSecuritycurrencyConnectors(securitycurrency);
    S cloneSecuritycurrency = beforeSave(securitycurrency, existingEntity, user);
    if (existingEntity != null) {
      if (historyNeedToBeReloaded(securitycurrency, createEditSecuritycurrency)) {
        historyAccessHasChanged = true;
        createEditSecuritycurrency.setFullLoadTimestamp(null);
      }
      DataHelper.updateEntityWithUpdatable(securitycurrency, createEditSecuritycurrency, updatePropertyLevelClasses);
      createEditSecuritycurrency.clearUnusedFields();
    } else {
      createEditSecuritycurrency = securitycurrency;
    }

    S beforSaveSecuritycurrency = SerializationUtils.clone(createEditSecuritycurrency);
    createEditSecuritycurrency = afterMainEntitySaved(getJpaRepository().save(createEditSecuritycurrency),
        beforSaveSecuritycurrency);
    afterSave(createEditSecuritycurrency, cloneSecuritycurrency, user, historyAccessHasChanged);

    return createEditSecuritycurrency;
  }

  protected void checkAndClearSecuritycurrencyConnectors(final S securitycurrency) {
    if (securitycurrency.getIdConnectorIntra() != null) {
      FeedSupport fd = IFeedConnector.FeedSupport.INTRA;
      IFeedConnector fc = ConnectorHelper.getConnectorByConnectorId(feedConnectorbeans,
          securitycurrency.getIdConnectorIntra(), fd);
      fc.checkAndClearSecuritycurrencyUrlExtend(securitycurrency, fd);
    }
    if (securitycurrency.getIdConnectorHistory() != null) {
      FeedSupport fd = IFeedConnector.FeedSupport.HISTORY;
      IFeedConnector fc = ConnectorHelper.getConnectorByConnectorId(feedConnectorbeans,
          securitycurrency.getIdConnectorHistory(), fd);
      fc.checkAndClearSecuritycurrencyUrlExtend(securitycurrency, fd);
    }
  }

  protected S beforeSave(S securitycurrency, S existingEntity, User user) throws Exception {
    return null;
  }

  protected void afterSave(S securitycurrency, S cloneSecuritycurrency, User user, boolean historyAccessHasChanged) {
    if (historyAccessHasChanged) {
      reloadAsyncHistoryquotes(securitycurrency);
    }
  }

  protected S afterMainEntitySaved(S securitycurrency, S beforSaveSecuritycurrency) {
    return securitycurrency;
  }

  @Override
  public Set<Class<? extends Annotation>> getUpdatePropertyLevels(final S existingSecurity) {
    return existingSecurity == null ? Set.of(PropertySelectiveUpdatableOrWhenNull.class, PropertyAlwaysUpdatable.class)
        : Set.of(PropertySelectiveUpdatableOrWhenNull.class, PropertyAlwaysUpdatable.class);
  }

  
  
}
