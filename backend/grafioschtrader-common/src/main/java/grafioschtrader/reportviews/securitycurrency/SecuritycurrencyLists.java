package grafioschtrader.reportviews.securitycurrency;

import java.io.Serializable;
import java.util.List;

import grafioschtrader.entities.Currencypair;
import grafioschtrader.entities.Security;

public class SecuritycurrencyLists implements Serializable {

  private static final long serialVersionUID = 1L;

  public List<Security> securityList;
  public List<Currencypair> currencypairList;

  public SecuritycurrencyLists() {
  }

  public SecuritycurrencyLists(List<Security> securityList, List<Currencypair> currencypairList) {
    super();
    this.securityList = securityList;
    this.currencypairList = currencypairList;
  }

  public int getLength() {
    return securityList.size() + currencypairList.size();
  }

}
