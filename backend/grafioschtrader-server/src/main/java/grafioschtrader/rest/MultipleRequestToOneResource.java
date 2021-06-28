package grafioschtrader.rest;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import grafioschtrader.service.MultipleRequestToOneService;
import grafioschtrader.service.MultipleRequestToOneService.DataForCurrencySecuritySearch;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping(RequestMappings.MULTIPLE_REQUEST_TO_ONE_MAP)
@Tag(name = RequestMappings.MULTIPLE_REQUEST_TO_ONE, description = "Controller for multiple request to one")
public class MultipleRequestToOneResource {

  @Autowired
  private MultipleRequestToOneService multipleRequestToOneService;

  @Operation(summary = "Returns the base data for a currency and security search", description = "", tags = {
      RequestMappings.MULTIPLE_REQUEST_TO_ONE })
  @GetMapping(value = "/dataforcurrencysecuritysearch", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<DataForCurrencySecuritySearch> getDataForCurrencySecuritySearch() {
    return new ResponseEntity<>(multipleRequestToOneService.getDataForCurrencySecuritySearch(), HttpStatus.OK);
  }

}
