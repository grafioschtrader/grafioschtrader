package grafioschtrader.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Return this class after import.
 *
 * @author Hugo Graf
 *
 */
public class UploadHistoryquotesSuccess {
  @Schema(description = "Number of imported EOD records")
  public int success = 0;

  @Schema(description = "Number of not imported records, because a record with this data exists already")
  public int notOverridden = 0;

  @Schema(description = "Number of records, that does not match the range or format of accepted values")
  public int validationErrors = 0;

  @Schema(description = "Counts the not imported records with ambiguous date in that data set")
  public int duplicatedInImport = 0;

  @Schema(description = "Counts the records in the data set which are not in the range of security life time")
  public int outOfDateRange = 0;
}
