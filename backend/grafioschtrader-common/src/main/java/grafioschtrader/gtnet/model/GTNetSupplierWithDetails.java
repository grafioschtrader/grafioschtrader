package grafioschtrader.gtnet.model;

import java.util.List;

import grafiosch.entities.GTNet;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * DTO combining a GTNet header with its associated GTNetSupplierDetailWithSettings entries.
 *
 * Used in the GTNetExchange UI for expandable row display, showing which remote suppliers
 * can provide price data (intraday or historical) for a specific security or currency pair.
 */
@Schema(description = """
    Combined DTO for displaying supplier information in expandable table rows. Contains the GTNet domain
    (with domain name, config, and last update timestamp) along with a list of detail entries with their
    settings specifying which price types (LASTPRICE, HISTORICAL) the supplier offers.""")
public class GTNetSupplierWithDetails {

  @Schema(description = "GTNet domain information including the domain name and configuration.")
  private GTNet gtNet;

  @Schema(description = "List of detail entries with settings showing which price types this supplier offers.")
  private List<GTNetSupplierDetailWithSettings> details;

  public GTNetSupplierWithDetails() {
  }

  public GTNetSupplierWithDetails(GTNet gtNet, List<GTNetSupplierDetailWithSettings> details) {
    this.gtNet = gtNet;
    this.details = details;
  }

  public GTNet getGtNet() {
    return gtNet;
  }

  public void setGtNet(GTNet gtNet) {
    this.gtNet = gtNet;
  }

  public List<GTNetSupplierDetailWithSettings> getDetails() {
    return details;
  }

  public void setDetails(List<GTNetSupplierDetailWithSettings> details) {
    this.details = details;
  }
}
