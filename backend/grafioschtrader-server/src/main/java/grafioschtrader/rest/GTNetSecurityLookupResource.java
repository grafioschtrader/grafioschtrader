package grafioschtrader.rest;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import grafioschtrader.gtnet.GTNetSecurityLookupService;
import grafioschtrader.gtnet.model.SecurityGtnetLookupRequest;
import grafioschtrader.gtnet.model.SecurityGtnetLookupResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * REST controller for security metadata lookup via GTNet.
 * Allows searching for security information by ISIN, currency, and/or ticker symbol
 * from local database and GTNet peers.
 */
@RestController
@RequestMapping(RequestGTMappings.GTNETSECURITYLOOKUP_MAP)
@Tag(name = RequestGTMappings.GTNETSECURITYLOOKUP, description = "Controller for GTNet security metadata lookup")
public class GTNetSecurityLookupResource {

  @Autowired
  private GTNetSecurityLookupService gtNetSecurityLookupService;

  /**
   * Lookup security metadata by ISIN, currency, and/or ticker symbol.
   * Searches local database first, then queries configured GTNet peers.
   *
   * @param request the search criteria containing ISIN, currency, and/or ticker symbol
   * @return response containing matching securities and query statistics
   */
  @Operation(summary = "Lookup security metadata",
      description = """
          Searches for security metadata matching the provided criteria. First checks the local database,
          then queries configured GTNet peers. Returns matching securities with instance-agnostic metadata
          including asset class type, stock exchange MIC, and connector hints.""",
      tags = { RequestGTMappings.GTNETSECURITYLOOKUP })
  @PostMapping(value = "/lookup", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<SecurityGtnetLookupResponse> lookupSecurity(
      @RequestBody SecurityGtnetLookupRequest request) {
    return ResponseEntity.ok(gtNetSecurityLookupService.lookupSecurity(request));
  }
}
