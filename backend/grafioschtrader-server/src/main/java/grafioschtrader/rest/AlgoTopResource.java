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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import grafiosch.entities.User;
import grafiosch.rest.UpdateCreateDeleteWithTenantJpaRepository;
import grafioschtrader.GlobalConstants;
import grafioschtrader.algo.AlgoTopCreate;
import grafioschtrader.algo.AlgoTopCreateFromPortfolio;
import grafioschtrader.entities.AlgoTop;
import grafioschtrader.entities.Tenant;
import grafioschtrader.repository.AlgoTopJpaRepository;
import grafioschtrader.repository.TenantJpaRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping(RequestGTMappings.ALGOTOP_MAP)
@Tag(name = RequestGTMappings.ALGOTOP, description = "Controller for top level algorithmic trading strategy")
public class AlgoTopResource extends AlgoBaseResource<AlgoTop> {

  @Autowired
  private AlgoTopJpaRepository algoTopJpaRepository;

  @Autowired
  private TenantJpaRepository tenantJpaRepository;

  @Autowired
  private grafioschtrader.service.AlgoAlarmEvaluationService algoAlarmEvaluationService;

  public AlgoTopResource() {
    super(AlgoTop.class);
  }

  @Operation(summary = "Returns all top level allgorithmic tranding", description = "Can be used to shown in a tree", tags = {
      RequestGTMappings.ALGOTOP })
  @GetMapping(value = "/tenant", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<List<AlgoTop>> getAlgoTopByIdTenantOrderByName() {
    final User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    Integer mainIdTenant = user.getActualIdTenant();
    if (!mainIdTenant.equals(user.getIdTenant())) {
      // Simulation mode: return only the AlgoTop linked to this simulation tenant
      Tenant simTenant = tenantJpaRepository.findById(user.getIdTenant()).orElse(null);
      if (simTenant != null && simTenant.getIdAlgoTop() != null) {
        AlgoTop algoTop = algoTopJpaRepository.findById(simTenant.getIdAlgoTop()).orElse(null);
        if (algoTop != null && mainIdTenant.equals(algoTop.getIdTenant())) {
          return new ResponseEntity<>(List.of(algoTop), HttpStatus.OK);
        }
      }
      return new ResponseEntity<>(List.of(), HttpStatus.OK);
    }
    return new ResponseEntity<>(algoTopJpaRepository.findByIdTenantOrderByName(mainIdTenant), HttpStatus.OK);
  }

  @Operation(summary = "Returns top level allgorithmic tranding by specified ", description = "", tags = {
      RequestGTMappings.ALGOTOP })
  @GetMapping(value = "/{idAlgoAssetclassSecurity}", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<AlgoTop> getAlgoTopByIdAlgoAssetclassSecurity(
      @Parameter(description = "Id of top level algorithmic trading", required = true) @PathVariable final Integer idAlgoAssetclassSecurity) {
    final User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    return new ResponseEntity<>(
        algoTopJpaRepository.findByIdTenantAndIdAlgoAssetclassSecurity(user.getActualIdTenant(), idAlgoAssetclassSecurity),
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

  @Operation(summary = "Auto-generate AlgoTop hierarchy from portfolio holdings at reference date",
      description = "Creates AlgoTop with AlgoAssetclass and AlgoSecurity children based on actual holdings",
      tags = { RequestGTMappings.ALGOTOP })
  @PostMapping(value = "/createfromportfolio", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<AlgoTop> createFromPortfolio(
      @RequestBody AlgoTopCreateFromPortfolio dto) throws Exception {
    return createEntity(dto);
  }

  @Operation(summary = "Manually trigger alarm evaluation for current tenant", description = "Evaluates all indicator-based alerts for active AlgoTop configurations", tags = {
      RequestGTMappings.ALGOTOP })
  @PostMapping(value = "/evaluatealarms", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<Void> evaluateAlarms() {
    algoAlarmEvaluationService.evaluateIndicatorAlerts();
    return ResponseEntity.ok().build();
  }

  @Operation(summary = "Normalize child percentages to sum to 100%",
      description = "Recalculates percentages of direct children (AlgoAssetclass or AlgoSecurity) of the given parent so they sum to exactly 100.00",
      tags = { RequestGTMappings.ALGOTOP })
  @PutMapping(value = "/normalizepercentages/{idAlgoAssetclassSecurity}", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<Void> normalizePercentages(@PathVariable Integer idAlgoAssetclassSecurity) {
    final User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    algoTopJpaRepository.normalizeChildPercentages(idAlgoAssetclassSecurity, user.getActualIdTenant());
    return ResponseEntity.ok().build();
  }

  @Operation(summary = "Normalize all percentages in the entire AlgoTop tree",
      description = "Normalizes AlgoAssetclass children and all their AlgoSecurity children so each level sums to exactly 100.00",
      tags = { RequestGTMappings.ALGOTOP })
  @PutMapping(value = "/normalizeallpercentages/{idAlgoAssetclassSecurity}", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<Void> normalizeAllPercentages(@PathVariable Integer idAlgoAssetclassSecurity) {
    final User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    algoTopJpaRepository.normalizeAllPercentages(idAlgoAssetclassSecurity, user.getActualIdTenant());
    return ResponseEntity.ok().build();
  }

  @Override
  protected String getPrefixEntityLimit() {
    return GlobalConstants.GT_LIMIT_DAY;
  }

}
