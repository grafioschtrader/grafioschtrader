package grafioschtrader.rest;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import grafiosch.entities.TenantBase;
import grafiosch.entities.User;
import grafiosch.repository.TenantBaseCustom;
import grafiosch.rest.TenantBaseResource;
import grafiosch.rest.UpdateCreateJpaRepository;
import grafioschtrader.entities.Tenant;
import grafioschtrader.repository.TenantJpaRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping(RequestGTMappings.TENANT_MAP)
@Tag(name = TenantBase.TABNAME, description = "Controller for tenant")
public class TenantResource extends TenantBaseResource<Tenant> {

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

  @Override
  protected TenantBaseCustom getTenantRepository() {
    return tenantJpaRepository;
  }

}
