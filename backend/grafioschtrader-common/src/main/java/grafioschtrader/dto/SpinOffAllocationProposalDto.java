package grafioschtrader.dto;

import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonFormat;

import grafiosch.BaseConstants;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Market-value allocation based on the first common closing-price day")
public record SpinOffAllocationProposalDto(
    @JsonFormat(pattern = BaseConstants.STANDARD_DATE_FORMAT) LocalDate priceDate, Double parentClose,
    Double childClose, Double fxChildToParent, Double proposedAllocation) {
}
