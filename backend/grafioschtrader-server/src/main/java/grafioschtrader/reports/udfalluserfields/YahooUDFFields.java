package grafioschtrader.reports.udfalluserfields;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.apache.commons.collections4.map.PassiveExpiringMap;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import grafiosch.BaseConstants;
import grafiosch.entities.UDFData;
import grafiosch.entities.UDFData.UDFDataKey;
import grafiosch.types.IUDFSpecialType;
import grafiosch.udfalluserfields.UDFFieldsHelper;
import grafioschtrader.GlobalConstants;
import grafioschtrader.connector.instrument.BaseFeedConnector;
import grafioschtrader.connector.instrument.yahoo.CrumbManager;
import grafioschtrader.connector.yahoo.YahooHelper;
import grafioschtrader.connector.yahoo.YahooSymbolSearch;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.UDFMetadataSecurity;
import grafioschtrader.reportviews.securitycurrency.SecuritycurrencyPosition;
import grafioschtrader.reportviews.securitycurrency.SecuritycurrencyUDFGroup;
import grafioschtrader.repository.MicProviderMapRepository;
import grafioschtrader.types.UDFSpecialGTType;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.Refill;

public abstract class YahooUDFFields extends AllUserFieldsSecurity {

  private static final Logger log = LoggerFactory.getLogger(YahooUDFFields.class);

  /**
   * We believe that the Yahoo Finance ticker symbol on the Nasdaq and NYSE
   * corresponds to the general ticker symbol of the security.
   */
  private static final List<String> US_STOCK_EXCHANGE = Collections
      .unmodifiableList(Arrays.asList(GlobalConstants.STOCK_EX_MIC_NASDAQ, GlobalConstants.STOCK_EX_MIC_NYSE));

  /**
   * The class may be inherited several times, so the symbol search may be
   * repeated several times for the same security if recreate is set to True. The
   * symbol is read from the cache within 24 hours.
   */
  private static final Map<Integer, String> CACHE_SYMBOL = new PassiveExpiringMap<>(TimeUnit.DAYS.toMillis(1));

  /**
   * We limit the parallel streams to the following value. Otherwise too many
   * requests may be sent to Yahoo.
   */
  private static final ForkJoinPool forkJoinPool = new ForkJoinPool(5);
  private YahooSymbolSearch yahooSymbolSearch = new YahooSymbolSearch();

  private Bucket bucket;
  /**
   * This is the date format that comes from Yahoo. The time may have to be
   * adapted to the local user.
   */
  private DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMMM d, yyyy 'at' h a z", Locale.ENGLISH);
  private final SimpleDateFormat dateFormatEarnings = new SimpleDateFormat(BaseConstants.STANDARD_DATE_FORMAT);
  private UDFMetadataSecurity udfMDSYahooSymbol;

  protected void createYahooFieldValue(SecuritycurrencyUDFGroup securitycurrencyUDFGroup,
      IUDFSpecialType uDFSpecialType, MicProviderMapRepository micProviderMapRepository, boolean recreate) {
    udfMDSYahooSymbol = getMetadataSecurity(UDFSpecialGTType.UDF_SPEC_INTERNAL_YAHOO_SYMBOL_HIDE);
    UDFMetadataSecurity udfMetaDataSecurity = getMetadataSecurity(uDFSpecialType);
    LocalDate now = LocalDate.now();

    Bandwidth limit = Bandwidth.classic(2, Refill.intervally(1, Duration.ofSeconds(2)));
    this.bucket = Bucket.builder().addLimit(limit).build();
    List<SecuritycurrencyPosition<Security>> filteredList = securitycurrencyUDFGroup.securityPositionList.stream()
        .filter(
            s -> matchAssetclassAndSpecialInvestmentInstruments(udfMetaDataSecurity, s.securitycurrency.getAssetClass())
                && ((java.sql.Date) s.securitycurrency.getActiveToDate()).toLocalDate().isAfter(now))
        .collect(Collectors.toList());
    CrumbManager.setCookie();
    forkJoinPool.submit(() -> filteredList.parallelStream().forEach(s -> {
      createWhenNotExistsYahooFieldValue(securitycurrencyUDFGroup, udfMetaDataSecurity, s.securitycurrency,
          micProviderMapRepository, recreate);
    })).join();
  }

