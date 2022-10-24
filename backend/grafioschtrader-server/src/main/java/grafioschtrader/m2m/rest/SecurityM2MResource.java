package grafioschtrader.m2m.rest;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import grafioschtrader.entities.Security;
import grafioschtrader.repository.SecurityJpaRepository;
import grafioschtrader.rest.RequestMappings;
import io.swagger.v3.oas.annotations.Operation;

@RestController
@RequestMapping(RequestMappings.SECURITY_M2M_MAP)
public class SecurityM2MResource {

  @Autowired
  private SecurityJpaRepository securityJpaRepository;
  
  @Operation(summary = "Returns a security ISIN and currency", description = "", tags = {RequestMappings.SECURITY_M2M })
  @GetMapping(value = "/{isin}/{currency}", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<Security> getSecurityByIdSecuritycurrency(@PathVariable final String isin,
      @PathVariable final String currency) {
    Security security = securityJpaRepository
        .findByIsinAndCurrency(isin, currency);
    return new ResponseEntity<>(security, HttpStatus.OK);
  }
  
}
