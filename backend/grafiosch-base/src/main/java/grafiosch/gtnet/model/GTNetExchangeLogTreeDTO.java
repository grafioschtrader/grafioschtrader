package grafiosch.gtnet.model;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Tree structure DTO for GTNet exchange log display.
 * Contains separate trees for supplier and consumer statistics.
 */
@Schema(description = "Exchange log tree for a single GTNet, with supplier and consumer subtrees")
public class GTNetExchangeLogTreeDTO {

  @Schema(description = "GTNet identifier")
  public Integer idGtNet;

  @Schema(description = "Domain name of the remote GTNet")
  public String domainRemoteName;

  @Schema(description = "Root node for supplier (receiver) statistics")
  public GTNetExchangeLogNodeDTO supplierTotal;

  @Schema(description = "Root node for consumer (requester) statistics")
  public GTNetExchangeLogNodeDTO consumerTotal;

  public GTNetExchangeLogTreeDTO() {
  }

  public GTNetExchangeLogTreeDTO(Integer idGtNet, String domainRemoteName) {
    this.idGtNet = idGtNet;
    this.domainRemoteName = domainRemoteName;
    this.supplierTotal = new GTNetExchangeLogNodeDTO("Supplier Total", null, null);
    this.consumerTotal = new GTNetExchangeLogNodeDTO("Consumer Total", null, null);
  }
}
