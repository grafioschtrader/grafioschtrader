package grafioschtrader.rest;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import grafiosch.entities.TenantBase;
import grafiosch.entities.User;
import grafiosch.repository.TenantBaseCustom;
import grafiosch.rest.TenantBaseResource;
import grafiosch.rest.UpdateCreateJpaRepository;
import grafiosch.security.JwtTokenHandler;
import grafioschtrader.GlobalConstants;
import grafioschtrader.algo.SimulationTenantCreateDTO;
import grafioschtrader.algo.SimulationTenantInfo;
import grafioschtrader.entities.Tenant;
import grafioschtrader.repository.SimulationTenantService;
import grafioschtrader.repository.TenantJpaRepository;
import grafioschtrader.types.TenantKindType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping(RequestGTMappings.TENANT_MAP)
@Tag(name = TenantBase.TABNAME, description = "Controller for tenant")
public class TenantResource extends TenantBaseResource<Tenant> {

  @Autowired
  private TenantJpaRepository tenantJpaRepository;

  @Autowired
  private SimulationTenantService simulationTenantService;

  @Autowired
  private JwtTokenHandler jwtTokenHandler;

  @GetMapping(produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<Tenant> getTenantAndPortfolio() {
    final User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    return new ResponseEntity<>(tenantJpaRepository.getReferenceById(user.getIdTenant()), HttpStatus.OK);
  }

  @Override
  protected UpdateCreateJpaRepository<Tenant> getUpdateCreateJpaRepository() {
    return tenantJpaRepository;
  }

  @Operation(summary = "Change tenants currency and also in its each protfolio", description = "", tags = {
      TenantBase.TABNAME })
  @PatchMapping("/watchlistforperformance/{idWatchlist}")
  public ResponseEntity<Tenant> setWatchlistForPerformance(
      @Parameter(description = "ID of watchlist", required = true) @PathVariable Integer idWatchlist) {
    return new ResponseEntity<>(tenantJpaRepository.setWatchlistForPerformance(idWatchlist), HttpStatus.OK);
  }

  @Operation(summary = "Change tenants currency and also in its each protfolio", description = "", tags = {
      TenantBase.TABNAME })
  @PatchMapping("{currency}")
  public ResponseEntity<Tenant> changeCurrencyTenantAndPortfolios(
      @Parameter(description = "New currency", required = true) @PathVariable String currency) {
    return new ResponseEntity<>(tenantJpaRepository.changeCurrencyTenantAndPortfolios(currency), HttpStatus.OK);
  }

  @Operation(summary = "Create a simulation tenant from an AlgoTop strategy", tags = { TenantBase.TABNAME })
  @PostMapping(value = "/simulation", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<Tenant> createSimulationTenant(@Valid @RequestBody SimulationTenantCreateDTO dto) {
    return new ResponseEntity<>(simulationTenantService.createSimulationTenant(dto), HttpStatus.CREATED);
  }

  @Operation(summary = "List all simulation tenants for the current user's main tenant", tags = { TenantBase.TABNAME })
  @GetMapping(value = "/simulations", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<List<SimulationTenantInfo>> getSimulationTenants() {
    return new ResponseEntity<>(simulationTenantService.getSimulationTenants(), HttpStatus.OK);
  }

  @Operation(summary = "Delete a simulation tenant and all its data", tags = { TenantBase.TABNAME })
  @DeleteMapping(value = "/simulation/{idTenant}")
  public ResponseEntity<Void> deleteSimulationTenant(
      @Parameter(description = "ID of the simulation tenant", required = true) @PathVariable Integer idTenant) {
    simulationTenantService.deleteSimulationTenant(idTenant);
    return new ResponseEntity<>(HttpStatus.NO_CONTENT);
  }

  @Operation(summary = "Switch to a different tenant (main or simulation) and receive a new JWT", tags = {
      TenantBase.TABNAME })
  @PostMapping(value = "/switchto/{idTargetTenant}", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<Map<String, String>> switchTenant(
      @Parameter(description = "ID of the target tenant", required = true) @PathVariable Integer idTargetTenant) {
    final User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    // Use actualIdTenant which preserves the DB main tenant even when user is in a simulation
    Integer mainIdTenant = user.getActualIdTenant();

    // Validate: target must be the user's main tenant or a simulation child of it
    if (!mainIdTenant.equals(idTargetTenant)) {
      Tenant target = tenantJpaRepository.findById(idTargetTenant).orElse(null);
      if (target == null || target.getTenantKindType() != TenantKindType.SIMULATION_COPY
          || !mainIdTenant.equals(target.getIdParentTenant())) {
        return new ResponseEntity<>(HttpStatus.FORBIDDEN);
      }
    }

    String token = jwtTokenHandler.createTokenForUser(user, 120, idTargetTenant);
    return new ResponseEntity<>(Map.of("token", token), HttpStatus.OK);
  }

  @Override
  protected TenantBaseCustom getTenantRepository() {
    return tenantJpaRepository;
  }

  @Override
  protected String getPrefixEntityLimit() {
    return GlobalConstants.GT_LIMIT_DAY;
  }

}
