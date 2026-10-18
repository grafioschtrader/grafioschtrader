package grafioschtrader.dto;

import java.time.LocalDate;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonFormat;

import grafiosch.BaseConstants;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = """
    What the language model answered for one security when the description of a field was tested, and how the check
    against the source judged it. Nothing of it is stored. Unlike a proposal of a run, a discarded answer keeps the
    value, the source and the excerpt of the model, so the user sees why the check failed.""")
public record UDFSuggestTestResult(@Schema(description = "The security") Integer idSecuritycurrency,
    @Schema(description = "The outcome of the check") Outcome outcome,
    @Schema(description = "The value: canonical when proposed, as answered by the model when discarded") String value,
    @Schema(description = "The page the model named as source") String sourceUrl,
    @Schema(description = "The excerpt of the page the model quoted") String evidence,
    @Schema(description = "The date the page gives for the value, or null") @JsonFormat(pattern = BaseConstants.STANDARD_DATE_FORMAT) LocalDate asOfDate,
    @Schema(description = "Why the answer was discarded or not found, or why the test failed; English") String note,
    @Schema(description = """
        True when a proposed value equals the existing value of the field, false when it differs, null when there is
        nothing to compare""") Boolean equalsExisting,
    @Schema(description = """
        One line per page the model fetched: the URL and its status, its text length or why it could not be read. It
        shows why nothing was found, for example a source that refuses the request""") List<String> fetches) {

  /** Outcome of testing one security. */
  public enum Outcome {
    /** The answer passed every check; a run would show it as a proposal. */
    PROPOSED,
    /** The model answered, but a check against the source failed; a run would discard it. */
    DISCARDED,
    /** The model found no value. */
    NOT_FOUND,
    /** A page could not be read or the model gave no usable answer; a run would ask again later. */
    FAILED
  }
}
