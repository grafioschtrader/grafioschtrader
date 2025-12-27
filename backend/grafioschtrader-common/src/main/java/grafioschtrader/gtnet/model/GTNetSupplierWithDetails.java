package grafioschtrader.gtnet.model;

import java.util.List;

import grafioschtrader.entities.GTNetConfig;
import grafioschtrader.entities.GTNetSupplierDetail;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * DTO combining a GTNetConfig header with its associated GTNetSupplierDetail entries.
 *
 * Used in the GTNetExchange UI for expandable row display, showing which remote suppliers
 * can provide price data (intraday or historical) for a specific security or currency pair.
 */
@Schema(description = """
    Combined DTO for displaying supplier information in expandable table rows. Contains the supplier config
    (with GTNet domain reference and last update timestamp) along with a list of detail entries specifying
    which price types (LASTPRICE, HISTORICAL) the supplier offers for a particular security or currency pair.""")
public class GTNetSupplierWithDetails {

  @Schema(description = "Configuration information about the supplier including the GTNet domain and last update time.")
  private GTNetConfig gtNetConfig;

  @Schema(description = "List of detail entries showing which price types this supplier offers for the instrument.")
  private List<GTNetSupplierDetail> details;

  public GTNetSupplierWithDetails() {
  }

  public GTNetSupplierWithDetails(GTNetConfig gtNetConfig, List<GTNetSupplierDetail> details) {
    this.gtNetConfig = gtNetConfig;
    this.details = details;
  }

  public GTNetConfig getGtNetConfig() {
    return gtNetConfig;
  }

  public void setGtNetConfig(GTNetConfig gtNetConfig) {
    this.gtNetConfig = gtNetConfig;
  }

  public List<GTNetSupplierDetail> getDetails() {
    return details;
  }

  public void setDetails(List<GTNetSupplierDetail> details) {
    this.details = details;
  }
}
