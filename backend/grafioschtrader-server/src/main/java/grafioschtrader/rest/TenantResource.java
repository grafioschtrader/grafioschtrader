package grafioschtrader.rest;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import grafioschtrader.entities.Tenant;
import grafioschtrader.entities.User;
import grafioschtrader.repository.TenantJpaRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;

@RestController
@RequestMapping(RequestMappings.TENANT_MAP)
@Tag(name = Tenant.TABNAME, description = "Controller for tenant")
public class TenantResource extends UpdateCreateResource<Tenant> {

  private final Logger log = LoggerFactory.getLogger(this.getClass());

  @Autowired
  private TenantJpaRepository tenantJpaRepository;

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
      Tenant.TABNAME })
  @PatchMapping("/watchlistforperformance/{idWatchlist}")
  public ResponseEntity<Tenant> setWatchlistForPerformance(
      @Parameter(description = "ID of watchlist", required = true) @PathVariable Integer idWatchlist) {
    return new ResponseEntity<>(tenantJpaRepository.setWatchlistForPerformance(idWatchlist), HttpStatus.OK);
  }

  @Operation(summary = "Change tenants currency and also in its each protfolio", description = "", tags = {
      Tenant.TABNAME })
  @PatchMapping("{currency}")
  public ResponseEntity<Tenant> changeCurrencyTenantAndPortfolios(
      @Parameter(description = "New currency", required = true) @PathVariable String currency) {
    return new ResponseEntity<>(tenantJpaRepository.changeCurrencyTenantAndPortfolios(currency), HttpStatus.OK);
  }

  @Operation(summary = "Export the data of a client with it private ond public data", description = "The created zip file will cotains two files one with ddl and the 2nd with dml statements", tags = {
      Tenant.TABNAME })
  @GetMapping(value = "/exportpersonaldataaszip", produces = "application/zip")
  public void getExportPersonalDataAsZip(HttpServletResponse response) throws Exception {
    tenantJpaRepository.getExportPersonalDataAsZip(response);
  }

  @Operation(summary = "Delete the private data the main tenant of the user. It als removes the user from this application", description = "", tags = {
      Tenant.TABNAME })
  @DeleteMapping(produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<Void> deleteMyDataAndUserAccount() throws Exception {
    log.debug("Delete all data of a user");
    tenantJpaRepository.deleteMyDataAndUserAccount();
    return ResponseEntity.noContent().build();
  }

}
