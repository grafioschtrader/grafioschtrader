package grafiosch.rest;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import grafiosch.gtnet.IExchangeKindType;
import grafiosch.gtnet.model.GTNetExchangeLogTreeDTO;
import grafiosch.repository.GTNetExchangeLogJpaRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * REST resource for GTNet exchange log statistics.
 */
@RestController
@RequestMapping(RequestMappings.GTNETEXCHANGELOG_MAP)
@Tag(name = "GTNetExchangeLog", description = "GTNet exchange statistics and monitoring")
public class GTNetExchangeLogResource {

  @Autowired
  private GTNetExchangeLogJpaRepository gtNetExchangeLogJpaRepository;

  /**
   * Returns the hierarchical exchange log tree for a specific GTNet and entity kind.
   *
   * @param idGtNet the GTNet identifier
   * @param entityKind the entity kind (e.g., LAST_PRICE, HISTORICAL_PRICES, SECURITY_METADATA)
   * @return tree structure with supplier and consumer statistics
   */
  @GetMapping(value = "/tree/{idGtNet}", produces = APPLICATION_JSON_VALUE)
  @Operation(summary = "Get exchange log tree for a specific GTNet")
  public ResponseEntity<GTNetExchangeLogTreeDTO> getExchangeLogTree(
      @PathVariable Integer idGtNet,
      @RequestParam IExchangeKindType entityKind) {
    GTNetExchangeLogTreeDTO tree = gtNetExchangeLogJpaRepository.getExchangeLogTree(idGtNet, entityKind);
    if (tree == null) {
      return ResponseEntity.notFound().build();
    }
    return ResponseEntity.ok(tree);
  }

  /**
   * Returns exchange log trees for all GTNets that have log data for the specified entity kind.
   *
   * @param entityKind the entity kind (e.g., LAST_PRICE, HISTORICAL_PRICES, SECURITY_METADATA)
   * @return list of tree structures, one per GTNet with communication enabled for this entity kind
   */
  @GetMapping(value = "/trees", produces = APPLICATION_JSON_VALUE)
  @Operation(summary = "Get exchange log trees for all GTNets")
  public ResponseEntity<List<GTNetExchangeLogTreeDTO>> getAllExchangeLogTrees(
      @RequestParam IExchangeKindType entityKind) {
    return ResponseEntity.ok(gtNetExchangeLogJpaRepository.getAllExchangeLogTrees(entityKind));
  }
}
