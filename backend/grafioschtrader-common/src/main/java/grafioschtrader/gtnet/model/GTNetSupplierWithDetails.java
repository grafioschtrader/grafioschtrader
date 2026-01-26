package grafioschtrader.gtnet.model;

import java.util.List;

import grafiosch.entities.GTNet;
import grafioschtrader.entities.GTNetSupplierDetail;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * DTO combining a GTNet header with its associated GTNetSupplierDetail entries.
 *
 * Used in the GTNetExchange UI for expandable row display, showing which remote suppliers
 * can provide price data (intraday or historical) for a specific security or currency pair.
 */
@Schema(description = """
    Combined DTO for displaying supplier information in expandable table rows. Contains the GTNet domain
    (with domain name, config, and last update timestamp) along with a list of detail entries specifying
    which price types (LASTPRICE, HISTORICAL) the supplier offers for a particular security or currency pair.""")
public class GTNetSupplierWithDetails {

  @Schema(description = "GTNet domain information including the domain name and configuration.")
  private GTNet gtNet;

  @Schema(description = "List of detail entries showing which price types this supplier offers for the instrument.")
  private List<GTNetSupplierDetail> details;

  public GTNetSupplierWithDetails() {
  }

  public GTNetSupplierWithDetails(GTNet gtNet, List<GTNetSupplierDetail> details) {
    this.gtNet = gtNet;
    this.details = details;
  }

  public GTNet getGtNet() {
    return gtNet;
  }

  public void setGtNet(GTNet gtNet) {
    this.gtNet = gtNet;
  }

  public List<GTNetSupplierDetail> getDetails() {
    return details;
  }

  public void setDetails(List<GTNetSupplierDetail> details) {
    this.details = details;
  }
}
