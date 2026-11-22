package grafioschtrader.dto;

import grafioschtrader.dto.SpinOffPreviewDto.Decision;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Account decision and parent units accepted in the spin-off preview")
public record SpinOffDecisionDto(Integer idSecurityaccount, Double parentUnits, Decision decision) {
}
