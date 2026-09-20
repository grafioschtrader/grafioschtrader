package grafioschtrader.dto;

import java.time.LocalDate;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * One marked instrument as the maintenance table shows it: the marker row itself plus enough of the instrument to
 * recognize it, and the two dates that say whether the automatic filling is actually working.
 */
@Schema(description = """
    A marked instrument together with the name, ISIN and currency of the instrument itself, the last closing price a
    data provider delivered, and the last closing price of any kind. When the two differ, the days in between were
    created by the end of day filling.""")
public interface IBankruptSecurityWithName {

  @Schema(description = "Identifier of the marker row, needed to edit or remove it")
  Integer getIdBankruptSecurity();

  Integer getIdSecuritycurrency();

  @Schema(description = "Name of the instrument")
  String getName();

  String getIsin();

  @Schema(description = "Currency the instrument is quoted in")
  String getCurrency();

  @Schema(description = "Day from which the issuer stopped delivering prices, as recorded by the user")
  LocalDate getNoDataSince();

  String getNote();

  @Schema(description = "Newest closing price that a connector, an import or the user supplied, so not a filled one")
  LocalDate getLastRealQuoteDate();

  @Schema(description = "Newest closing price of any kind, filled days included")
  LocalDate getLastQuoteDate();

  @Schema(description = "User who created the marker, so the table can decide whether it may be edited")
  Integer getCreatedBy();
}
