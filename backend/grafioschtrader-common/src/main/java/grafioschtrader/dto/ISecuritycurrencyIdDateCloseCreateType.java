package grafioschtrader.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * A youngest historical quote together with the way it came into existence. The create type tells apart a price a
 * connector really delivered from one that was fabricated, either by the linear gap filling of the user or by the gap
 * filler of a connector. Only the caller that has to disclose the origin of a displayed price needs this addition,
 * which is why it is kept apart from the plain {@link ISecuritycurrencyIdDateClose}.
 */
public interface ISecuritycurrencyIdDateCloseCreateType extends ISecuritycurrencyIdDateClose {

  /**
   * Ordinal of {@link grafioschtrader.types.HistoryquoteCreateType} of this quote, null for a row written before the
   * column existed.
   * <p>
   * The underlying column is {@code tinyint(1)}, which a projection would otherwise hand out as a boolean and thereby
   * collapse every value greater than one. The query therefore casts it to a signed integer.
   * </p>
   */
  @Schema(description = "Ordinal of HistoryquoteCreateType telling how this quote was created")
  Integer getCreateType();
}
