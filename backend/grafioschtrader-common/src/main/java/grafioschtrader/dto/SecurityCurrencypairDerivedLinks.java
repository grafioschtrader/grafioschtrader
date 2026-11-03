package grafioschtrader.dto;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import grafioschtrader.common.DateBusinessHelper;
import grafioschtrader.entities.Currencypair;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.SecurityDerivedLink;
import grafioschtrader.entities.Securitycurrency;

public class SecurityCurrencypairDerivedLinks {

  public List<SecurityDerivedLink> securityDerivedLinks;
  public List<Security> securities;
  public List<Currencypair> currencypairs;

  public SecurityCurrencypairDerivedLinks() {
  }

  public SecurityCurrencypairDerivedLinks(List<SecurityDerivedLink> securityDerivedLinks, List<Security> securities,
      List<Currencypair> currencypairs) {
    this.securityDerivedLinks = securityDerivedLinks;
    this.securities = securities;
    this.currencypairs = currencypairs;
  }

  public List<VarNameLastPrice> getLastPricesByLinks(Integer idSecuritycurrency) {
    List<VarNameLastPrice> varNameClosePriceList = new ArrayList<>();
    Double sLast = this.findSecurityCurrencypair(idSecuritycurrency);
    if (sLast != null) {
      varNameClosePriceList.add(new VarNameLastPrice(SecurityDerivedLink.FIRST_VAR_NAME_LETTER, sLast));
      for (SecurityDerivedLink sdl : securityDerivedLinks) {
        sLast = findSecurityCurrencypair(sdl.getIdLinkSecuritycurrency());
        if (sLast != null) {
          varNameClosePriceList.add(new VarNameLastPrice(sdl.getVarName(), sLast));
        } else {
          return Collections.emptyList();
        }
      }
    }

    return varNameClosePriceList;
  }

  private Double findSecurityCurrencypair(Integer idSecuritycurrency) {
    Double sLast = null;
    Optional<Security> security = securities.stream().filter(s -> s.getIdSecuritycurrency().equals(idSecuritycurrency))
        .findFirst();
    if (security.isPresent()) {
      sLast = security.get().getSLast();
    } else {
      Optional<Currencypair> currencypairOpt = currencypairs.stream()
          .filter(c -> c.getIdSecuritycurrency().equals(idSecuritycurrency)).findFirst();
      if (currencypairOpt.isPresent()) {
        sLast = currencypairOpt.get().getSLast();
      }
    }
    return sLast;
  }

  /**
   * Replaces loaded linked instruments by the instances the caller refreshed in the same price update. A price that
   * came from a GTNet peer exists only on that instance and was never written to the database, and an instrument
   * updated a moment ago may not be visible to the read that filled this object, so the database copy would be stale.
   *
   * @param refreshedInstruments the freshly updated instruments keyed by their id; an id without a loaded counterpart
   *                             is ignored
   */
  public void preferRefreshedInstruments(Map<Integer, ? extends Securitycurrency<?>> refreshedInstruments) {
    if (refreshedInstruments.isEmpty()) {
      return;
    }
    securities = securities.stream().map(s -> refreshedInstruments.get(s.getIdSecuritycurrency()) instanceof Security r
        ? r
        : s).collect(Collectors.toList());
    currencypairs = currencypairs.stream()
        .map(c -> refreshedInstruments.get(c.getIdSecuritycurrency()) instanceof Currencypair r ? r : c)
        .collect(Collectors.toList());
  }

  public LocalDateTime getNewestIntradayTimestamp() {
    LocalDateTime oldestDate = DateBusinessHelper.getOldestTradingDayAsLocalDateTime();

    LocalDateTime maxDateSecurity = securities.stream().map(Security::getSTimestamp).max(LocalDateTime::compareTo)
        .orElse(oldestDate);
    LocalDateTime maxDateCurrencypair = currencypairs.stream().map(Currencypair::getSTimestamp)
        .max(LocalDateTime::compareTo).orElse(oldestDate);
    return maxDateSecurity.isAfter(maxDateCurrencypair) ? maxDateSecurity : maxDateCurrencypair;
  }

  public static class VarNameLastPrice {
    public String varName;
    public double sLast;

    public VarNameLastPrice(String varName, double sLast) {
      this.varName = varName;
      this.sLast = sLast;
    }

  }

}
