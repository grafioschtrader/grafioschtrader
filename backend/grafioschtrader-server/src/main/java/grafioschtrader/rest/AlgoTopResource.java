package grafioschtrader.rest;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import grafiosch.entities.User;
import grafiosch.rest.UpdateCreateDeleteWithTenantJpaRepository;
import grafiosch.rest.UpdateCreateDeleteWithTenantResource;
import grafioschtrader.GlobalConstants;
import grafioschtrader.algo.AlgoTopCreate;
import grafioschtrader.entities.AlgoTop;
import grafioschtrader.repository.AlgoTopJpaRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping(RequestGTMappings.ALGOTOP_MAP)
@Tag(name = RequestGTMappings.ALGOTOP, description = "Controller for top level algorithmic trading strategy")
public class AlgoTopResource extends UpdateCreateDeleteWithTenantResource<AlgoTop> {

  @Autowired
  private AlgoTopJpaRepository algoTopJpaRepository;

  public AlgoTopResource() {
    super(AlgoTop.class);
  }

  @Operation(summary = "Returns all top level allgorithmic tranding", description = "Can be used to shown in a tree", tags = {
      RequestGTMappings.ALGOTOP })
  @GetMapping(value = "/tenant", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<List<AlgoTop>> getAlgoTopByIdTenantOrderByName() {
    final User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    return new ResponseEntity<>(algoTopJpaRepository.findByIdTenantOrderByName(user.getIdTenant()), HttpStatus.OK);
  }

  @Operation(summary = "Returns top level allgorithmic tranding by specified ", description = "", tags = {
      RequestGTMappings.ALGOTOP })
  @GetMapping(value = "/{idAlgoAssetclassSecurity}", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<AlgoTop> getAlgoTopByIdAlgoAssetclassSecurity(
      @Parameter(description = "Id of top level algorithmic trading", required = true) @PathVariable final Integer idAlgoAssetclassSecurity) {
    final User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    return new ResponseEntity<>(
        algoTopJpaRepository.findByIdTenantAndIdAlgoAssetclassSecurity(user.getIdTenant(), idAlgoAssetclassSecurity),
        HttpStatus.OK);
  }

  @Override
  protected UpdateCreateDeleteWithTenantJpaRepository<AlgoTop> getUpdateCreateJpaRepository() {
    return algoTopJpaRepository;
  }

  @Operation(summary = "", description = "", tags = { RequestGTMappings.ALGOTOP })
  @PostMapping(value = "/create", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<AlgoTop> create(@RequestBody AlgoTopCreate algoTopCreate) throws Exception {
    return createEntity(algoTopCreate);
  }

  @Override
  protected String getPrefixEntityLimit() {
    return GlobalConstants.GT_LIMIT_DAY;
  }

}
