package grafiosch.dto;

import java.util.ArrayList;
import java.util.List;

import grafiosch.types.ISubscriptionType;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = """
    Encapsulates subscription type information in a format suitable for user interface display and selection.
    It combines human-readable names with the available subscription type options for a specific provider.""")
public class SubscriptionTypeReadableName {
  
  @Schema(description = "Human-readable display name for the provider or subscription category.")
  public String readableName;

  @Schema(description = "List of available subscription types for this provider.")
  public List<ISubscriptionType> subscriptionTypes = new ArrayList<>();

  public SubscriptionTypeReadableName(String readableName) {
    this.readableName = readableName;
  }
}
