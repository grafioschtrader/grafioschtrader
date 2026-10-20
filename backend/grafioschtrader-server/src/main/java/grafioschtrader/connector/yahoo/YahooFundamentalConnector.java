package grafioschtrader.connector.yahoo;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

import grafioschtrader.GlobalConstants;
import grafioschtrader.connector.fundamental.FundamentalAnswer;
import grafioschtrader.connector.fundamental.FundamentalAnswerMapper;
import grafioschtrader.connector.fundamental.FundamentalFeedSupport;
import grafioschtrader.connector.fundamental.FundamentalFeedSupport.HttpAnswer;
import grafioschtrader.connector.fundamental.IFundamentalFeedConnector;
import grafioschtrader.connector.instrument.BaseFeedConnector;
import grafioschtrader.entities.Security;
import grafioschtrader.types.FundamentalSource;
import grafioschtrader.udffill.UDFFillScopeService;

/**
 * Reads fundamental base values from Yahoo's unofficial {@code v10/finance/quoteSummary}. It needs no key but the
 * cookie and crumb of {@link CrumbManager}; a 401 renews both once and repeats the request.
 *
 * <p>
 * The symbol is the extension of the Yahoo intraday connector when the security uses it, then the system-wide Yahoo
 * symbol of the user-defined field, then ticker plus the exchange suffix of the MIC.
 * </p>
 */
@Component
public class YahooFundamentalConnector implements IFundamentalFeedConnector {

  private static final String URL = "https://query2.finance.yahoo.com/v10/finance/quoteSummary/%s"
      + "?modules=defaultKeyStatistics,financialData,summaryDetail&crumb=%s";
  private static final String YAHOO_CONNECTOR_ID = BaseFeedConnector.ID_PREFIX + YahooHelper.YAHOO;
  /** The Yahoo symbols of the user-defined field are read once per run, not once per security. */
  private static final Duration SYMBOL_CACHE = Duration.ofMinutes(30);

  private final UDFFillScopeService udfFillScopeService;
  private Map<Integer, String> yahooSymbols;
  private Instant yahooSymbolsLoaded;

  public YahooFundamentalConnector(UDFFillScopeService udfFillScopeService) {
    this.udfFillScopeService = udfFillScopeService;
  }

  @Override
  public FundamentalSource getFundamentalSource() {
    return FundamentalSource.YAHOO;
  }

  @Override
  public boolean isFundamentalAvailable() {
    return true;
  }

  @Override
  public boolean supportsFundamentals(Security security) {
    return symbol(security) != null;
  }

  @Override
  public Duration getFundamentalPause() {
    return Duration.ofMillis(600);
  }

  @Override
  public FundamentalAnswer loadFundamentals(Security security) throws Exception {
    String symbol = symbol(security);
    HttpAnswer answer = request(symbol);
    if (answer.status() == 401) {
      CrumbManager.resetCookieCrumb();
      answer = request(symbol);
    }
    if (answer.status() != 200) {
      return FundamentalAnswer.failed("HTTP " + answer.status() + " for " + symbol, answer.status());
    }
    return FundamentalAnswerMapper.yahoo(FundamentalFeedSupport.parse(answer.body()), security.getIdSecuritycurrency(),
        LocalDateTime.now(ZoneOffset.UTC));
  }

  /** The Yahoo symbol of a security, null when none can be formed. */
  String symbol(Security security) {
    if (YAHOO_CONNECTOR_ID.equals(security.getIdConnectorIntra()) && notBlank(security.getUrlIntraExtend())) {
      return security.getUrlIntraExtend().trim();
    }
    String udfSymbol = yahooSymbols().get(security.getIdSecuritycurrency());
    if (notBlank(udfSymbol)) {
      return udfSymbol.trim();
    }
    return FundamentalFeedSupport.derivedYahooSymbol(security);
  }

  private synchronized Map<Integer, String> yahooSymbols() {
    if (yahooSymbols == null || yahooSymbolsLoaded.plus(SYMBOL_CACHE).isBefore(Instant.now())) {
      yahooSymbols = udfFillScopeService.getYahooSymbols();
      yahooSymbolsLoaded = Instant.now();
    }
    return yahooSymbols;
  }

  private static HttpAnswer request(String symbol) throws Exception {
    String url = String.format(URL, URLEncoder.encode(symbol, StandardCharsets.UTF_8),
        URLEncoder.encode(CrumbManager.getCrumb(), StandardCharsets.UTF_8));
    Map<String, String> headers = new HashMap<>(Map.of("User-Agent", GlobalConstants.USER_AGENT_HTTPCLIENT));
    String cookie = CrumbManager.getCookie();
    if (notBlank(cookie)) {
      headers.put("Cookie", cookie);
    }
    return FundamentalFeedSupport.get(url, headers);
  }

  private static boolean notBlank(String text) {
    return text != null && !text.isBlank();
  }
}
