package grafioschtrader.rest;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import grafioschtrader.algo.strategy.model.AlgoStrategyImplementations;
import grafioschtrader.algo.strategy.model.InputAndShowDefinitionStrategy;
import grafioschtrader.algo.strategy.model.StrategyHelper;
import grafioschtrader.entities.AlgoStrategy;
import grafioschtrader.repository.AlgoStrategyJpaRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping(RequestMappings.ALGOSTRATEGY_MAP)
@Tag(name = RequestMappings.ALGOSTRATEGY, description = "Controller for algorithmic trading strategy")
public class AlgoStrategyResource extends UpdateCreateDeleteWithTenantResource<AlgoStrategy> {

  @Autowired
  private AlgoStrategyJpaRepository algoStrategyJpaRepository;

  public AlgoStrategyResource() {
    super(AlgoStrategy.class);
  }

  @Operation(summary = """
  Return the field properties so that they can be used as an input form. 
  There is one definition each for the corresponding level like security, asset class etc.""", 
  description = "", tags = { RequestMappings.ALGOSTRATEGY })
  @GetMapping(value = "/form/{algoStrategyImplementations}", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<InputAndShowDefinitionStrategy> getFormDefinitionsByAlgoStrategy(
      @PathVariable final byte algoStrategyImplementations) {
    return new ResponseEntity<>(StrategyHelper.getFormDefinitionsByAlgoStrategyImpl(
        AlgoStrategyImplementations.getAlgoStrategyImplentaions(algoStrategyImplementations)), HttpStatus.OK);
  }

  @Operation(summary = "", description = "", tags = { RequestMappings.ALGOSTRATEGY })
  @GetMapping(value = "/unusedsrategies/{idAlgoAssetclassSecurity}", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<Set<AlgoStrategyImplementations>> getUnusedStrategiesForManualAdding(
      @PathVariable() final Integer idAlgoAssetclassSecurity) {
    return new ResponseEntity<>(algoStrategyJpaRepository.getUnusedStrategiesForManualAdding(idAlgoAssetclassSecurity),
        HttpStatus.OK);
  }

  @Override
  protected UpdateCreateDeleteWithTenantJpaRepository<AlgoStrategy> getUpdateCreateJpaRepository() {
    return algoStrategyJpaRepository;
  }
}
