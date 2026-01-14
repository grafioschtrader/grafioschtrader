package grafioschtrader.gtnet.model;

import java.util.ArrayList;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Response wrapper for security lookup results from local database and GTNet peers.
 */
@Schema(description = """
    Response wrapper for security lookup results. Contains matching securities from the local database
    and from GTNet peers, along with statistics about the query execution.""")
public class SecurityGtnetLookupResponse {

  @Schema(description = "List of matching securities from local database and responding peers")
  private List<SecurityGtnetLookupDTO> securities;

  @Schema(description = "Number of GTNet peers that were queried")
  private int peersQueried;

  @Schema(description = "Number of GTNet peers that responded successfully")
  private int peersResponded;

  @Schema(description = "Error messages from peers that failed to respond or rejected the request")
  private List<String> errors;

  public SecurityGtnetLookupResponse() {
    this.securities = new ArrayList<>();
    this.errors = new ArrayList<>();
  }

  public SecurityGtnetLookupResponse(List<SecurityGtnetLookupDTO> securities, int peersQueried, int peersResponded) {
    this.securities = securities != null ? securities : new ArrayList<>();
    this.peersQueried = peersQueried;
    this.peersResponded = peersResponded;
    this.errors = new ArrayList<>();
  }

  public List<SecurityGtnetLookupDTO> getSecurities() {
    return securities;
  }

  public void setSecurities(List<SecurityGtnetLookupDTO> securities) {
    this.securities = securities;
  }

  public int getPeersQueried() {
    return peersQueried;
  }

  public void setPeersQueried(int peersQueried) {
    this.peersQueried = peersQueried;
  }

  public int getPeersResponded() {
    return peersResponded;
  }

  public void setPeersResponded(int peersResponded) {
    this.peersResponded = peersResponded;
  }

  public List<String> getErrors() {
    return errors;
  }

  public void setErrors(List<String> errors) {
    this.errors = errors;
  }

  public void addError(String error) {
    if (this.errors == null) {
      this.errors = new ArrayList<>();
    }
    this.errors.add(error);
  }

  public void addSecurity(SecurityGtnetLookupDTO security) {
    if (this.securities == null) {
      this.securities = new ArrayList<>();
    }
    this.securities.add(security);
  }
}