  /**
   * Creates or updates the Yahoo earning link or next earning date if not already
   * present. This method checks if the Yahoo earning link or next earning date is
   * already present. If not, it creates the link or retrieves the next earning
   * date.
   **/
  private void createWhenNotExistsYahooFieldValue(SecuritycurrencyUDFGroup securitycurrencyUDFGroup,
      UDFMetadataSecurity udfMetaDataSecurity, Security security, MicProviderMapRepository micProviderMapRepository,
      boolean recreate) {
    Object value = UDFFieldsHelper.readValueFromUser0(udfMetaDataSecurity, uDFDataJpaRepository, Security.class,
        security.getIdSecuritycurrency());
    if (value == null || recreate
        || (udfMetaDataSecurity.getUdfSpecialType() == UDFSpecialGTType.UDF_SPEC_INTERNAL_YAHOO_EARNING_NEXT_DATE
            && LocalDateTime.now().isAfter((LocalDateTime) value))) {
      writeYahooFieldValues(securitycurrencyUDFGroup, udfMetaDataSecurity, security, micProviderMapRepository,
          recreate);
    } else {
      putValueToJsonValue(securitycurrencyUDFGroup, udfMetaDataSecurity, security.getIdSecuritycurrency(), value,
          false);
    }
  }

  /**
   * Creates the Yahoo earning link or retrieves the next earning date for a
   * security. This method evaluates the Yahoo symbol for the security and creates
   * the earning link or retrieves the next earning date from Yahoo Finance.
   */
  private void writeYahooFieldValues(SecuritycurrencyUDFGroup securitycurrencyUDFGroup,
      UDFMetadataSecurity udfMetaDataSecurity, Security security, MicProviderMapRepository micProviderMapRepository,
      boolean recreate) {
    String yahooSymbol = evaluateYahooSymbol(security, micProviderMapRepository, recreate);
    if (yahooSymbol != null) {
      if (udfMetaDataSecurity.getUdfSpecialType() == UDFSpecialGTType.UDF_SPEC_INTERNAL_YAHOO_STATISTICS_LINK) {
        String url = YahooHelper.YAHOO_FINANCE_QUOTE + yahooSymbol + "/key-statistics/";
        putValueToJsonValue(securitycurrencyUDFGroup, udfMetaDataSecurity, security.getIdSecuritycurrency(), url, true);
      } else {
        createEaringsFieldValue(securitycurrencyUDFGroup, udfMetaDataSecurity, security, yahooSymbol);
      }
    }
  }

  private void createEaringsFieldValue(SecuritycurrencyUDFGroup securitycurrencyUDFGroup,
      UDFMetadataSecurity udfMetaDataSecurity, Security security, String yahooSymbol) {
    String url = YahooHelper.YAHOO_CALENDAR + "earnings?day=" + dateFormatEarnings.format(new Date()) + "&symbol="
        + yahooSymbol;
    if (udfMetaDataSecurity.getUdfSpecialType() == UDFSpecialGTType.UDF_SPEC_INTERNAL_YAHOO_EARNING_LINK) {
      putValueToJsonValue(securitycurrencyUDFGroup, udfMetaDataSecurity, security.getIdSecuritycurrency(), url, true);
    } else {
      try {
        LocalDateTime nextEarningDate = extractNextEarningDate(securitycurrencyUDFGroup, udfMetaDataSecurity, security,
            url);
        putValueToJsonValue(securitycurrencyUDFGroup, udfMetaDataSecurity, security.getIdSecuritycurrency(),
            nextEarningDate, true);
      } catch (Exception e) {
        log.error("Can not extract data from url for " + security.getName(), e);
      }
    }
  }

