package grafioschtrader.gtnet.model.msg;

import java.util.ArrayList;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Payload for batch security metadata lookup request (GT_NET_SECURITY_BATCH_LOOKUP_SEL_C).
 *
 * Used in M2M communication to request security information for multiple securities from a remote GTNet peer
 * in a single request, reducing network round-trips for bulk operations.
 */
@Schema(description = """
    Payload for batch security metadata lookup request. Contains multiple search criteria entries,
    each with ISIN, currency, and/or ticker symbol. The remote peer will search its database
    for each query and return matching security metadata grouped by query index.""")
public class SecurityBatchLookupMsg {

  @Schema(description = "List of individual lookup queries, each containing search criteria for one security")
  public List<SecurityLookupMsg> queries;

  public SecurityBatchLookupMsg() {
    this.queries = new ArrayList<>();
  }

  public SecurityBatchLookupMsg(List<SecurityLookupMsg> queries) {
    this.queries = queries;
  }

  public List<SecurityLookupMsg> getQueries() {
    return queries;
  }

  public void setQueries(List<SecurityLookupMsg> queries) {
    this.queries = queries;
  }

  public void addQuery(SecurityLookupMsg query) {
    if (this.queries == null) {
      this.queries = new ArrayList<>();
    }
    this.queries.add(query);
  }

  public boolean isEmpty() {
    return queries == null || queries.isEmpty();
  }

  public int size() {
    return queries == null ? 0 : queries.size();
  }
}
