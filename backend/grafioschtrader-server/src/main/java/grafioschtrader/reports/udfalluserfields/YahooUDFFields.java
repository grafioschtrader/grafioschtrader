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

/**
 * Abstract base class for managing Yahoo Finance-related user-defined fields across all users in the system. This class
 * extends AllUserFieldsSecurity to provide specialized functionality for retrieving and managing Yahoo Finance data
 * such as earnings information, statistics links, and earnings dates for securities.
 * 
 * The class implements parallel processing to efficiently handle multiple securities while respecting Yahoo Finance's
 * rate limiting requirements. It manages various types of Yahoo-related UDF fields including:</br>
 * - Yahoo statistics links for fundamental analysis</br>
 * - Yahoo earnings calendar links for upcoming earnings events</br>
 * - Next earnings dates extracted from Yahoo Finance calendar</br>
 * - Internal Yahoo symbol management for data retrieval</br>
 * 
 * Key features include:</br>
 * - Parallel processing with controlled concurrency to prevent API overload</br>
 * - Intelligent caching and persistence of Yahoo symbols and field values</br>
 * - Asset class and investment instrument filtering for targeted field application</br>
 * - Automatic recreation of expired or missing field values</br>
 * - Error handling and logging for failed data extraction operations</br>
 * 
 * The class uses a ForkJoinPool with limited parallelism to balance performance with Yahoo Finance's usage policies,
 * ensuring reliable data retrieval without service disruption.
 * 
 * Subclasses typically implement specific Yahoo field types by leveraging the common infrastructure provided for symbol
 * resolution, data extraction, and field value management.
 */
public abstract class YahooUDFFields extends AllUserFieldsSecurity {

  private static final Logger log = LoggerFactory.getLogger(YahooUDFFields.class);

  private YahooUDFConnect yahooUDFConnect = new YahooUDFConnect();
  /**
   * We limit the parallel streams to the following value. Otherwise too many requests may be sent to Yahoo.
   */
  private static final ForkJoinPool forkJoinPool = new ForkJoinPool(5);

  private UDFMetadataSecurity udfMDSYahooSymbol;

  /**
   * Creates or updates Yahoo Finance field values for all applicable securities in the provided group. This method
   * filters securities based on asset class matching and active status, then processes them in parallel to efficiently
   * retrieve and store Yahoo Finance data while respecting rate limits.
   * 
   * The processing includes:</br>
   * 1. Filtering securities by asset class compatibility and active status</br>
   * 2. Parallel processing with controlled concurrency (max 5 threads)</br>
   * 3. Creating or updating field values based on the specified UDF special type</br>
   * 4. Automatic Yahoo symbol resolution and caching</br>
   * 
   * Only securities that match the UDF metadata's category types and special investment instruments and have active
   * dates in the future are processed to ensure relevance and data quality.
   * 
   * @param securitycurrencyUDFGroup the group containing securities and UDF data context for processing
   * @param uDFSpecialType           the specific type of Yahoo UDF field to create or update
   * @param micProviderMapRepository repository for market identifier code mappings used in symbol resolution
   * @param recreate                 if true, forces recreation of field values even if they already exist
   */
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
   * Creates or updates Yahoo field values only when necessary, implementing intelligent field value management. This
   * method checks for existing field values and determines whether they need to be created, updated, or simply loaded
   * from existing data. Special handling is provided for earnings date fields that may expire and require periodic
   * updates.
   * 
   * The method implements the following logic:</br>
   * - Creates new values if none exist</br>
   * - Recreates values if forced recreation is requested</br>
   * - Updates earnings dates that have passed (for next earnings date fields)</br>
   * - Loads existing valid values into the UDF group for display</br>
   * 
   * This approach optimizes performance by avoiding unnecessary Yahoo Finance API calls while ensuring data freshness
   * for time-sensitive information like earnings dates.
   * 
   * @param securitycurrencyUDFGroup the UDF group context for storing field values
   * @param udfMetaDataSecurity      the metadata definition for the specific Yahoo UDF field
   * @param security                 the security for which to create or update the field value
   * @param micProviderMapRepository repository for market identifier mappings
   * @param recreate                 if true, forces recreation regardless of existing values
   */
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
   * Retrieves Yahoo symbol and creates appropriate field values based on the UDF special type. This method handles the
   * actual data retrieval and field value creation by first resolving the Yahoo symbol for the security, then creating
   * the appropriate field content based on whether the field is for statistics links or earnings-related information.
   * 
   * The method supports different Yahoo UDF field types:</br>
   * - Statistics links: Direct URL construction to Yahoo Finance key statistics page</br>
   * - Earnings-related fields: Delegation to earnings-specific processing</br>
   * 
   * Yahoo symbol resolution uses multiple strategies including existing connectors, cached symbols, and symbol search
   * when necessary.
   * 
   * @param securitycurrencyUDFGroup the UDF group context for storing field values
   * @param udfMetaDataSecurity      the metadata definition for the specific Yahoo UDF field
   * @param security                 the security for which to create field values
   * @param micProviderMapRepository repository for market identifier mappings
   * @param recreate                 if true, forces fresh symbol resolution and field creation
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

  /**
   * Creates earnings-related field values by handling both earnings links and next earnings dates. This method
   * generates the appropriate Yahoo Finance earnings calendar URL and either stores the URL directly (for link fields)
   * or extracts the next earnings date from the calendar page (for date fields).
   * 
   * The method supports two types of earnings fields:</br>
   * - Earnings calendar links: Direct URL to Yahoo Finance earnings calendar</br>
   * - Next earnings dates: Extracted date/time of the next scheduled earnings announcement</br>
   * 
   * Error handling is implemented for earnings date extraction failures, with appropriate logging to track data
   * retrieval issues for specific securities.
   * 
   * @param securitycurrencyUDFGroup the UDF group context for storing field values
   * @param udfMetaDataSecurity      the metadata definition for the specific earnings UDF field
   * @param security                 the security for which to create earnings field values
   * @param yahooSymbol              the resolved Yahoo Finance symbol for the security
   */
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
