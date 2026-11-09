package grafioschtrader.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = """
    A currency pair as one data provider knows it, found by the currency pair lookup. A connector builds its URL
    extension from the hit of its own provider, or from that of a provider sharing its identifiers - Consorsbank, for
    example, quotes currencies under the same FactSet notations as onvista.""")
public record CurrencypairSearchHit(
    @Schema(description = "Short id of the provider that delivered the hit, e.g. 'onvista'") String providerId,
    @Schema(description = "Identifier of the pair at the provider, e.g. 'EURCAD' at onvista or '1xCHFVS' at Boursorama") String instrumentId,
    @Schema(description = "Notation of the quote at the provider, e.g. the FactSet notation 8361803; null when the provider has none") String notation,
    @Schema(description = "Exchange code of the quote at the provider, e.g. '@W$' for FactSet synthetic rates; may be null") String exchangeCode,
    @Schema(description = "Name of the pair as the provider writes it") String name) {
}
