package grafioschtrader.rest;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import grafioschtrader.dto.CorrelationLimits;
import grafioschtrader.dto.CorrelationResult;
import grafioschtrader.dto.CorrelationRollingResult;
import grafioschtrader.dto.TenantLimit;
import grafioschtrader.entities.CorrelationSet;
import grafioschtrader.entities.User;
import grafioschtrader.reportviews.securitycurrency.SecuritycurrencyLists;
import grafioschtrader.repository.CorrelationSetJpaRepository;
import grafioschtrader.search.SecuritycurrencySearch;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping(RequestMappings.CORRELATION_SET_MAP)
@Tag(name = RequestMappings.CORRELATION_SET, description = "Controller for correlation set")
public class CorrelationSetResource extends UpdateCreateDeleteWithTenantResource<CorrelationSet> {

  @Autowired
  private CorrelationSetJpaRepository correlationSetJpaRepository;

  public CorrelationSetResource() {
    super(CorrelationSet.class);
  }

  @Operation(summary = "Return all correlation set for this tenant", description = "", tags = {
      RequestMappings.CORRELATION_SET })
  @GetMapping(value = "/tenant", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<List<CorrelationSet>> getCorrelationSetByTenant() {
    var user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    return new ResponseEntity<>(correlationSetJpaRepository.findByIdTenantOrderByName(user.getIdTenant()),
        HttpStatus.OK);
  }

  @Operation(summary = "Return the calculated results for a correlation set", description = "", tags = {
      RequestMappings.CORRELATION_SET })
  @GetMapping(value = "/calculation/{idCorrelationSet}", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<CorrelationResult> getCalculationByCorrelationSet(
      @PathVariable final Integer idCorrelationSet) {
    return new ResponseEntity<>(correlationSetJpaRepository.getCalculationByCorrelationSet(idCorrelationSet),
        HttpStatus.OK);
  }

  @Operation(summary = "Add one or more instruments to the correlation set", description = "", tags = {
      RequestMappings.CORRELATION_SET })
  @PutMapping(value = "{idCorrelationSet}/addSecuritycurrency", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<CorrelationSet> addSecuritycurrenciesToWatchlist(@PathVariable final Integer idCorrelationSet,
      @RequestBody final SecuritycurrencyLists securitycurrencyLists) {
    return ResponseEntity.ok().body(
        correlationSetJpaRepository.addSecuritycurrenciesToCorrelationSet(idCorrelationSet, securitycurrencyLists));
  }

  @Operation(summary = "Return the limits for the number of correlation sets", description = "", tags = {
      RequestMappings.CORRELATION_SET })
  @GetMapping(value = "limit", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<CorrelationLimits> getCorrelationSetLimit() {
    return new ResponseEntity<>(correlationSetJpaRepository.getCorrelationSetLimit(), HttpStatus.OK);
  }

  @Operation(summary = "Searches instruments which are not in the specified correlation set by a s search criteria", description = "", tags = {
      RequestMappings.CORRELATION_SET })
  @GetMapping(value = "/{idCorrelationSet}/search", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<SecuritycurrencyLists> searchByCriteria(
      @Parameter(description = "Id of correlation set", required = true) @PathVariable final Integer idCorrelationSet,
      @Parameter(description = "Search criteria", required = true) final SecuritycurrencySearch securitycurrencySearch) {
    return new ResponseEntity<>(correlationSetJpaRepository.searchByCriteria(idCorrelationSet, securitycurrencySearch),
        HttpStatus.OK);
  }

  @Operation(summary = "Return the limits for instruments on a correlation sets", description = "", tags = {
      RequestMappings.CORRELATION_SET })
  @GetMapping(value = "limit/{idCorrelationSet}", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<TenantLimit> getCorrelationSetInstrumentLimit(@PathVariable final Integer idCorrelationSet) {
    return new ResponseEntity<>(correlationSetJpaRepository.getCorrelationSetInstrumentLimit(idCorrelationSet),
        HttpStatus.OK);
  }

  @Operation(summary = "Return the rolling correlations for spezified list security pairs and its correlation set", description = "", tags = {
      RequestMappings.CORRELATION_SET })
  @GetMapping(value = "corrrolling/{idCorrelationSet}", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<List<CorrelationRollingResult>> getRollingCorrelations(
      @PathVariable final Integer idCorrelationSet, @RequestParam() final String securityIdsPairs) {
    String[] ids = securityIdsPairs.split(",");
    var securityIdsPairs2 = new Integer[ids.length / 2][2];
    for (int i = 0; i < ids.length; i++) {
      securityIdsPairs2[i / 2][i % 2] = Integer.valueOf(ids[i]);
    }
    return new ResponseEntity<>(correlationSetJpaRepository.getRollingCorrelations(idCorrelationSet, securityIdsPairs2),
        HttpStatus.OK);
  }

  @Operation(summary = "Remove an instrument from specified correlation set", description = "", tags = {
      RequestMappings.CORRELATION_SET })
  @DeleteMapping(value = "{idCorrelationSet}/removeinstrument/{idSecuritycurrency}", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<CorrelationSet> removeCurrencypairFromWatchlist(@PathVariable final Integer idCorrelationSet,
      @PathVariable final Integer idSecuritycurrency) {
    return ResponseEntity.ok()
        .body(correlationSetJpaRepository.removeInstrumentFromCorrelationSet(idCorrelationSet, idSecuritycurrency));
  }

  @Override
  protected UpdateCreateDeleteWithTenantJpaRepository<CorrelationSet> getUpdateCreateJpaRepository() {
    return correlationSetJpaRepository;
  }

}
