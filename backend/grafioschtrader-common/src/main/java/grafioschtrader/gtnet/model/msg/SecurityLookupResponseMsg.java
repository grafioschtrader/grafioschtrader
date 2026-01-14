package grafioschtrader.gtnet.model.msg;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;

import grafioschtrader.gtnet.model.SecurityGtnetLookupDTO;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Payload for security metadata lookup response (GT_NET_SECURITY_LOOKUP_RESPONSE_S).
 *
 * Contains a list of matching securities found on the responding GTNet peer.
 */
@Schema(description = """
    Payload for security metadata lookup response. Contains a list of securities matching
    the search criteria from the remote GTNet peer's database.""")
public class SecurityLookupResponseMsg {

  @Schema(description = "List of matching security metadata entries")
  public List<SecurityGtnetLookupDTO> securities;

  public SecurityLookupResponseMsg() {
  }

  public SecurityLookupResponseMsg(List<SecurityGtnetLookupDTO> securities) {
    this.securities = securities;
  }

  @JsonIgnore
  public boolean isEmpty() {
    return securities == null || securities.isEmpty();
  }
}
