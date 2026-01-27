package grafioschtrader.gtnet.model.msg;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnore;

import grafioschtrader.gtnet.model.SecurityGtnetLookupDTO;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Payload for batch security metadata lookup response (GT_NET_SECURITY_BATCH_LOOKUP_RESPONSE_S).
 *
 * Contains a map of query index to matching securities found on the responding GTNet peer.
 * Each entry corresponds to the query at the same index in the original batch request.
 */
@Schema(description = """
    Payload for batch security metadata lookup response. Contains a map where each key is the
    query index from the original batch request, and the value is a list of matching securities
    from the remote GTNet peer's database.""")
public class SecurityBatchLookupResponseMsg {

  @Schema(description = "Map of query index to list of matching security metadata entries")
  public Map<Integer, List<SecurityGtnetLookupDTO>> results;

  public SecurityBatchLookupResponseMsg() {
    this.results = new HashMap<>();
  }

  public SecurityBatchLookupResponseMsg(Map<Integer, List<SecurityGtnetLookupDTO>> results) {
    this.results = results;
  }

  public Map<Integer, List<SecurityGtnetLookupDTO>> getResults() {
    return results;
  }

  public void setResults(Map<Integer, List<SecurityGtnetLookupDTO>> results) {
    this.results = results;
  }

  public void addResult(Integer queryIndex, List<SecurityGtnetLookupDTO> securities) {
    if (this.results == null) {
      this.results = new HashMap<>();
    }
    this.results.put(queryIndex, securities);
  }

  @JsonIgnore
  public boolean isEmpty() {
    return results == null || results.isEmpty();
  }

  @JsonIgnore
  public int getTotalResultCount() {
    if (results == null) {
      return 0;
    }
    return results.values().stream()
        .mapToInt(list -> list == null ? 0 : list.size())
        .sum();
  }
}
