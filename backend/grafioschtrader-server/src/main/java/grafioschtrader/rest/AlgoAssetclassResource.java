package grafioschtrader.rest;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import grafioschtrader.entities.AlgoAssetclass;
import grafioschtrader.entities.User;
import grafioschtrader.repository.AlgoAssetclassJpaRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping(RequestMappings.ALGOASSETCLASS_MAP)
@Tag(name = RequestMappings.ALGOASSETCLASS, description = "Controller for top level algorithmic trading assetclass")
public class AlgoAssetclassResource extends UpdateCreateDeleteWithTenantResource<AlgoAssetclass> {

  @Autowired
  AlgoAssetclassJpaRepository algoAssetclassJpaRepository;

  public AlgoAssetclassResource() {
    super(AlgoAssetclass.class);
  }

  @Operation(summary = "Get the full algorithmic tranding tree for a strategy without the top level", description = "", tags = {
      RequestMappings.ALGOASSETCLASS })
  @GetMapping(value = "/{idAlgoAssetclassParent}", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<List<AlgoAssetclass>> getAlgoAssetclassByIdTenantAndIdAlgoAssetclassParent(
      @PathVariable final Integer idAlgoAssetclassParent) {
    final User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    return new ResponseEntity<>(
        algoAssetclassJpaRepository.findByIdTenantAndIdAlgoAssetclassParent(user.getIdTenant(), idAlgoAssetclassParent),
        HttpStatus.OK);
  }

  @Override
  protected UpdateCreateDeleteWithTenantJpaRepository<AlgoAssetclass> getUpdateCreateJpaRepository() {
    return algoAssetclassJpaRepository;
  }

}
