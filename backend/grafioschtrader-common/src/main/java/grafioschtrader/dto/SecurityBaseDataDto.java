package grafioschtrader.dto;

import java.time.LocalDate;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonFormat;

import grafiosch.BaseConstants;
import grafioschtrader.types.DistributionFrequency;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = """
    Master data of one listing as a data provider's detail endpoint delivers it, independent of the provider. A GT field
    is set only when the provider value has exactly its meaning; every other raw value is kept in providerAttributes.
    Mapping raw values to GT terms such as the asset class is left to the caller. The provider never writes a
    security.""")
public record SecurityBaseDataDto(@Schema(description = "Short id of the provider, e.g. 'six'") String providerId,
    @Schema(description = "Key of the listing at the provider that was used for the request, e.g. the SIX ValorId") String providerInstrumentId,
    @Schema(description = "ISIN as the detail endpoint delivers it") String isin,
    @Schema(description = "Trading currency as the detail endpoint delivers it") String currency,
    @Schema(description = "MIC of the listing") String mic, @Schema(description = "Name of the instrument") String name,
    @Schema(description = "Ticker symbol at the exchange") String tickerSymbol,
    @Schema(description = "Two-letter country of the issuer, or domicile of a fund; null when not delivered") String issuerCountry,
    @Schema(description = """
        First trading day of the instrument. Only set when the provider delivers exactly that, e.g. the first trading day
        of a bond; the first trading day of the current listing of a share is no such value.""") @JsonFormat(pattern = BaseConstants.STANDARD_DATE_FORMAT) LocalDate activeFromDate,
    @Schema(description = "Last trading day, e.g. the maturity of a bond; null when open") @JsonFormat(pattern = BaseConstants.STANDARD_DATE_FORMAT) LocalDate activeToDate,
    @Schema(description = "Distribution frequency, only when the provider delivers a frequency such as a coupon frequency") DistributionFrequency distributionFrequency,
    @Schema(description = "Smallest denomination of a bond") Integer denomination,
    @Schema(description = "Product page of the issuer, e.g. of a fund company; null when it cannot be derived") String productLink,
    @Schema(description = "Page of this listing at the stock exchange, e.g. the SIX detail page; null when the provider is no exchange") String stockexchangeLink,
    @Schema(description = """
        First trading day of this listing at the exchange. Unlike activeFromDate it may be much later than the start of
        the instrument, e.g. after a listing change; it only tells how long the listing has been traded.""") @JsonFormat(pattern = BaseConstants.STANDARD_DATE_FORMAT) LocalDate listedSince,
    @Schema(description = """
        True when the provider states that the fund distributes its income, false when it accumulates it; null when the
        provider states nothing, as for shares. It decides whether "no distribution" may be proposed.""") Boolean distributing,
    @Schema(description = """
        All further raw values of the provider, e.g. productLine, secType, assetClass, investmentRegion, couponRate or
        managementFee. They are shown to the user and serve for deriving GT terms, but are never copied into a GT field
        as they are.""") Map<String, String> providerAttributes) {

  public SecurityBaseDataDto {
    providerAttributes = providerAttributes == null ? Map.of() : providerAttributes;
  }
}
