package grafioschtrader.rest;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import grafiosch.rest.UpdateCreateDeleteAuditResource;
import grafiosch.rest.UpdateCreateJpaRepository;
import grafioschtrader.GlobalConstants;
import grafioschtrader.dto.RiskFreeInstrumentOption;
import grafioschtrader.entities.RiskFreeRateMapping;
import grafioschtrader.repository.RiskFreeRateMappingJpaRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * REST controller for the risk-free-rate currency-to-security mapping. Inherits standard CRUD endpoints from
 * {@link UpdateCreateDeleteAuditResource} (POST /, PUT /, DELETE /{id}) plus a list endpoint and two helpers that
 * populate the editable-table dropdowns in the admin UI.
 *
 * <p>
 * Access: every authenticated user can read. Create is open to any role. Update/delete is enforced by the standard
 * ownership rule of {@link grafiosch.common.UserAccessHelper#hasRightsOrPrivilegesForEditingOrDelete} — i.e. row
 * owner OR holder of ROLE_ADMIN / ROLE_ALL_EDIT. ROLE_LIMIT_EDIT users are capped at 2 CUD operations per day on this
 * entity (registered via {@code GLOB_KEY_LIMIT_DAY_RISKFREERATEMAPPING} in {@code GlobalParamKeyDefault}).
 *
 * <p>
 * Cross-field validation (the posted {@code currency} must match the ISO currency of the selected security) is
 * enforced in {@link grafioschtrader.repository.RiskFreeRateMappingJpaRepositoryImpl#saveOnlyAttributes}, not here,
 * so the rule cannot be bypassed by non-REST entry points.
 */
@RestController
@RequestMapping(RequestGTMappings.RISKFREERATEMAPPING_MAP)
@Tag(name = RequestGTMappings.RISKFREERATEMAPPING, description = "Controller for the risk-free rate currency mapping")
public class RiskFreeRateMappingResource extends UpdateCreateDeleteAuditResource<RiskFreeRateMapping> {

  @Autowired
  private RiskFreeRateMappingJpaRepository riskFreeRateMappingJpaRepository;

  @Operation(summary = "List all risk-free rate mappings",
      description = "Returns every (currency, security) mapping row. Visible to any authenticated user.",
      tags = { RequestGTMappings.RISKFREERATEMAPPING })
  @GetMapping(produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<List<RiskFreeRateMapping>> getAll() {
    return new ResponseEntity<>(riskFreeRateMappingJpaRepository.findAll(), HttpStatus.OK);
  }

  @Operation(summary = "List all risk-free instrument candidates",
      description = """
          Returns all Security rows in the seeded 'Risk-free rate' assetclass, including those already linked to a
          currency in risk_free_rate_mapping. Each option carries the securitycurrency id, display name, and FRED
          series id so the editable-table dropdown can populate two columns from one call. Per-row 'already-used'
          filtering is applied client-side so the row being edited still sees its own current selection.""",
      tags = { RequestGTMappings.RISKFREERATEMAPPING })
  @GetMapping(value = "/instruments", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<List<RiskFreeInstrumentOption>> getAllInstruments() {
    return new ResponseEntity<>(riskFreeRateMappingJpaRepository.findAllRiskFreeInstruments(), HttpStatus.OK);
  }

  @Override
  protected UpdateCreateJpaRepository<RiskFreeRateMapping> getUpdateCreateJpaRepository() {
    return riskFreeRateMappingJpaRepository;
  }

  @Override
  protected String getPrefixEntityLimit() {
    return GlobalConstants.GT_LIMIT_DAY;
  }
}
