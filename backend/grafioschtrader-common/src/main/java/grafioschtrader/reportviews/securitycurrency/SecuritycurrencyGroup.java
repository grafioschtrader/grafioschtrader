package grafioschtrader.reportviews.securitycurrency;

import java.util.Date;
import java.util.List;

import grafioschtrader.entities.Currencypair;
import grafioschtrader.entities.Security;

public class SecuritycurrencyGroup {
  public List<SecuritycurrencyPosition<Security>> securityPositionList;
  public List<SecuritycurrencyPosition<Currencypair>> currencypairPositionList;
  public Date lastTimestamp;
  public Integer idWatchlist;

  public SecuritycurrencyGroup(List<SecuritycurrencyPosition<Security>> securityPositionList,
      List<SecuritycurrencyPosition<Currencypair>> currencypairPositionList, Date lastTimestamp, Integer idWatchlist) {
    super();
    this.securityPositionList = securityPositionList;
    this.currencypairPositionList = currencypairPositionList;
    this.lastTimestamp = lastTimestamp;
    this.idWatchlist = idWatchlist;
  }

}
