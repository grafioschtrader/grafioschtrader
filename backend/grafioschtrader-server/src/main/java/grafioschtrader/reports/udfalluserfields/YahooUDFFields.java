package grafioschtrader.reports.udfalluserfields;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import grafioschtrader.GlobalConstants;
import grafioschtrader.connector.instrument.BaseFeedConnector;
import grafioschtrader.connector.yahoo.YahooHelper;
import grafioschtrader.connector.yahoo.YahooSymbolSearch;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.UDFData;
import grafioschtrader.entities.UDFData.UDFDataKey;
import grafioschtrader.entities.UDFMetadataSecurity;
import grafioschtrader.reportviews.securitycurrency.SecuritycurrencyPosition;
import grafioschtrader.reportviews.securitycurrency.SecuritycurrencyUDFGroup;
import grafioschtrader.repository.MicProviderMapRepository;
import grafioschtrader.types.UDFSpecialType;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.Refill;

public abstract class YahooUDFFields extends AllUserFieldsBase {

  private static final Logger log = LoggerFactory.getLogger(YahooUDFFields.class);
  /**
   * We limit the parallel streams to the following value. Otherwise too many
   * requests may be sent to Yahoo.
   */
  private static final ForkJoinPool forkJoinPool = new ForkJoinPool(6);
  private YahooSymbolSearch yahooSymbolSearch = new YahooSymbolSearch();
  
  private Bucket bucket;
  /**
   * This is the date format that comes from Yahoo. The time may have to be
   * adapted to the local user.
   */
  private DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMM d, yyyy, h a z", Locale.ENGLISH);
  private UDFMetadataSecurity udfMDSYahooSymbol;

  protected void createYahooFieldValue(SecuritycurrencyUDFGroup securitycurrencyUDFGroup,
      UDFSpecialType uDFSpecialType, MicProviderMapRepository micProviderMapRepository) {
    udfMDSYahooSymbol = getMetadataSecurity(UDFSpecialType.UDF_SPEC_INTERNAL_YAHOO_SYMBOL_HIDE);
    UDFMetadataSecurity udfMetaDataSecurity = getMetadataSecurity(uDFSpecialType);
    LocalDate now = LocalDate.now();
   
    Bandwidth limit = Bandwidth.classic(25, Refill.intervally(25, Duration.ofMinutes(1)));
    this.bucket = Bucket.builder().addLimit(limit).build();
    List<SecuritycurrencyPosition<Security>> filteredList = securitycurrencyUDFGroup.securityPositionList.stream()
        .filter(
            s -> matchAssetclassAndSpecialInvestmentInstruments(udfMetaDataSecurity, s.securitycurrency.getAssetClass())
                && ((java.sql.Date) s.securitycurrency.getActiveToDate()).toLocalDate().isAfter(now))
        .collect(Collectors.toList());
    forkJoinPool.submit(() -> filteredList.parallelStream().forEach(s -> {
      createWhenNotExistsYahooFieldValue(securitycurrencyUDFGroup, udfMetaDataSecurity, s.securitycurrency,
          micProviderMapRepository);
    })).join();
  }

