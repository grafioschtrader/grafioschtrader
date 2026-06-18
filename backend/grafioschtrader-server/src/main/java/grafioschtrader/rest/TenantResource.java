package grafioschtrader.rest;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import java.util.List;

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
import grafiosch.types.TenantAccessLevel;
import grafioschtrader.GlobalConstants;
import grafioschtrader.algo.SimulationTenantCreateDTO;
import grafioschtrader.algo.SimulationTenantInfo;
import grafioschtrader.dto.TaxStatementExportRequest;
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
  @PatchMapping("/currency/{currency}")
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

  @Operation(summary = "Persist the tax export dialog settings for the current tenant", tags = {
      TenantBase.TABNAME })
  @PatchMapping(value = "/taxexportsettings", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<Void> saveTaxExportSettings(@RequestBody TaxStatementExportRequest taxExportSettings) {
    final User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    Tenant tenant = tenantJpaRepository.getReferenceById(user.getIdTenant());
    tenant.setTaxExportSettings(taxExportSettings);
    tenantJpaRepository.save(tenant);
    return new ResponseEntity<>(HttpStatus.OK);
  }

  @Override
  protected TenantBaseCustom getTenantRepository() {
    return tenantJpaRepository;
  }

  @Override
  protected String getPrefixEntityLimit() {
    return GlobalConstants.GT_LIMIT_DAY;
  }

  /**
   * Allows switching into a simulation tenant that is a child of the user's home tenant (in addition to the generic
   * home/grant rules handled by the base class).
   */
  @Override
  protected TenantAccessLevel resolveAppSpecificSwitchTargetLevel(User user, Integer homeIdTenant, Integer idTarget) {
    Tenant target = tenantJpaRepository.findById(idTarget).orElse(null);
    if (target != null && target.getTenantKindType() == TenantKindType.SIMULATION_COPY
        && homeIdTenant.equals(target.getIdParentTenant())) {
      return TenantAccessLevel.MANAGE;
    }
    return null;
  }

  @Override
  protected Integer createManagedClientTenant(String tenantName, User advisor) {
    Tenant advisorTenant = tenantJpaRepository.findById(advisor.getActualIdTenant())
        .orElseThrow(() -> new IllegalStateException("Advisor tenant not found"));
    Tenant tenant = new Tenant(tenantName, advisorTenant.getCurrency(), advisor.getIdUser(), TenantKindType.MAIN, false);
    return tenantJpaRepository.save(tenant).getIdTenant();
  }

  @Override
  protected String getManagedTenantName(Integer idTenant) {
    return tenantJpaRepository.findById(idTenant).map(Tenant::getTenantName).orElse(null);
  }

}
