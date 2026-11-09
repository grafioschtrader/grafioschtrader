package grafioschtrader.rest;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import grafioschtrader.dto.CurrencypairLookupResponse;
import grafioschtrader.instrument.lookup.CurrencypairLookupService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * REST controller of the currency pair lookup, which proposes the connectors and their URL extensions for a currency
 * pair that is being created. It reads from external data providers and writes nothing.
 */
@RestController
@RequestMapping(RequestGTMappings.CURRENCYPAIRLOOKUP_MAP)
@Tag(name = RequestGTMappings.CURRENCYPAIRLOOKUP, description = "Looks up the connectors of a currency pair")
public class CurrencypairLookupResource {

  @Autowired
  private CurrencypairLookupService currencypairLookupService;

  @Operation(summary = "Look up the connectors of a currency pair", description = """
      Tries every activated connector that serves currency pairs of this kind for the history and the intraday feed,
      with the URL extension it derives from the currencies or from a provider search. Each connector comes with the
      outcome of a trial request; a history connector also with the oldest closing price it delivers. Nothing is
      saved.""", tags = { RequestGTMappings.CURRENCYPAIRLOOKUP })
  @GetMapping(value = "/lookup", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<CurrencypairLookupResponse> lookup(
      @Parameter(description = "Base currency, e.g. EUR or BTC", required = true) @RequestParam String fromCurrency,
      @Parameter(description = "Quote currency, e.g. CAD", required = true) @RequestParam String toCurrency) {
    return ResponseEntity.ok(currencypairLookupService.lookup(fromCurrency, toCurrency));
  }
}
