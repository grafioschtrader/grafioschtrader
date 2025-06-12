package grafioschtrader.reportviews.securitycurrency;

import java.util.Date;
import java.util.List;
import java.util.Map;

import grafioschtrader.entities.Currencypair;
import grafioschtrader.entities.Security;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = """
  Extends SecuritycurrencyGroup to include User Defined Fields (UDF) data for each instrument. This class is typically used in watchlist 
  reports where custom user-specific information needs to be displayed alongside standard instrument data.""")
public class SecuritycurrencyUDFGroup extends SecuritycurrencyGroup {

  
  @Schema(description = """
      A map containing User Defined Fields (UDF) data for the instruments in the group.
      The key is the ID of the security or currencypair (idSecuritycurrency),
      and the value is a JSON string representing the UDF key-value pairs for that instrument.
      Example: {100: "{\\"customNote\\":\\"Watch closely\\", \\"rating\\":5}", 205: "{\\"targetPrice\\":150.00}"}
      """)
  private Map<Integer, String> udfEntityValues;

  public SecuritycurrencyUDFGroup(List<SecuritycurrencyPosition<Security>> securityPositionList) {
    this(securityPositionList, null, null, null, null);
  }

  public SecuritycurrencyUDFGroup(List<SecuritycurrencyPosition<Security>> securityPositionList,
      List<SecuritycurrencyPosition<Currencypair>> currencypairPositionList, Date lastTimestamp, Integer idWatchlist,
      Map<Integer, String> udfEntityValues) {
    super(securityPositionList, currencypairPositionList, lastTimestamp, idWatchlist);
    this.udfEntityValues = udfEntityValues;
  }

  public Map<Integer, String> getUdfEntityValues() {
    return udfEntityValues;
  }

  public static interface IUDFEntityValues {
    Integer getIdSecuritycurrency();

    String getJsonValues();
  }

  
}
