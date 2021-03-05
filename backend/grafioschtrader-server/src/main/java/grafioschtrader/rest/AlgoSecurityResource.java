package grafioschtrader.rest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import grafioschtrader.entities.AlgoSecurity;
import grafioschtrader.repository.AlgoSecurityJpaRepository;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping(RequestMappings.ALGOSECURITY_MAP)
@Tag(name = RequestMappings.ALGOASSETCLASS, description = "Controller for top level algorithmic trading assetclass security")
public class AlgoSecurityResource extends UpdateCreateDeleteWithTenantResource<AlgoSecurity> {

  @Autowired
  AlgoSecurityJpaRepository algoSecurityJpaRepository;

  public AlgoSecurityResource() {
    super(AlgoSecurity.class);
  }

  @Override
  protected UpdateCreateDeleteWithTenantJpaRepository<AlgoSecurity> getUpdateCreateJpaRepository() {
    return algoSecurityJpaRepository;
  }

}
