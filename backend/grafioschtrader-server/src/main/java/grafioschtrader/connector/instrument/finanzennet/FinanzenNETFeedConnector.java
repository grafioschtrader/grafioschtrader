package grafioschtrader.connector.instrument.finanzennet;

import java.text.NumberFormat;
import java.text.ParseException;
import java.util.Arrays;
import java.util.Date;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jsoup.nodes.Document;
import org.springframework.stereotype.Component;

import grafioschtrader.connector.instrument.finanzen.FinanzenConnetorBase;
import grafioschtrader.connector.instrument.finanzen.FinanzenHelper;
import grafioschtrader.entities.Currencypair;
import grafioschtrader.entities.Historyquote;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.Securitycurrency;
import grafioschtrader.types.AssetclassType;
import grafioschtrader.types.SpecialInvestmentInstruments;

/*-
* At one time, historical data could also be queried via this connector.
 * However, access to these pages was better protected against non-browser
 * queries. At present, the source code does not yet fully reflect this
 * situation.
 *
 * For some securities, there is a redirect to the corresponding country page.
 * These securities can therefore not be queried. There is a finance.net, ch, and at.
 * Therefore, Finanzen.net should not be used to query the prices of Austrian and Swiss securities.
 *
 * Stocks, Bond, ETF:<br>
 * It difficult to check the url extension with a regex pattern
 *
 * Dividend: Value are summarized, can not be used in this application.
 * Splits: Not supported
 *
 * A regex pattern check is not active. However, the accessibility of the instrument is checked.
 */
@Component
public class FinanzenNETFeedConnector extends FinanzenConnetorBase {

  public static String domain = "https://www.finanzen.net/";
  private static final List<Locale> CANDIDATE_LOCALES = Arrays.asList(Locale.GERMANY, Locale.US);

  private static final Pattern THREE_NUMBER_PATTERN = Pattern
      .compile("([-]?\\d{1,3}(?:[.,]\\d{3})*[.,]?\\d*)\\s+([-]?\\d{1,3}(?:[.,]\\d{3})*[.,]?\\d*)\\s+([-]?\\d{1,3}(?:[.,]\\d{3})*[.,]?\\d*)");

  private static Map<FeedSupport, FeedIdentifier[]> supportedFeed;

  static {
    supportedFeed = new HashMap<>();
    supportedFeed.put(FeedSupport.FS_INTRA, new FeedIdentifier[] { FeedIdentifier.SECURITY_URL, FeedIdentifier.CURRENCY_URL });
  }

  public FinanzenNETFeedConnector() {
    super(supportedFeed, "finanzennet", "Finanzen NET", null, EnumSet.of(UrlCheck.INTRADAY));
    initalizeHttpClient(domain);

  }

  @Override
  public String getSecurityHistoricalDownloadLink(final Security security) {
    String[] firstPartUrl = security.getUrlHistoryExtend().split(Pattern.quote("|"));
    return domain + firstPartUrl[0];
  }

  @Override
  public List<Historyquote> getEodSecurityHistory(final Security security, final Date from, final Date to)
      throws Exception {
    return null;
  }

  @Override
  public String getCurrencypairHistoricalDownloadLink(final Currencypair currencypair) {
    return domain + currencypair.getUrlHistoryExtend();
  }

  @Override
  public List<Historyquote> getEodCurrencyHistory(final Currencypair currencyPair, final Date from, final Date to)
      throws Exception {
    return null;
  }

  @Override
  public String getSecurityIntradayDownloadLink(final Security security) {
    return domain + security.getUrlIntraExtend()
        + (security.getAssetClass().getCategoryType() == AssetclassType.FIXED_INCOME
            || security.getAssetClass().getSpecialInvestmentInstrument() == SpecialInvestmentInstruments.ETF
                ? "/" + FinanzenHelper.getNormalMappedStockexchangeSymbol(security.getStockexchange().getMic())
                : "");
  }

  @Override
  public void updateSecurityLastPrice(final Security security) throws Exception {
    updateSecuritycurrency(security, getSecurityIntradayDownloadLink(security));
  }

  @Override
  public int getIntradayDelayedSeconds() {
    return 900;
  }
  
  @Override
  public String getCurrencypairIntradayDownloadLink(final Currencypair currencypair) {
    return domain + currencyIntraPrefix + currencypair.getUrlIntraExtend();
  }
  
  @Override
  public void  updateCurrencyPairLastPrice(final Currencypair currencypair) throws Exception {
    updateSecuritycurrency(currencypair, getCurrencypairIntradayDownloadLink(currencypair));
  }


  public <T extends Securitycurrency<T>> void updateSecuritycurrency(T securitycurrency, String url) throws Exception {
    final Document doc = getDoc(url);
    String rawTextContent = doc.select("div.snapshot__values").text();
    String cleanedTextForRegex = rawTextContent.replaceAll("[A-Z]{3}", "").replace("%", "").replace(":", "")
        .replace("±", "") .replace("+", "").replaceAll("\\s+", " ").trim();
    Matcher matcher = THREE_NUMBER_PATTERN.matcher(cleanedTextForRegex);
    if (matcher.find()) {
      String lastPriceStr = matcher.group(1).trim();
      String changeStr = matcher.group(2).trim();
      String changePercentageStr = matcher.group(3).trim();

      ParseException lastParseException = null;
      for (Locale locale : CANDIDATE_LOCALES) {
        try {
          NumberFormat format = NumberFormat.getInstance(locale);
          format.setParseIntegerOnly(false);
          securitycurrency.setSLast(format.parse(lastPriceStr).doubleValue());
          securitycurrency.setSOpen(securitycurrency.getSLast() - format.parse(changeStr).doubleValue());
          securitycurrency.setSChangePercentage(format.parse(changePercentageStr).doubleValue());
          securitycurrency.setSTimestamp(new Date(System.currentTimeMillis() - getIntradayDelayedSeconds()));
          return;
        } catch (ParseException e) {
          lastParseException = e;
        }
      }
      // If we reach here, parsing failed for all locales
      if (lastParseException != null) {
        throw lastParseException;
      } else {
        // This case should ideally not be reached if matcher.find() was true,
        // but added for completeness if no locale handles a valid pattern match.
        throw new ParseException("Could not parse numbers with any of the candidate locales.", 0);
      }
    } else {
      throw new ParseException("Could not find the three consecutive number pattern in the text: '" + cleanedTextForRegex + "'",
          0);
    }
  }

}