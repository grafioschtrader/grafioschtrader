package grafioschtrader.rest;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import grafiosch.entities.User;
import grafiosch.rest.UpdateCreateDeleteWithTenantJpaRepository;
import grafioschtrader.entities.AlgoAssetclass;
import grafioschtrader.reportviews.securitycurrency.SecuritycurrencyLists;
import grafioschtrader.repository.AlgoAssetclassJpaRepository;
import grafioschtrader.search.SecuritycurrencySearch;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping(RequestGTMappings.ALGOASSETCLASS_MAP)
@Tag(name = RequestGTMappings.ALGOASSETCLASS, description = "Controller for top level algorithmic trading assetclass")
public class AlgoAssetclassResource extends AlgoBaseResource<AlgoAssetclass> {

  @Autowired
  AlgoAssetclassJpaRepository algoAssetclassJpaRepository;

  public AlgoAssetclassResource() {
    super(AlgoAssetclass.class);
  }

  @Operation(summary = "Get the full algorithmic tranding tree for a strategy without the top level", description = "", tags = {
      RequestGTMappings.ALGOASSETCLASS })
  @GetMapping(value = "/{idAlgoAssetclassParent}", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<List<AlgoAssetclass>> getAlgoAssetclassByIdTenantAndIdAlgoAssetclassParent(
      @PathVariable final Integer idAlgoAssetclassParent) {
    final User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    return new ResponseEntity<>(algoAssetclassJpaRepository
        .findByIdTenantAndIdAlgoAssetclassParent(user.getActualIdTenant(), idAlgoAssetclassParent), HttpStatus.OK);
  }

  @Operation(summary = "Searches instruments that may be added to a custom category", description = """
      Independent of the watchlist of the hierarchy. Instruments already in the category, instruments a simulation
      cannot trade (CFD, Forex, leverage factor other than 1) and instruments that ended before the hierarchy opens
      are left out.""", tags = { RequestGTMappings.ALGOASSETCLASS })
  @GetMapping(value = "/{idAlgoAssetclassSecurity}/search", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<SecuritycurrencyLists> searchByCriteria(
      @Parameter(description = "Id of the custom category", required = true) @PathVariable final Integer idAlgoAssetclassSecurity,
      @Parameter(description = "Search criteria", required = true) final SecuritycurrencySearch securitycurrencySearch) {
    return new ResponseEntity<>(
        algoAssetclassJpaRepository.searchByCriteria(idAlgoAssetclassSecurity, securitycurrencySearch), HttpStatus.OK);
  }

  @Operation(summary = "Adds one or more securities as instrument nodes to a custom category", description = "", tags = {
      RequestGTMappings.ALGOASSETCLASS })
  @PutMapping(value = "/{idAlgoAssetclassSecurity}/addSecurity", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<AlgoAssetclass> addSecuritiesToCustomCategory(
      @PathVariable final Integer idAlgoAssetclassSecurity,
      @RequestBody final SecuritycurrencyLists securitycurrencyLists) throws Exception {
    return ResponseEntity.ok().body(
        algoAssetclassJpaRepository.addSecuritiesToCustomCategory(idAlgoAssetclassSecurity, securitycurrencyLists));
  }

  @Override
  protected UpdateCreateDeleteWithTenantJpaRepository<AlgoAssetclass> getUpdateCreateJpaRepository() {
    return algoAssetclassJpaRepository;
  }

}
