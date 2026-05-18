package grafioschtrader.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Projection used by the admin UI's instrument-picker dropdown: identifies an unmapped risk-free Security and supplies
 * the derived FRED series id (urlHistoryExtend) so the table's third column can be populated without an extra fetch.
 */
@Schema(description = "Single option in the risk-free-instrument picker dropdown.")
public interface RiskFreeInstrumentOption {

  @Schema(description = "Securitycurrency id of the candidate instrument.")
  Integer getIdSecuritycurrency();

  @Schema(description = "Display name of the Security (e.g. 'USD 3M Risk-Free Rate (FRED DGS3MO)').")
  String getName();

  @Schema(description = "ISO currency code of the underlying Security (e.g. 'USD').")
  String getCurrency();

  @Schema(description = "FRED series id or generic url_history_extend used to fetch this rate (e.g. 'DGS3MO').")
  String getUrlHistoryExtend();
}
