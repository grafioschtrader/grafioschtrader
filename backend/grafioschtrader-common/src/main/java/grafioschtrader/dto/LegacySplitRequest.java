package grafioschtrader.dto;

import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonFormat;

import grafiosch.BaseConstants;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Request body for the bulk split adjustment endpoint
 * {@code POST /historyquotes/legacy/{idSecuritycurrency}/split}. The post-split factor is
 * {@code toFactor / fromFactor}; a 2/1 split is therefore {@code fromFactor = 1, toFactor = 2}.
 * Rows with {@code date < splitDate} are adjusted; rows on or after {@code splitDate} are left
 * untouched because they are assumed to already reflect the post-split scale.
 */
@Schema(description = """
    Request body for applying a forgotten split to archived (historyquote_legacy) rows. Only rows
    with date strictly before splitDate are updated. OHLC values are multiplied by
    fromFactor/toFactor; volume is multiplied by toFactor/fromFactor.""")
public class LegacySplitRequest {

  @Schema(description = "Effective split date. Only archived rows with date < splitDate are adjusted.")
  @JsonFormat(pattern = BaseConstants.STANDARD_DATE_FORMAT)
  public LocalDate splitDate;

  @Schema(description = "Pre-split factor. For a 2/1 split this is 1; for a 1/2 reverse split this is 2.")
  public int fromFactor;

  @Schema(description = "Post-split factor. For a 2/1 split this is 2; for a 1/2 reverse split this is 1.")
  public int toFactor;
}
