package grafioschtrader.reportviews.historyquotequality;

import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonFormat;

import grafiosch.BaseConstants;
import grafioschtrader.dto.IHistoryquoteQuality;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Extends historical quote quality metrics with security-specific properties for display in the API.")
public interface IHistoryquoteQualityWithSecurityProp extends IHistoryquoteQuality {

    @Schema(description = "The human-readable name of the security, shown as the label to the user.")
    String getName();

    @Schema(description = "The ISO currency code of the security, shown as the label to the user.")
    String getCurrency();

    @Schema(description = "Date when the security became active, formatted as YYYY-MM-DD.")
    @JsonFormat(pattern = BaseConstants.STANDARD_DATE_FORMAT)
    LocalDate getActiveFromDate();

    @Schema(description = "Date when the security was deactivated, formatted as YYYY-MM-DD; null if still active.")
    @JsonFormat(pattern = BaseConstants.STANDARD_DATE_FORMAT)
    LocalDate getActiveToDate();

    @Schema(description = "Internal identifier of the Security entity.")
    int getIdSecurity();

    @Schema(description = "Count of quotes created by the connector (create_type = CONNECTOR_CREATED).")
    @Override
    Integer getConnectorCreated();

    @Schema(description = "Count of quotes filled on non-trading days (no official trading calendar).")
    Integer getFilledNoTradeDay();

    @Schema(description = "Count of quotes manually imported (create_type = MANUAL_IMPORTED).")
    @Override
    Integer getManualImported();

    @Schema(description = "Count of quotes filled via linear interpolation (create_type = FILLED_LINEAR).")
    @Override
    Integer getFilledLinear();
}
