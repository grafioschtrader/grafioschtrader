package grafioschtrader.reports.udfalluserfields;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ForkJoinPool;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import grafiosch.types.IUDFSpecialType;
import grafiosch.udfalluserfields.UDFFieldsHelper;
import grafioschtrader.connector.yahoo.YahooHelper;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.UDFMetadataSecurity;
import grafioschtrader.reportviews.securitycurrency.SecuritycurrencyPosition;
import grafioschtrader.reportviews.securitycurrency.SecuritycurrencyUDFGroup;
import grafioschtrader.repository.MicProviderMapRepository;
import grafioschtrader.types.UDFSpecialGTType;

public abstract class YahooUDFFields extends AllUserFieldsSecurity {

  private static final Logger log = LoggerFactory.getLogger(YahooUDFFields.class);

  private YahooUDFConnect yahooUDFConnect = new YahooUDFConnect();
  /**
   * We limit the parallel streams to the following value. Otherwise too many
   * requests may be sent to Yahoo.
   */
  private static final ForkJoinPool forkJoinPool = new ForkJoinPool(5);

  private UDFMetadataSecurity udfMDSYahooSymbol;

  

  protected void createYahooFieldValue(SecuritycurrencyUDFGroup securitycurrencyUDFGroup,
      IUDFSpecialType uDFSpecialType, MicProviderMapRepository micProviderMapRepository, boolean recreate) {
    udfMDSYahooSymbol = getMetadataSecurity(UDFSpecialGTType.UDF_SPEC_INTERNAL_YAHOO_SYMBOL_HIDE);
    UDFMetadataSecurity udfMetaDataSecurity = getMetadataSecurity(uDFSpecialType);
    LocalDate now = LocalDate.now();

    List<SecuritycurrencyPosition<Security>> filteredList = securitycurrencyUDFGroup.securityPositionList.stream()
        .filter(
            s -> matchAssetclassAndSpecialInvestmentInstruments(udfMetaDataSecurity, s.securitycurrency.getAssetClass())
                && ((java.sql.Date) s.securitycurrency.getActiveToDate()).toLocalDate().isAfter(now))
        .collect(Collectors.toList());
  
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
    String yahooSymbol = yahooUDFConnect.evaluateYahooSymbol(uDFDataJpaRepository, udfMDSYahooSymbol, security,
        micProviderMapRepository, recreate);
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
    String url = yahooUDFConnect.getEarningURL(yahooSymbol);
    if (udfMetaDataSecurity.getUdfSpecialType() == UDFSpecialGTType.UDF_SPEC_INTERNAL_YAHOO_EARNING_LINK) {
      putValueToJsonValue(securitycurrencyUDFGroup, udfMetaDataSecurity, security.getIdSecuritycurrency(), url, true);
    } else {
      try {
        LocalDateTime nextEarningDate = yahooUDFConnect.extractNextEarningDate(url);
        putValueToJsonValue(securitycurrencyUDFGroup, udfMetaDataSecurity, security.getIdSecuritycurrency(),
            nextEarningDate, true);
      } catch (Exception e) {
        log.error("Can not extract data from url {} for {} ", url, security.getName(), e);
      }
    }
  }

}
