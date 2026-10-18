package grafioschtrader.dto;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonFormat;

import grafiosch.BaseConstants;
import grafioschtrader.types.UDFFillSource;
import grafioschtrader.types.UDFFillState;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = """
    Fill mark of one cell of a user-defined security field on the watchlist. An automatic cell, whose fill row is
    APPLIED and still holds the displayed value, a cell the user locked, and a cell with a proposal of a language model
    awaiting the decision of the user carry such a mark. A proposal is never part of the values themselves.""")
public record UDFFillInfo(@Schema(description = "The fill source that produced the cell") UDFFillSource fillSource,
    @Schema(description = "APPLIED for an automatic value, LOCKED for a cell the user took over, PROPOSED for a proposal") UDFFillState fillState,
    @Schema(description = "UTC time of the last definitive outcome for this cell") @JsonFormat(pattern = BaseConstants.STANDARD_DATE_TIME_FORMAT) LocalDateTime checkedTime,
    @Schema(description = "Optional page from which the value was taken") String sourceUrl,
    @Schema(description = "The proposed value of a PROPOSED cell, null otherwise") String proposedValue,
    @Schema(description = "The excerpt of the source of a PROPOSED cell, null otherwise") String evidence,
    @Schema(description = "The fill rule version the proposal was made for; an accept must name it") Integer ruleVersion) {

  /**
   * Creates the mark of an automatic or locked cell, which carries no proposal.
   */
  public UDFFillInfo(UDFFillSource fillSource, UDFFillState fillState, LocalDateTime checkedTime, String sourceUrl) {
    this(fillSource, fillState, checkedTime, sourceUrl, null, null, null);
  }
}
