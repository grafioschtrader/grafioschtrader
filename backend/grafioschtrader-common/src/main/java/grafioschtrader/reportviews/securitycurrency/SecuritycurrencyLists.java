package grafioschtrader.reportviews.securitycurrency;

import java.io.Serializable;
import java.util.List;

import grafioschtrader.entities.Currencypair;
import grafioschtrader.entities.Security;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Represents a container for lists of securities and currency pairs.")
public class SecuritycurrencyLists implements Serializable {

  private static final long serialVersionUID = 1L;

  @Schema(description = "A list of security entities.")
  public List<Security> securityList;
  @Schema(description = "A list of currency pair entities.")
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
