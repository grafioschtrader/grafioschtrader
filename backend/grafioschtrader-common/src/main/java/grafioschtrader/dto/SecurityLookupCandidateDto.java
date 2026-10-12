package grafioschtrader.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonFormat;

import grafiosch.BaseConstants;
import grafioschtrader.types.AssetclassType;
import grafioschtrader.types.DistributionFrequency;
import grafioschtrader.types.SecuritySearchStatus;
import grafioschtrader.types.SpecialInvestmentInstruments;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = """
    One listing proposed by the security lookup for an instrument that is being created. It merges the search result
    and the master data of the provider and carries, per feed, the connectors that can serve this listing. The user
    chooses one candidate and at most one connector per feed; the choice is copied into the security form and saved
    through the normal dialog.""")
public record SecurityLookupCandidateDto(
    @Schema(description = "Short id of the provider that proposed the listing, e.g. 'six'") String providerId,
    @Schema(description = "Key of the listing at the provider, e.g. the SIX ValorId 'CH1148308724CHF4'") String providerInstrumentId,
    @Schema(description = "Instrument type in the provider's own vocabulary, e.g. SIX product line 'DB'") String providerInstrumentType,
    @Schema(description = "ISIN of the listing") String isin,
    @Schema(description = "Trading currency of the listing") String currency,
    @Schema(description = "MIC of the listing") String mic, @Schema(description = "Name of the instrument") String name,
    @Schema(description = "Ticker symbol at the exchange") String tickerSymbol,
    @Schema(description = "Two-letter country of the issuer or domicile of a fund") String issuerCountry,
    @Schema(description = "First trading day, when the provider delivers exactly that") @JsonFormat(pattern = BaseConstants.STANDARD_DATE_FORMAT) LocalDate activeFromDate,
    @Schema(description = "Last trading day, e.g. the maturity of a bond") @JsonFormat(pattern = BaseConstants.STANDARD_DATE_FORMAT) LocalDate activeToDate,
    @Schema(description = "Distribution frequency, e.g. the coupon frequency of a bond") DistributionFrequency distributionFrequency,
    @Schema(description = "Smallest denomination of a bond") Integer denomination,
    @Schema(description = "Product page of the issuer, only derived for funds from an issuer template") String productLink,
    @Schema(description = "Page of the listing at the stock exchange, e.g. the SIX detail page") String stockexchangeLink,
    @Schema(description = "Asset class type the provider reports for the listing; null when not unambiguous") AssetclassType categoryType,
    @Schema(description = "Financial instrument the provider reports for the listing; null when not unambiguous") SpecialInvestmentInstruments specialInvestmentInstrument,
    @Schema(description = """
        Id of the asset class the user chose before the lookup; providers and connectors were selected by its type and
        financial instrument""") Integer matchedAssetClassId,
    @Schema(description = """
        True when the type the provider reports contradicts the chosen asset class, e.g. SIX lists a bond while an
        equity asset class was chosen. The listing is kept, since the provider data may be the incomplete side.""") boolean instrumentTypeMismatch,
    @Schema(description = "Outcome of the master data request; the search values remain when it failed") SecuritySearchStatus baseDataStatus,
    @Schema(description = "Short technical explanation of a failed master data request") String baseDataDetail,
    @Schema(description = "All further raw values of the provider, shown when the row is expanded") Map<String, String> providerAttributes,
    @Schema(description = "Connectors for historical end-of-day prices") List<ConnectorAlternativeDto> historyConnectors,
    @Schema(description = "Connectors for intraday prices") List<ConnectorAlternativeDto> intraConnectors,
    @Schema(description = "Connectors for dividends") List<ConnectorAlternativeDto> dividendConnectors,
    @Schema(description = "Connectors for splits") List<ConnectorAlternativeDto> splitConnectors,
    @Schema(description = """
        True when the instrument distributes nothing: the master data says so, e.g. an accumulating fund, or no
        dividend connector delivered a single dividend and at least one of them concludes "no distribution". A dividend
        connector is then pointless, so the dialog offers none for taking over.""") boolean distributesNothing,
    @Schema(description = "Every provider that found this listing, the exchange provider first") List<String> providerIds,
    @Schema(description = """
        Source of each set field, keyed by field name: a provider id such as 'six' or 'yahoo', or 'derived' for a
        value a rule produced, e.g. a hyperlink from a template""") Map<String, String> fieldSources) {
}
