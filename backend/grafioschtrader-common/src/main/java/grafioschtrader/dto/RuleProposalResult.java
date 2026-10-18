package grafioschtrader.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = """
    A template rule written by the language model, evaluated like the test of a rule against all values of the field.
    The model never writes a value; the user accepts the rule into the field, edits it or discards it.""")
public record RuleProposalResult(@Schema(description = "The rule the model wrote, null when it wrote none") String rule,
    @Schema(description = "The translated parse error of the rule, null when it parses") String parseError,
    @Schema(description = "The counters and examples of the rule, null when it does not parse") UDFFillTestResult testResult) {
}
