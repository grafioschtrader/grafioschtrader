package grafioschtrader.reportviews.securityaccount;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * A value of the report that cannot be drawn as a treemap tile. It is listed below the chart so that nothing disappears
 * silently and the tiles plus these entries still add up to the report total.
 */
@Schema(description = """
    A value that the holdings treemap cannot draw, listed in a note below the chart. Together with the tiles it adds up
    to the report total.""")
public record HoldingsTreemapExcluded(

    @Schema(description = "Security name or ISO currency code of the excluded value") String label,

    @Schema(description = "Value in main currency") double valueMC,

    @Schema(description = """
        Translation key of the reason: TREEMAP_NEGATIVE_VALUE (a treemap cannot draw a negative area) or
        TREEMAP_PRICE_MISSING (no reliable value without price or exchange rate)""") String reasonKey) {

  public static final String TREEMAP_NEGATIVE_VALUE = "TREEMAP_NEGATIVE_VALUE";
  public static final String TREEMAP_PRICE_MISSING = "TREEMAP_PRICE_MISSING";
}
