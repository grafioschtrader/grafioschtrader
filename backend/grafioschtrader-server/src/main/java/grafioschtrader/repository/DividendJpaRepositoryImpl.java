package grafioschtrader.repository;

import java.text.ParseException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import grafioschtrader.GlobalConstants;
import grafioschtrader.common.DateHelper;
import grafioschtrader.connector.ConnectorHelper;
import grafioschtrader.connector.instrument.IFeedConnector;
import grafioschtrader.entities.Dividend;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.Securitysplit;
import grafioschtrader.types.CreateType;

public class DividendJpaRepositoryImpl implements DividendJpaRepositoryCustom {

  @Autowired
  private DividendJpaRepository dividendJpaRepository;

  @Autowired(required = false)
  private List<IFeedConnector> feedConnectors = new ArrayList<>();

  @Autowired
  private SecurityJpaRepository securityJpaRepository;

  @Autowired
  private GlobalparametersJpaRepository globalparametersJpaRepository;

  @Autowired
  private SecuritysplitJpaRepository securitysplitJpaRepository;

  private final Logger log = LoggerFactory.getLogger(this.getClass());

  @Override
  public List<String> periodicallyUpdate() {
    List<String> errorMessages = new ArrayList<>();
    List<Integer> securityIds = dividendJpaRepository.getIdSecurityForPeriodicallyUpdate(
        GlobalConstants.DIVIDEND_FREQUENCY_PLUS_DAY, globalparametersJpaRepository.getMaxDividendRetry());
    List<Security> securities = securityJpaRepository.findAllById(securityIds);
    Map<Integer, List<Dividend>> idSecurityDividendsMap = dividendJpaRepository
        .findByIdSecuritycurrencyInOrderByIdSecuritycurrencyAscExDateAsc(securityIds).stream()
        .collect(Collectors.groupingBy(Dividend::getIdSecuritycurrency, Collectors.toList()));
    List<Integer> idsSplitYoungerAsDividendList = dividendJpaRepository
        .getIdSecuritySplitAfterDividendWhenAdjusted(getDividendAdjustedConnectorsId(), securityIds);

    for (Security security : securities) {
      List<String> errorMessagesSecurity = loadAllDividendDataFromConnectorAndUpdate(security,
          idSecurityDividendsMap.getOrDefault(security.getIdSecuritycurrency(), new ArrayList<Dividend>()),
          idsSplitYoungerAsDividendList.contains(security.getIdSecuritycurrency()));
      if (!errorMessagesSecurity.isEmpty()) {
        errorMessages.add("Name: " + security.getName() + " ISIN:" + security.getIsin());
        errorMessages.addAll(errorMessagesSecurity);
      }
    }
    return errorMessages;
  }

  private List<String> getDividendAdjustedConnectorsId() {
    return feedConnectors.stream().filter(IFeedConnector::isDividendSplitAdjusted).map(IFeedConnector::getID)
        .collect(Collectors.toList());
  }

  @Override
  public List<String> loadAllDividendDataFromConnector(Security security) {
    List<Dividend> existingDividends = dividendJpaRepository.findByIdSecuritycurrencyAndCreateTypeOrderByExDateAsc(
        security.getIdSecuritycurrency(), CreateType.ADD_MODIFIED_USER.getValue());
    List<String> errorMessages = loadAllDividendDataFromConnectorAndUpdate(security, existingDividends, true);
    return errorMessages;
  }

  private List<String> loadAllDividendDataFromConnectorAndUpdate(Security security, List<Dividend> existingDividends,
      boolean replaceAlways) {
    List<String> errorMessages = new ArrayList<>();
    short retryDividendLoad = security.getRetryDividendLoad();
    try {
      IFeedConnector connector = ConnectorHelper.getConnectorByConnectorId(feedConnectors,
          security.getIdConnectorDividend(), IFeedConnector.FeedSupport.DIVIDEND);
      List<Dividend> dividendsRead = connector.getDividendHistory(security,
          LocalDate.parse(GlobalConstants.OLDEST_TRADING_DAY));
      retryDividendLoad = 0;
      security.setDividendEarliestNextCheck(
          DateHelper.setTimeToZeroAndAddDay(new Date(), GlobalConstants.DIVIDEND_FROM_NOW_FOR_NEXT_CHECK_IN_DAYS));
      if (!replaceAlways && dividendsRead.size() == existingDividends.size() || dividendsRead.isEmpty()
          || (!existingDividends.isEmpty() && dividendsRead.get(dividendsRead.size() - 1).getExDate()
              .equals(existingDividends.get(existingDividends.size() - 1)))) {
        securityJpaRepository.save(security);
        return errorMessages;
      }
      splitAdjustDividends(security.getIdSecuritycurrency(), dividendsRead, connector.isDividendSplitAdjusted());
      updateDividendData(security, dividendsRead, existingDividends);
    } catch (ParseException pe) {
      log.error(pe.getMessage() + "Offset: " + pe.getErrorOffset(), pe);
      errorMessages.add(pe.getMessage());
    } catch (final Exception ex) {
      retryDividendLoad++;
      log.error(ex.getMessage() + " " + security, ex);
      errorMessages.add(ExceptionUtils.getStackTrace(ex));
    }
    security.setRetryDividendLoad(retryDividendLoad);
    securityJpaRepository.save(security);
    return errorMessages;
  }

  private void updateDividendData(Security security, List<Dividend> dividendsRead, List<Dividend> existingDividends) {
    dividendJpaRepository.deleteByIdSecuritycurrencyAndCreateType(security.getIdSecuritycurrency(),
        CreateType.CONNECTOR_CREATED.getValue());

    DividendSplitsHelper.updateDividendSplitData(security, dividendsRead, existingDividends,
        this.dividendJpaRepository);
  }

  private void splitAdjustDividends(Integer idSecurity, List<Dividend> dividendsRead,
      boolean isSplitAdjusted) {
    List<Securitysplit> securitysplitList = securitysplitJpaRepository
        .findByIdSecuritycurrencyOrderBySplitDateAsc(idSecurity);

    for (Dividend dividend : dividendsRead) {
      double factor = Securitysplit.calcSplitFatorForFromDate(securitysplitList,
          DateHelper.getDateFromLocalDate(dividend.getExDate()));
      if(isSplitAdjusted) {
        dividend.setAmount(dividend.getAmountAdjusted() * factor);
      } else {
        dividend.setAmountAdjusted(dividend.getAmount() / factor);
      }
    }
  }
}
