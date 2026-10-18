package grafioschtrader.dto;

import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonFormat;

import grafiosch.BaseConstants;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = """
    A value proposed by a language model and verified against its source: the source answered, names the security and
    contains the excerpt, and the excerpt contains the value. This establishes plausibility, not truth. The proposal is
    not part of the values of the field before the user accepts it.""")
public record UDFFillProposal(@Schema(description = "The security") Integer idSecuritycurrency,
    @Schema(description = "Name of the security") String name,
    @Schema(description = "The proposed value in canonical form") String value,
    @Schema(description = "The page that names the value") String sourceUrl,
    @Schema(description = "The excerpt of the page that contains the value") String evidence,
    @Schema(description = "The date the page gives for the value, or null") @JsonFormat(pattern = BaseConstants.STANDARD_DATE_FORMAT) LocalDate asOfDate,
    @Schema(description = "The fill rule version of the proposal; an accept or reject must name it") int ruleVersion) {
}
