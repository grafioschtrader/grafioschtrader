package grafioschtrader.dto;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import grafioschtrader.common.DateHelper;
import grafioschtrader.entities.Currencypair;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.SecurityDerivedLink;

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

  public Date getNewestIntradayTimestamp() throws ParseException {
    Date oldestDate = DateHelper.getOldestTradingDay();

    Date maxDateSecurity = securities.stream().map(Security::getSTimestamp).max(Date::compareTo).orElse(oldestDate);
    Date maxDateCurrencypair = currencypairs.stream().map(Currencypair::getSTimestamp).max(Date::compareTo)
        .orElse(oldestDate);
    return maxDateSecurity.after(maxDateCurrencypair) ? maxDateSecurity : maxDateCurrencypair;
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