  /**
   * Creates or updates the Yahoo earning link or next earning date if not already
   * present. This method checks if the Yahoo earning link or next earning date is
   * already present. If not, it creates the link or retrieves the next earning
   * date.
   **/
  private void createWhenNotExistsYahooFieldValue(SecuritycurrencyUDFGroup securitycurrencyUDFGroup,
      UDFMetadataSecurity udfMetaDataSecurity, Security security, MicProviderMapRepository micProviderMapRepository) {
    Object value = readValueFromUser0(udfMetaDataSecurity, security.getIdSecuritycurrency());
    if (value == null
        || (udfMetaDataSecurity.getUdfSpecialType() == UDFSpecialType.UDF_SPEC_INTERNAL_YAHOO_EARNING_NEXT_DATE
            && LocalDateTime.now().isAfter((LocalDateTime) value))) {
      writeYahooFieldValues(securitycurrencyUDFGroup, udfMetaDataSecurity, security,
          micProviderMapRepository);
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
      UDFMetadataSecurity udfMetaDataSecurity, Security security, MicProviderMapRepository micProviderMapRepository) {
    String yahooSymbol = evaluateYahooSymbol(security, micProviderMapRepository);
    if (yahooSymbol != null) {
      if (udfMetaDataSecurity.getUdfSpecialType() == UDFSpecialType.UDF_SPEC_INTERNAL_YAHOO_STATISTICS_LINK) {
        String url = YahooHelper.YAHOO_FINANCE_QUOTE + yahooSymbol + "/key-statistics/";
        putValueToJsonValue(securitycurrencyUDFGroup, udfMetaDataSecurity, security.getIdSecuritycurrency(), url, true);
      } else {
        createEaringsFieldValue(securitycurrencyUDFGroup, udfMetaDataSecurity, security, yahooSymbol);
      }
    }
  }
  
  private void createEaringsFieldValue(SecuritycurrencyUDFGroup securitycurrencyUDFGroup,
      UDFMetadataSecurity udfMetaDataSecurity, Security security, String yahooSymbol) {
    String url = YahooHelper.YAHOO_CALENDAR + "earnings/?symbol=" + yahooSymbol;
    if (udfMetaDataSecurity.getUdfSpecialType() == UDFSpecialType.UDF_SPEC_INTERNAL_YAHOO_EARNING_LINK) {
      putValueToJsonValue(securitycurrencyUDFGroup, udfMetaDataSecurity, security.getIdSecuritycurrency(), url, true);
    } else {
      try {
        LocalDateTime nextEarningDate = extractNextEarningDate(securitycurrencyUDFGroup, udfMetaDataSecurity,
            security, url);
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
  protected String evaluateYahooSymbol(Security security, MicProviderMapRepository micProviderMapRepository) {
    String yahooCon = BaseFeedConnector.ID_PREFIX + YahooHelper.YAHOO;
    String yahooSymbol = yahooCon.equals(security.getIdConnectorIntra()) ? security.getUrlIntraExtend()
        : yahooCon.equals(security.getIdConnectorHistory()) ? security.getUrlHistoryExtend()
            : yahooCon.equals(security.getIdConnectorSplit()) ? security.getUrlSplitExtend()
                : yahooCon.equals(security.getIdConnectorDividend()) ? security.getUrlDividendExtend() : null;
    if (yahooSymbol == null) {
      Optional<UDFData> udfDataSymbolOpt = uDFDataJpaRepository.findById(new UDFDataKey(GlobalConstants.UDF_ID_USER,
          Security.class.getSimpleName(), security.getIdSecuritycurrency()));
      if (udfDataSymbolOpt.isPresent()) {
        yahooSymbol = (String) readValueFromUser0(udfMDSYahooSymbol, security.getIdSecuritycurrency());
      }
      if (yahooSymbol == null) {
        waitForTokenOrGo();
        yahooSymbol = yahooSymbolSearch.getSymbolByISINOrSymbolOrName(micProviderMapRepository,
            security.getStockexchange().getMic(), security.getIsin(), security.getTickerSymbol(), security.getName());
        writeValueToUser0(udfMDSYahooSymbol, security.getIdSecuritycurrency(), yahooSymbol);
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
    final Connection conn = Jsoup.connect(url).userAgent(GlobalConstants.USER_AGENT);
    final Document doc = conn.get();
    Elements tables = doc.select("table[class=W(100%)]");
    if (tables.size() > 0) {
      Elements rows = tables.get(0).select("tr");
      for (Element row : rows) {
        Elements cols = row.select("td");
        if (cols.size() >= 6) {
          LocalDateTime localDateTime = LocalDateTime
              .parse(cols.get(2).text().replace(" AM", " AM ").replace(" PM", " PM "), formatter);
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
