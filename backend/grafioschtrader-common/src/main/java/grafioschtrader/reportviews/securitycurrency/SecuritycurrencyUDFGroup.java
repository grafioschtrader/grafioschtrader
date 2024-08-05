package grafioschtrader.reportviews.securitycurrency;

import java.util.Date;
import java.util.List;
import java.util.Map;

import grafioschtrader.entities.Currencypair;
import grafioschtrader.entities.Security;

public class SecuritycurrencyUDFGroup extends SecuritycurrencyGroup {

  /**
   * Map idSecurity and JSON values as String
   */
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
 
 /* 
  public static class UDFEntityValues  {

    private final int idEntity;
    private String jsonValues;
   
    public UDFEntityValues(int idEntity, String jsonValues) {
      this.idEntity = idEntity;
      this.jsonValues = jsonValues;
    }

    public int getIdEntity() {
      return idEntity;
    }
    
    public void setJsonValues(String jsonValues) {
      this.jsonValues = jsonValues;
    }

    public String getJsonValues() {
      return jsonValues;
    }
    
  }
  */
}
