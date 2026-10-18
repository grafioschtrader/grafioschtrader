package grafioschtrader.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Result of one model request using only the selected tenant key, without fallback.")
public record AIKeyTestResult(boolean succeeded, String reason) {
}