  /**
   * Evaluates the Yahoo symbol for the given security. It attempts to determine
   * the symbol using the different connectors. If none is a Yahoo connector, the
   * symbol is searched for in the JSON of user 0. If this fails, the
   * time-consuming Yahoo search is used.
   */
  protected String evaluateYahooSymbol(Security security, MicProviderMapRepository micProviderMapRepository,
      boolean recreate) {
    String yahooCon = BaseFeedConnector.ID_PREFIX + YahooHelper.YAHOO;
    String yahooSymbol = yahooCon.equals(security.getIdConnectorIntra()) ? security.getUrlIntraExtend()
        : yahooCon.equals(security.getIdConnectorHistory()) ? security.getUrlHistoryExtend()
            : yahooCon.equals(security.getIdConnectorSplit()) ? security.getUrlSplitExtend()
                : yahooCon.equals(security.getIdConnectorDividend()) ? security.getUrlDividendExtend() : null;
    if (yahooSymbol == null) {
      Optional<UDFData> udfDataSymbolOpt = uDFDataJpaRepository.findById(
          new UDFDataKey(BaseConstants.UDF_ID_USER, Security.class.getSimpleName(), security.getIdSecuritycurrency()));
      if (udfDataSymbolOpt.isPresent()) {
        yahooSymbol = (String) UDFFieldsHelper.readValueFromUser0(udfMDSYahooSymbol, uDFDataJpaRepository,
            Security.class, security.getIdSecuritycurrency());
      }
      if (yahooSymbol == null || recreate) {
        yahooSymbol = CACHE_SYMBOL.get(security.getIdSecuritycurrency());
        if (yahooSymbol == null) {
          String mic = security.getStockexchange().getMic();
          if (US_STOCK_EXCHANGE.contains(mic) && security.getTickerSymbol() != null) {
            yahooSymbol = security.getTickerSymbol();
          } else {
            waitForTokenOrGo();
            yahooSymbol = yahooSymbolSearch.getSymbolByISINOrSymbolOrName(micProviderMapRepository,
                security.getStockexchange().getMic(), security.getIsin(), security.getTickerSymbol(),
                security.getName());
          }
          UDFFieldsHelper.writeValueToUser0(udfMDSYahooSymbol, uDFDataJpaRepository, security.getClass(),
              security.getIdSecuritycurrency(), yahooSymbol);
          CACHE_SYMBOL.put(security.getIdSecuritycurrency(), yahooSymbol);
        }
      }
    }
    return yahooSymbol == null || yahooSymbol.startsWith("^") ? null : yahooSymbol;
  }

  private void waitForTokenOrGo() {
    do {
      ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
      if (probe.isConsumed()) {
        return;
      } else {
        long waitForRefill = TimeUnit.MILLISECONDS.convert(probe.getNanosToWaitForRefill(), TimeUnit.NANOSECONDS);
        try {
          Thread.sleep(waitForRefill);
        } catch (InterruptedException e) {
          e.printStackTrace();
        }
      }
    } while (true);
  }

  /**
   * Extracts the next earning date from the specified URL for a given security.
   *
   * <p>
   * This method connects to the provided URL using Jsoup, parses the HTML content
   * to find the earning dates table, and extracts the next earning date. The
   * method ensures that the extracted date is in the future relative to the
   * current date and time.
   *
   * @param securitycurrencyUDFGroup The group of security and currency data.
   * @param udfMetaDataSecurity      Metadata security information.
   * @param security                 The security for which the next earning date
   *                                 is to be extracted.
   * @param url                      The URL to connect to and extract the earning
   *                                 date from.
   * @return The next earning date as a {@link LocalDateTime}, or {@code null} if
   *         no future earning date is found.
   * @throws IOException If an I/O error occurs while connecting to the URL or
   *                     parsing the document.
   */
  private LocalDateTime extractNextEarningDate(SecuritycurrencyUDFGroup securitycurrencyUDFGroup,
      UDFMetadataSecurity udfMetaDataSecurity, Security security, String url) throws IOException {
    waitForTokenOrGo();
    LocalDateTime now = LocalDateTime.now();
    LocalDateTime nextEarningDate = null;

    final Connection conn = Jsoup.connect(url).header("Cookie", CrumbManager.getCookie())
        .userAgent(GlobalConstants.USER_AGENT);
    final Document doc = conn.get();
    Elements tables = doc.select("table.bd");
    if (tables.size() > 0) {
      Elements rows = tables.get(0).select("tr");
      for (Element row : rows) {
        Elements cols = row.select("td");
        if (cols.size() >= 6) {
          LocalDateTime localDateTime = LocalDateTime.parse(cols.get(2).text(), formatter);
          if (now.isAfter(localDateTime)) {
            break;
          }
          nextEarningDate = localDateTime;
        }
      }
    }
    return nextEarningDate;
  }

}
