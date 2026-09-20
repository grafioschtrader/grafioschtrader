package grafioschtrader.rest;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import java.time.LocalDate;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.format.annotation.DateTimeFormat.ISO;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import grafiosch.entities.TenantBase;
import grafiosch.entities.User;
import grafiosch.repository.TenantBaseCustom;
import grafiosch.rest.TenantBaseResource;
import grafiosch.rest.UpdateCreateJpaRepository;
import grafioschtrader.algo.SimulationDateBounds;
import grafioschtrader.algo.SimulationPreviewDto;
import grafioschtrader.algo.SimulationRunRequestDTO;
import grafioschtrader.algo.SimulationTenantCreateDTO;
import grafioschtrader.algo.SimulationTenantInfo;
import grafioschtrader.dto.TaxStatementExportRequest;
import grafioschtrader.entities.AlgoEventLog;
import grafioschtrader.entities.AlgoSimulationResult;
import grafioschtrader.entities.Tenant;
import grafioschtrader.repository.SimulationTenantService;
import grafioschtrader.repository.TenantJpaRepository;
import grafioschtrader.service.AlgoHistoricalReplayService;
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
  private AlgoHistoricalReplayService replayService;

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
  public ResponseEntity<Tenant> createSimulationTenant(@Valid @RequestBody SimulationTenantCreateDTO dto)
      throws Exception {
    return new ResponseEntity<>(simulationTenantService.createSimulationTenant(dto), HttpStatus.CREATED);
  }

  @PostMapping(value = "/simulation/preview", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<SimulationPreviewDto> previewSimulation(@Valid @RequestBody SimulationTenantCreateDTO dto) {
    return ResponseEntity.ok(simulationTenantService.previewSimulation(dto));
  }

  @Operation(summary = "Dates that limit what an environment of this hierarchy can evaluate", description = "Reports the first day on which every instrument of the linked watchlist has price data, and the first "
      + "transaction of the portfolio. Both are informational: an earlier opening date remains allowed, because "
      + "opening with cash alone before any price data is a legitimate starting point.", tags = { TenantBase.TABNAME })
  @GetMapping(value = "/simulation/bounds/{idAlgoTop}", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<SimulationDateBounds> getSimulationDateBounds(@PathVariable final Integer idAlgoTop,
      @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) final LocalDate openingDate) {
    return new ResponseEntity<>(simulationTenantService.getSimulationDateBounds(idAlgoTop, openingDate), HttpStatus.OK);
  }

  @Operation(summary = "List all simulation tenants for the current user's main tenant", tags = { TenantBase.TABNAME })
  @GetMapping(value = "/simulations", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<List<SimulationTenantInfo>> getSimulationTenants() {
    return new ResponseEntity<>(simulationTenantService.getSimulationTenants(), HttpStatus.OK);
  }

  @Operation(summary = "Start a historical replay of a simulation environment", description = "Replays the environment from the day after its immutable opening date to the requested end "
      + "date, using only the observations available on each of those days. The run executes in the background; "
      + "poll the run endpoint for its progress and its metrics.", tags = { TenantBase.TABNAME })
  @PostMapping(value = "/simulation/{idTenant}/run", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<AlgoSimulationResult> startSimulationRun(
      @Parameter(description = "ID of the simulation tenant", required = true) @PathVariable Integer idTenant,
      @Valid @RequestBody SimulationRunRequestDTO request) {
    return new ResponseEntity<>(replayService.submit(idTenant, request), HttpStatus.ACCEPTED);
  }

  @Operation(summary = "Status, progress and metrics of the historical replay of a simulation environment", tags = {
      TenantBase.TABNAME })
  @GetMapping(value = "/simulation/{idTenant}/run", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<AlgoSimulationResult> getSimulationRun(
      @Parameter(description = "ID of the simulation tenant", required = true) @PathVariable Integer idTenant) {
    return replayService.status(idTenant).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.noContent().build());
  }

  @Operation(summary = "Audit trail of the historical replay, newest day first", tags = { TenantBase.TABNAME })
  @GetMapping(value = "/simulation/{idTenant}/run/events", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<Page<AlgoEventLog>> getSimulationRunEvents(
      @Parameter(description = "ID of the simulation tenant", required = true) @PathVariable Integer idTenant,
      @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size) {
    return ResponseEntity.ok(replayService.events(idTenant, page, Math.min(Math.max(size, 1), 200)));
  }

  @Operation(summary = "Ask the running historical replay to stop after the day it is evaluating", tags = {
      TenantBase.TABNAME })
  @PostMapping(value = "/simulation/{idTenant}/run/cancel")
  public ResponseEntity<Void> cancelSimulationRun(
      @Parameter(description = "ID of the simulation tenant", required = true) @PathVariable Integer idTenant) {
    replayService.cancel(idTenant);
    return new ResponseEntity<>(HttpStatus.NO_CONTENT);
  }

  @Operation(summary = "Delete a simulation tenant and all its data", tags = { TenantBase.TABNAME })
  @DeleteMapping(value = "/simulation/{idTenant}")
  public ResponseEntity<Void> deleteSimulationTenant(
      @Parameter(description = "ID of the simulation tenant", required = true) @PathVariable Integer idTenant) {
    simulationTenantService.deleteSimulationTenant(idTenant);
    return new ResponseEntity<>(HttpStatus.NO_CONTENT);
  }

  @Operation(summary = "Persist the tax export dialog settings for the current tenant", tags = { TenantBase.TABNAME })
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
  protected Integer createManagedClientTenant(String tenantName, User advisor) {
    Tenant advisorTenant = tenantJpaRepository.findById(advisor.getActualIdTenant())
        .orElseThrow(() -> new IllegalStateException("Advisor tenant not found"));
    Tenant tenant = new Tenant(tenantName, advisorTenant.getCurrency(), advisor.getIdUser(), TenantKindType.MAIN,
        false);
    return tenantJpaRepository.save(tenant).getIdTenant();
  }

  @Override
  protected String getManagedTenantName(Integer idTenant) {
    return tenantJpaRepository.findById(idTenant).map(Tenant::getTenantName).orElse(null);
  }

}
