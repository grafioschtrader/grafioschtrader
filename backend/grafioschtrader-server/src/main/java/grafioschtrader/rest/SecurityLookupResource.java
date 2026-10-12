package grafioschtrader.rest;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import grafiosch.entities.User;
import grafioschtrader.dto.SecurityLookupResponse;
import grafioschtrader.instrument.lookup.SecurityLookupService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * REST controller of the security lookup, which proposes master data and connectors for a security that is being
 * created. It reads from external data providers and writes nothing.
 */
@RestController
@RequestMapping(RequestGTMappings.SECURITYLOOKUP_MAP)
@Tag(name = RequestGTMappings.SECURITYLOOKUP, description = "Controller for looking up a security at data providers")
public class SecurityLookupResource {

  @Autowired
  private SecurityLookupService securityLookupService;

  @Operation(summary = "Look up a security by ISIN, currency, exchange and asset class", description = """
      Searches the data providers that cover the asset class at the exchange, fetches the master data of the listing
      and proposes per feed the connectors that deliver data for it, each with its derived URL extension and the
      outcome of a trial request. Nothing is saved; a provider that cannot be asked reports its status instead of
      failing the request.""", tags = { RequestGTMappings.SECURITYLOOKUP })
  @GetMapping(value = "/lookup", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<SecurityLookupResponse> lookup(
      @Parameter(description = "ISIN of the instrument", required = true) @RequestParam String isin,
      @Parameter(description = "Trading currency, e.g. CHF", required = true) @RequestParam String currency,
      @Parameter(description = "MIC of the exchange, e.g. XSWX", required = true) @RequestParam String mic,
      @Parameter(description = "Asset class chosen in the form", required = true) @RequestParam Integer idAssetClass,
      @Parameter(description = "Ticker entered in the form, a fallback search term") @RequestParam(required = false) String ticker) {
    final User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    return ResponseEntity.ok(
        securityLookupService.lookup(isin, currency, mic, ticker, idAssetClass, user.createAndGetJavaLocale()));
  }
}
