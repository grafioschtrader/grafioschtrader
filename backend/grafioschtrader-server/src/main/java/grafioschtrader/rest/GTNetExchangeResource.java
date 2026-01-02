package grafioschtrader.rest;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import grafiosch.entities.TaskDataChange;
import grafiosch.repository.TaskDataChangeJpaRepository;
import grafiosch.types.TaskDataExecPriority;
import grafioschtrader.dto.GTSecuritiyCurrencyExchange;
import grafioschtrader.entities.Currencypair;
import grafioschtrader.entities.GTNetExchange;
import grafioschtrader.entities.Security;
import grafioschtrader.gtnet.model.GTNetSupplierWithDetails;
import grafioschtrader.repository.GTNetExchangeJpaRepository;
import grafioschtrader.types.TaskTypeExtended;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * REST controller for managing GTNetExchange configurations.
 *
 * Provides endpoints for administrators to configure which securities and currency pairs
 * should receive or send price data (intraday and historical) via GTNet.
 */
@RestController
@RequestMapping(RequestGTMappings.GTNETEXCHANGE_MAP)
@Tag(name = RequestGTMappings.GTNETEXCHANGE, description = "Controller for GTNet exchange configuration")
public class GTNetExchangeResource {

  @Autowired
  private GTNetExchangeJpaRepository gtNetExchangeJpaRepository;

  @Autowired
  private TaskDataChangeJpaRepository taskDataChangeJpaRepository;

  @Operation(summary = "Get all configured securities",
      description = "Returns all GTNetExchange entries for securities with their configuration flags")
  @GetMapping(value = "/securities", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<GTSecuritiyCurrencyExchange<Security>> getSecurities(
      @Parameter(description = "When true, only returns active securities (activeToDate is null or in the future)")
      @RequestParam(defaultValue = "true") boolean activeOnly) {
    return new ResponseEntity<>(
        gtNetExchangeJpaRepository.getSecuritiesWithExchangeConfig(activeOnly), HttpStatus.OK);
  }

  @Operation(summary = "Get all configured currency pairs",
      description = "Returns all GTNetExchange entries for currency pairs with their configuration flags")
  @GetMapping(value = "/currencypairs", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<GTSecuritiyCurrencyExchange<Currencypair>> getCurrencypairs() {
    return new ResponseEntity<>(
        gtNetExchangeJpaRepository.getCurrencypairsWithExchangeConfigFull(), HttpStatus.OK);
  }

  @Operation(summary = "Batch update exchange configurations",
      description = "Updates multiple GTNetExchange entries in a single request. Only changed entries are persisted.")
  @PostMapping(value = "/batch", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<List<GTNetExchange>> batchUpdate(
      @RequestBody List<GTNetExchange> exchanges) {
    return new ResponseEntity<>(gtNetExchangeJpaRepository.batchUpdate(exchanges), HttpStatus.OK);
  }

  @Operation(summary = "Add a security to exchange configuration",
      description = "Creates a new GTNetExchange entry for a security with all flags set to false")
  @PostMapping(value = "/addsecurity/{idSecuritycurrency}", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<GTNetExchange> addSecurity(
      @Parameter(description = "The ID of the security to add")
      @PathVariable Integer idSecuritycurrency) {
    return new ResponseEntity<>(gtNetExchangeJpaRepository.addSecurity(idSecuritycurrency), HttpStatus.CREATED);
  }

  @Operation(summary = "Add a currency pair to exchange configuration",
      description = "Creates a new GTNetExchange entry for a currency pair with all flags set to false")
  @PostMapping(value = "/addcurrencypair/{idSecuritycurrency}", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<GTNetExchange> addCurrencypair(
      @Parameter(description = "The ID of the currency pair to add")
      @PathVariable Integer idSecuritycurrency) {
    return new ResponseEntity<>(gtNetExchangeJpaRepository.addCurrencypair(idSecuritycurrency), HttpStatus.CREATED);
  }

  @Operation(summary = "Delete an exchange configuration",
      description = "Removes a GTNetExchange entry, stopping all price data exchange for this instrument")
  @DeleteMapping(value = "/{id}")
  public ResponseEntity<Void> delete(
      @Parameter(description = "The ID of the GTNetExchange entry to delete")
      @PathVariable Integer id) {
    gtNetExchangeJpaRepository.deleteById(id);
    return new ResponseEntity<>(HttpStatus.NO_CONTENT);
  }

  @Operation(summary = "Get supplier details for an instrument",
      description = "Returns all GTNetSupplier entries with their details for a given security or currency pair")
  @GetMapping(value = "/supplierdetails/{idSecuritycurrency}", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<List<GTNetSupplierWithDetails>> getSupplierDetails(
      @Parameter(description = "The ID of the security or currency pair")
      @PathVariable Integer idSecuritycurrency) {
    return new ResponseEntity<>(gtNetExchangeJpaRepository.getSupplierDetails(idSecuritycurrency), HttpStatus.OK);
  }

  @Operation(summary = "Trigger exchange sync job",
      description = "Creates a background task to sync exchange configurations with GTNet peers. "
          + "This updates GTNetSupplierDetail entries based on what instruments each peer offers.")
  @PostMapping(value = "/triggersync", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<Void> triggerSync() {
    TaskDataChange taskDataChange = new TaskDataChange(
        TaskTypeExtended.GTNET_EXCHANGE_SYNC, TaskDataExecPriority.PRIO_NORMAL);
    taskDataChangeJpaRepository.save(taskDataChange);
    return new ResponseEntity<>(HttpStatus.ACCEPTED);
  }
}
