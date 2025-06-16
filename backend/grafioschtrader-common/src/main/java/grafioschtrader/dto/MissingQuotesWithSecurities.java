package grafioschtrader.dto;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonFormat;

import grafiosch.BaseConstants;
import grafioschtrader.entities.Security;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Data Transfer Object for analyzing missing historical quotes in securities analysis.
 * 
 * <p>
 * This class provides a comprehensive view of missing price data for securities within a specific year, enabling
 * identification of gaps that could affect performance calculations.
 * </p>
 * 
 * <p>
 * <strong>Analysis Structure:</strong>
 * </p>
 * <p>
 * The class organizes missing quote information in multiple ways:
 * </p>
 * <ul>
 * <li>By date - which securities are missing quotes on specific trading days</li>
 * <li>By security - how many missing quotes each security has</li>
 * <li>Overall context - the analysis year and trading day boundaries</li>
 * </ul>
 * 
 * <p>
 * <strong>Usage Context:</strong>
 * </p>
 * <p>
 * This DTO is typically used for data quality assessment, identifying securities that need historical quote updates,
 * and determining the completeness of price data for accurate performance analysis.
 * </p>
 */
@Schema(description = "Analysis of missing historical quotes for securities in a specific year")
public class MissingQuotesWithSecurities {

  @Schema(description = "Year of the missing quotes analysis")
  public final Integer year;

  @Schema(description = "Earliest trading day available in the system")
  @JsonFormat(pattern = BaseConstants.STANDARD_DATE_FORMAT)
  public final LocalDate firstEverTradingDay;

  @Schema(description = "Map of trading dates to security IDs missing quotes on those dates")
  public final Map<LocalDate, List<Integer>> dateSecurityMissingMap = new HashMap<>();

  @Schema(description = "Map of security IDs to their total count of missing quote days")
  public final Map<Integer, Integer> countIdSecurityMissingsMap = new HashMap<>();

  @Schema(description = "Securities that have missing historical quotes in the analyzed period")
  public List<Security> securties;

  public MissingQuotesWithSecurities(Integer year, LocalDate firstEverTradingDay) {
    this.year = year;
    this.firstEverTradingDay = firstEverTradingDay;
  }

  public Integer getYear() {
    return year;
  }

  /**
   * Adds a security as missing a quote on a specific trading date.
   * 
   * <p>
   * This method builds the date-centric view of missing quotes, organizing securities by the dates on which they lack
   * price data. If the date is not yet in the map, a new list is created automatically.
   * </p>
   * 
   * @param date               the trading date missing the quote
   * @param idSecuritycurrency the security ID missing the quote on this date
   */
  public void addDateSecurity(LocalDate date, Integer idSecuritycurrency) {
    dateSecurityMissingMap.computeIfAbsent(date, (x -> new ArrayList<>())).add(idSecuritycurrency);
  }

  /**
   * Increments the missing quote count for a specific security.
   * 
   * <p>
   * This method builds the security-centric view of missing quotes, tracking how many trading days each security is
   * missing price data. The count is automatically incremented if the security is already in the map.
   * </p>
   * 
   * @param idSecuritycurrency the security ID to increment the missing count for
   */
  public void addMissingIdSecurity(Integer idSecuritycurrency) {
    countIdSecurityMissingsMap.merge(idSecuritycurrency, 1, Integer::sum);
  }

  public void setSecurties(List<Security> securties) {
    this.securties = securties;
  }

  public LocalDate getFirstEverTradingDay() {
    return firstEverTradingDay;
  }

  public Map<LocalDate, List<Integer>> getDateSecurityMissingMap() {
    return dateSecurityMissingMap;
  }

  public Map<Integer, Integer> getCountIdSecurityMissingsMap() {
    return countIdSecurityMissingsMap;
  }

  public List<Security> getSecurities() {
    return securties;
  }

}
