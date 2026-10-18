package grafioschtrader.dto;

import java.util.List;

import grafioschtrader.types.AIKeySource;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = """
    What a rule proposal would send to the language model, shown to the user before the first request. The user may
    deselect examples; only the selected ones are sent.""")
public record RuleProposalPreview(@Schema(description = """
    The configured providers with their model in order of priority, for example gemini (gemini-flash-latest). The
    examples go to the first one that answers.""") List<Provider> providers,
    @Schema(description = "The examples, at most 20, in the order they would be sent") List<RuleProposalExample> examples) {
  public record Provider(String label, AIKeySource keySource) {
  }
}
