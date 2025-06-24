package grafioschtrader.rest;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import grafiosch.rest.UpdateCreateDeleteWithTenantJpaRepository;
import grafiosch.rest.UpdateCreateDeleteWithTenantResource;
import grafioschtrader.GlobalConstants;
import grafioschtrader.algo.AlgoSecurityStrategyImplType;
import grafioschtrader.entities.AlgoSecurity;
import grafioschtrader.repository.AlgoSecurityJpaRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping(RequestGTMappings.ALGOSECURITY_MAP)
@Tag(name = RequestGTMappings.ALGOSECURITY, description = "Controller for top level algorithmic trading assetclass security")
public class AlgoSecurityResource extends UpdateCreateDeleteWithTenantResource<AlgoSecurity> {

  @Autowired
  private AlgoSecurityJpaRepository algoSecurityJpaRepository;

  @Operation(summary = "", description = "", tags = { RequestGTMappings.ALGOSECURITY })
  @GetMapping(value = "/security/{idSecuritycurrency}", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<AlgoSecurityStrategyImplType> getAlgoSecurityStrategyImplTypeByIdSecuritycurrency(
      @PathVariable final Integer idSecuritycurrency) {
    return new ResponseEntity<>(
        algoSecurityJpaRepository.getAlgoSecurityStrategyImplTypeByIdSecuritycurrency(idSecuritycurrency),
        HttpStatus.OK);
  }

  public AlgoSecurityResource() {
    super(AlgoSecurity.class);
  }

  @Override
  protected UpdateCreateDeleteWithTenantJpaRepository<AlgoSecurity> getUpdateCreateJpaRepository() {
    return algoSecurityJpaRepository;
  }

  @Override
  protected String getPrefixEntityLimit() {
    return GlobalConstants.GT_LIMIT_DAY;
  }

}
