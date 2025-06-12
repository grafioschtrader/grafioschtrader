package grafioschtrader.reportviews.securitycurrency;

import java.util.Date;
import java.util.List;

import grafioschtrader.entities.Currencypair;
import grafioschtrader.entities.Security;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = """
        Represents a group of security and currencypair positions, typically used for watchlist reports. 
        It includes lists of these positions, the timestamp of the last data update, and the ID of the associated watchlist.""")
public class SecuritycurrencyGroup {
  @Schema(description = "A list of security positions, each containing a security entity and its associated positional data (like performance, UDFs, etc.).")
  public List<SecuritycurrencyPosition<Security>> securityPositionList;
  
  @Schema(description = "A list of currencypair positions, each containing a currencypair entity and its associated positional data.")
  public List<SecuritycurrencyPosition<Currencypair>> currencypairPositionList;
  
  @Schema(description = "Timestamp indicating the last time the data for this group (e.g., prices in a watchlist) was updated.",
      format = "date-time", example = "2024-07-21T10:30:00Z")
  public Date lastTimestamp;
  @Schema(description = "The ID of the watchlist to which this group of instruments belongs.")
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
