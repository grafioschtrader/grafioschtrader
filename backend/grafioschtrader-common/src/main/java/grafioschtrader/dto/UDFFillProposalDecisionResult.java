package grafioschtrader.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "What happened to one proposal the user accepted or rejected.")
public record UDFFillProposalDecisionResult(@Schema(description = "The security") Integer idSecuritycurrency,
    @Schema(description = "The outcome") Outcome outcome) {

  /** Outcome of a decision. */
  public enum Outcome {
    /** The proposal was accepted; the value is written and automatic. */
    APPLIED,
    /** The proposal was rejected. */
    REJECTED,
    /** The cell got a manual value meanwhile; the manual value stays and the proposal is dropped. */
    CONFLICT,
    /** The proposal, its version or its value changed meanwhile; nothing was done. */
    STALE
  }
}
