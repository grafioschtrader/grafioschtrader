package grafioschtrader.rest;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import grafiosch.rest.UpdateCreateDeleteAuditResource;
import grafiosch.rest.UpdateCreateJpaRepository;
import grafioschtrader.GlobalConstants;
import grafioschtrader.dto.TransactionCostEstimateRequest;
import grafioschtrader.dto.TransactionCostEstimateResult;
import grafioschtrader.entities.TradingPlatformPlan;
import grafioschtrader.repository.TradingPlatformPlanJpaRepository;
import grafioschtrader.service.TransactionCostEvalExEstimator;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping(RequestGTMappings.TRADINGPLATFORMPLAND_MAP)
@Tag(name = RequestGTMappings.TRADINGPLATFORMPLAND, description = "Controller for trading platform plan")
public class TradingPlatformPlanResource extends UpdateCreateDeleteAuditResource<TradingPlatformPlan> {

  @Autowired
  private TradingPlatformPlanJpaRepository tradingPlatformPlanJpaRepository;

  @Autowired
  private TransactionCostEvalExEstimator transactionCostEvalExEstimator;

  @GetMapping(produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<List<TradingPlatformPlan>> getAllTradingPlatform() {
    return new ResponseEntity<>(tradingPlatformPlanJpaRepository.findAll(), HttpStatus.OK);
  }

  @Operation(summary = "Estimate transaction cost using EvalEx fee model", description = """
      Evaluates the YAML-based fee model on the specified TradingPlatformPlan with the given trade parameters.
      Returns the estimated cost and the name of the matched rule.""")
  @PostMapping(value = "/estimatecost", produces = APPLICATION_JSON_VALUE, consumes = APPLICATION_JSON_VALUE)
  public ResponseEntity<TransactionCostEstimateResult> estimateCost(
      @RequestBody TransactionCostEstimateRequest request) {
    return new ResponseEntity<>(transactionCostEvalExEstimator.estimate(request), HttpStatus.OK);
  }

  @Operation(summary = "Validate YAML fee model", description = """
      Validates the given YAML string against the fee model JSON Schema and checks EvalEx syntax.
      Returns a list of validation errors, empty if valid.""")
  @PostMapping(value = "/validateyaml", produces = APPLICATION_JSON_VALUE, consumes = APPLICATION_JSON_VALUE)
  public ResponseEntity<List<String>> validateYaml(@RequestBody String yaml) {
    return new ResponseEntity<>(transactionCostEvalExEstimator.validate(yaml), HttpStatus.OK);
  }

  @Override
  protected UpdateCreateJpaRepository<TradingPlatformPlan> getUpdateCreateJpaRepository() {
    return tradingPlatformPlanJpaRepository;
  }

  @Override
  protected String getPrefixEntityLimit() {
    return GlobalConstants.GT_LIMIT_DAY;
  }

}
