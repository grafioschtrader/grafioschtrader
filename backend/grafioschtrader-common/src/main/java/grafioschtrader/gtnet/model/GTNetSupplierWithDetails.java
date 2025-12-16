package grafioschtrader.gtnet.model;

import java.util.List;

import grafioschtrader.entities.GTNetSupplier;
import grafioschtrader.entities.GTNetSupplierDetail;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * DTO combining a GTNetSupplier header with its associated GTNetSupplierDetail entries.
 *
 * Used in the GTNetExchange UI for expandable row display, showing which remote suppliers
 * can provide price data (intraday or historical) for a specific security or currency pair.
 */
@Schema(description = """
    Combined DTO for displaying supplier information in expandable table rows. Contains the supplier header
    (with GTNet domain reference and last update timestamp) along with a list of detail entries specifying
    which price types (LASTPRICE, HISTORICAL) the supplier offers for a particular security or currency pair.""")
public class GTNetSupplierWithDetails {

  @Schema(description = "Header information about the supplier including the GTNet domain and last update time.")
  private GTNetSupplier supplier;

  @Schema(description = "List of detail entries showing which price types this supplier offers for the instrument.")
  private List<GTNetSupplierDetail> details;

  public GTNetSupplierWithDetails() {
  }

  public GTNetSupplierWithDetails(GTNetSupplier supplier, List<GTNetSupplierDetail> details) {
    this.supplier = supplier;
    this.details = details;
  }

  public GTNetSupplier getSupplier() {
    return supplier;
  }

  public void setSupplier(GTNetSupplier supplier) {
    this.supplier = supplier;
  }

  public List<GTNetSupplierDetail> getDetails() {
    return details;
  }

  public void setDetails(List<GTNetSupplierDetail> details) {
    this.details = details;
  }
}
