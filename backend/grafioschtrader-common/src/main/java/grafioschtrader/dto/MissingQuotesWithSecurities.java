package grafioschtrader.dto;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonFormat;

import grafioschtrader.GlobalConstants;
import grafioschtrader.entities.Security;

public class MissingQuotesWithSecurities {

  public final Integer year;
  @JsonFormat(pattern = GlobalConstants.STANDARD_DATE_FORMAT)
  public final LocalDate firstEverTradingDay;
  public final Map<LocalDate, List<Integer>> dateSecurityMissingMap = new HashMap<>();
  public final Map<Integer, Integer> countIdSecurityMissingsMap = new HashMap<>();
  public List<Security> securties;

  public MissingQuotesWithSecurities(Integer year, LocalDate firstEverTradingDay) {
    this.year = year;
    this.firstEverTradingDay = firstEverTradingDay;
  }

  public Integer getYear() {
    return year;
  }

  public void addDateSecurity(LocalDate date, Integer idSecuritycurrency) {
    dateSecurityMissingMap.computeIfAbsent(date, (x -> new ArrayList<>())).add(idSecuritycurrency);
  }

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
