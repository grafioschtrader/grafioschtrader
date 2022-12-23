package grafioschtrader.connector.instrument.euronext;

import java.net.URL;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

import grafioschtrader.GlobalConstants;
import grafioschtrader.common.DataHelper;
import grafioschtrader.common.DateHelper;
import grafioschtrader.connector.instrument.BaseFeedConnector;
import grafioschtrader.entities.Historyquote;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.Securitycurrency;
import grafioschtrader.exceptions.GeneralNotTranslatedWithArgumentsException;
import grafioschtrader.types.AssetclassType;
import grafioschtrader.types.SpecialInvestmentInstruments;

@Component
public class EuronextFeedConnector extends BaseFeedConnector {

  public static final String STOCK_EX_MIC_AMSTERDAM = "XAMS";
  public static final String STOCK_EX_MIC_BRUSSELS = "XBRU";
  public static final String STOCK_EX_MIC_DUBLIN = "XMSM";
  public static final String STOCK_EX_MIC_LISBON = "XLIS";
  public static final String STOCK_EX_MIC_OSLO = "XOSL";
  public static final String STOCK_EX_MIC_PARIS = "XPAR";

  public static final String STOCK_EX_OPERATION_DUBLIN = "XDUB";

  public static final Map<String, String> mappingMicOperation = new HashMap<>();
  private static final Set<String> EN_STOCK_EXCHANGES_SET = Set.of(STOCK_EX_MIC_AMSTERDAM, STOCK_EX_MIC_BRUSSELS,
      STOCK_EX_MIC_DUBLIN, STOCK_EX_MIC_LISBON, STOCK_EX_MIC_OSLO, STOCK_EX_MIC_PARIS);

  private static final String DOMAIN_NAME_WITH_CHART = "https://live.euronext.com/intraday_chart/getChartData/";
  private static final String PERIOD_1M = "1M";
  private static final String PERIOD_MAX = "max";
  private static final ObjectMapper objectMapper = new ObjectMapper();
  private static Map<FeedSupport, FeedIdentifier[]> supportedFeed;

  static {
    supportedFeed = new HashMap<>();
    supportedFeed.put(FeedSupport.HISTORY, new FeedIdentifier[] { FeedIdentifier.SECURITY });
    mappingMicOperation.put(STOCK_EX_MIC_DUBLIN, STOCK_EX_OPERATION_DUBLIN);
  }

  public EuronextFeedConnector() {
    super(supportedFeed, "euronext", "Euronext", null);
  }

  @Override
  public String getSecurityHistoricalDownloadLink(final Security security) {
    return getSecurityHistoricalDownloadLink(security, PERIOD_1M);
  }

  private String getSecurityHistoricalDownloadLink(final Security security, String periodSuffix) {
    return DOMAIN_NAME_WITH_CHART + security.getIsin() + "-"
        + mappingMicOperation.getOrDefault(security.getStockexchange().getMic(), security.getStockexchange().getMic())
        + "/" + periodSuffix;
  }

  @Override
  protected <S extends Securitycurrency<S>> boolean checkAndClearSecuritycurrencyConnector(
      Securitycurrency<S> securitycurrency, FeedSupport feedSupport, String urlExtend, String errorMsgKey,
      FeedIdentifier feedIdentifier, SpecialInvestmentInstruments specialInvestmentInstruments,
      AssetclassType assetclassType) {

    boolean clear = super.checkAndClearSecuritycurrencyConnector(securitycurrency, feedSupport, urlExtend, errorMsgKey,
        feedIdentifier, specialInvestmentInstruments, assetclassType);
    if (securitycurrency instanceof Security security && (StringUtils.isBlank(security.getIsin())
        || !EN_STOCK_EXCHANGES_SET.contains(security.getStockexchange().getMic()))) {
      throw new GeneralNotTranslatedWithArgumentsException("gt.connector.euronext.setting.failure", null);
    }
    return clear;
  }

  @Override
  public List<Historyquote> getEodSecurityHistory(final Security security, final Date from, final Date to)
      throws Exception {

    final List<Historyquote> historyquotes = new ArrayList<>();
    final DateFormat dateFormat = new SimpleDateFormat(GlobalConstants.STANDARD_LOCAL_DATE_TIME);
    objectMapper.setDateFormat(dateFormat);
    String maxOr1M = DateHelper.getDateDiff(from, new Date(), TimeUnit.DAYS) > 30 ? PERIOD_MAX : PERIOD_1M;
    String url = getSecurityHistoricalDownloadLink(security, maxOr1M);
    final DailyClose[] dailyCloseArr = objectMapper.readValue(new URL(url), DailyClose[].class);
    for (int i = 0; i < dailyCloseArr.length; i++) {
      DailyClose dailyClose = dailyCloseArr[i];
      Date date = DateHelper.setTimeToZeroAndAddDay(dailyClose.time, 0);
      if (!date.before(from) && !date.after(to)) {
        Historyquote historyquote = new Historyquote();
        historyquote.setClose(DataHelper.round(dailyClose.price));
        historyquote.setVolume(dailyClose.volume);
        historyquote.setDate(date);
        historyquotes.add(historyquote);
      }
    }
    return historyquotes;
  }

  private static class DailyClose {
    public Date time;
    public double price;
    public Long volume;

  }
}
