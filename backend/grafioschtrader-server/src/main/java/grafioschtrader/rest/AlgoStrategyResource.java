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

import grafiosch.rest.UpdateCreateDeleteWithTenantJpaRepository;
import grafioschtrader.algo.strategy.model.AlgoLevelType;
import grafioschtrader.algo.strategy.model.AlgoStrategyImplementationType;
import grafioschtrader.algo.strategy.model.InputAndShowDefinitionStrategy;
import grafioschtrader.algo.strategy.model.StrategyHelper;
import grafioschtrader.entities.AlgoStrategy;
import grafioschtrader.repository.AlgoStrategyJpaRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping(RequestGTMappings.ALGOSTRATEGY_MAP)
@Tag(name = RequestGTMappings.ALGOSTRATEGY, description = "Controller for algorithmic trading strategy")
public class AlgoStrategyResource extends AlgoBaseResource<AlgoStrategy> {

  @Autowired
  private AlgoStrategyJpaRepository algoStrategyJpaRepository;

  @Autowired
  private grafioschtrader.service.AlgoMonitoringService monitoring;

  @io.swagger.v3.oas.annotations.media.Schema(description = "Live alert preference; never changes replay behavior")
  public record AlertEnabledDto(@jakarta.validation.constraints.NotNull Boolean alertEnabled) {
  }

  @Operation(summary = "Change live alerts for a strategy in the assigned monitoring hierarchy")
  @org.springframework.web.bind.annotation.PatchMapping(value = "/{id}/alert-enabled", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<AlgoStrategy> setAlertEnabled(@PathVariable Integer id,
      @jakarta.validation.Valid @org.springframework.web.bind.annotation.RequestBody AlertEnabledDto request) {
    return ResponseEntity.ok(monitoring.setAlertEnabled(id, request.alertEnabled()));
  }

  public AlgoStrategyResource() {
    super(AlgoStrategy.class);
  }

  @Operation(summary = """
      Return the field properties so that they can be used as an input form.
      There is one definition each for the corresponding level like security, asset class etc.""", description = "", tags = {
      RequestGTMappings.ALGOSTRATEGY })
  @GetMapping(value = "/form/{algoStrategyImplementations}", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<InputAndShowDefinitionStrategy> getFormDefinitionsByAlgoStrategy(
      @PathVariable final byte algoStrategyImplementations) {
    return new ResponseEntity<>(StrategyHelper.getFormDefinitionsByAlgoStrategyImpl(
        AlgoStrategyImplementationType.getAlgoStrategyImplentaionType(algoStrategyImplementations)), HttpStatus.OK);
  }

  // DOTO Maybe not used
  @Operation(summary = "Return of the possible strategies that can be applied to a non-existent level of an investment hierarchy.", description = "", tags = {
      RequestGTMappings.ALGOSTRATEGY })
  @GetMapping(value = "/level/{algoLevelType}", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<Set<AlgoStrategyImplementationType>> getStrategiesForLevel(
      @PathVariable() final AlgoLevelType algoLevelType) {
    return new ResponseEntity<>(algoStrategyJpaRepository.getStrategiesForLevel(algoLevelType), HttpStatus.OK);
  }

  @Operation(summary = "Return of the possible strategies that can be applied to an existing level of this investment hierarchy.", description = "", tags = {
      RequestGTMappings.ALGOSTRATEGY })
  @GetMapping(value = "/unusedsrategies/{idAlgoAssetclassSecurity}", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<Set<AlgoStrategyImplementationType>> getUnusedStrategiesForManualAdding(
      @PathVariable() final Integer idAlgoAssetclassSecurity) {
    return new ResponseEntity<>(algoStrategyJpaRepository.getUnusedStrategiesForManualAdding(idAlgoAssetclassSecurity),
        HttpStatus.OK);
  }

  @Override
  protected UpdateCreateDeleteWithTenantJpaRepository<AlgoStrategy> getUpdateCreateJpaRepository() {
    return algoStrategyJpaRepository;
  }

}
