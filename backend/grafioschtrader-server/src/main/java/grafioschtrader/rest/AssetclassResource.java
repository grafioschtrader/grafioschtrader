package grafioschtrader.rest;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import java.util.EnumMap;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import grafioschtrader.dto.ValueKeyHtmlSelectOptions;
import grafioschtrader.entities.Assetclass;
import grafioschtrader.entities.User;
import grafioschtrader.repository.AssetclassJpaRepository;
import grafioschtrader.types.AssetclassType;
import grafioschtrader.types.SpecialInvestmentInstruments;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;


@RestController
@RequestMapping(RequestMappings.ASSETCLASS_MAP)
@Tag(name = Assetclass.TABNAME, description = "Controller for asset class")
public class AssetclassResource extends UpdateCreateDeleteAuditResource<Assetclass> {

  @Autowired
  private AssetclassJpaRepository assetclassJpaRepository;

  @Operation(summary = "Return of all asset classes unsorted.", description = "", tags = { RequestMappings.ALGOASSETCLASS })
  @GetMapping(produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<List<Assetclass>> getAllAssetclass() {
    return new ResponseEntity<>(assetclassJpaRepository.findAll(), HttpStatus.OK);
  }

  @Operation(summary = "Return of an asset class by its Id", description = "", tags = { RequestMappings.ALGOASSETCLASS })
  @GetMapping(value = "/{idAssetClass}", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<Assetclass> getAssetclass(@PathVariable final Integer idAssetClass) {
    return new ResponseEntity<>(assetclassJpaRepository.findById(idAssetClass).get(), HttpStatus.OK);
  }

  @Operation(summary = "Returns id of asset class and 1 (has a security) or 0 (no security) ", description = "", tags = {
      Assetclass.TABNAME })
  @GetMapping(value = "/hassecurity", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<List<Object[]>> assetclassesHasSecurity() {
    return new ResponseEntity<>(assetclassJpaRepository.assetclassesHasSecurity(), HttpStatus.OK);
  }

  @Operation(summary = "Return whether a particular asset class is referenced by a security.", description = "", tags = {
      Assetclass.TABNAME })
  @GetMapping(value = "/{idAssetClass}/hassecurity", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<Boolean> assetclassHasSecurity(@PathVariable final Integer idAssetClass) {
    return new ResponseEntity<>(assetclassJpaRepository.assetclassHasSecurity(idAssetClass) > 0, HttpStatus.OK);
  }

  @Operation(summary = "Return of the possible combination of asset class to financial instrument.", description = "", tags = {
      Assetclass.TABNAME })
  @GetMapping(value = "/possibleassetclassspezinstrument", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<EnumMap<AssetclassType, SpecialInvestmentInstruments[]>> getPossibleAssetclassInstrumentMap() {
    return new ResponseEntity<>(Assetclass.possibleInstrumentsMap, HttpStatus.OK);
  }

  @Operation(summary = "Return of all investable asset classes used in a specific watchlist. CFD is excluded.", description = "", tags = {
      Assetclass.TABNAME })
  @GetMapping(value = "/watchlist/{idWatchlist}", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<List<Assetclass>> getInvestableAssetclassesByWatchlist(
      @PathVariable final Integer idWatchlist) {
    final User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    return new ResponseEntity<>(
        assetclassJpaRepository.getInvestableAssetclassesByWatchlist(user.getIdTenant(), idWatchlist), HttpStatus.OK);
  }

  @Override
  protected UpdateCreateJpaRepository<Assetclass> getUpdateCreateJpaRepository() {
    return assetclassJpaRepository;
  }

  @Operation(summary = "Return a sub-asset class for a given language as a key value pair.", 
    description = "Return is intended for a drop-down list.", tags = {
      Assetclass.TABNAME })
  @GetMapping(value = "/subcategory", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<List<ValueKeyHtmlSelectOptions>> getSubcategoryForLanguage() {
    return new ResponseEntity<>(assetclassJpaRepository.getSubcategoryForLanguage(), HttpStatus.OK);
  }

  @GetMapping(value = "/algounused/{idAlgoAssetclassSecurity}", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<List<Assetclass>> getUnusedAssetclassForAlgo(
      @PathVariable final Integer idAlgoAssetclassSecurity) {
    final User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    return new ResponseEntity<>(
        assetclassJpaRepository.getUnusedAssetclassForAlgo(user.getIdTenant(), idAlgoAssetclassSecurity),
        HttpStatus.OK);
  }

}
