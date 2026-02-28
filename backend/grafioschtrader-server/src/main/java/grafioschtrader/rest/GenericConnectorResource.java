package grafioschtrader.rest;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import grafiosch.common.UserAccessHelper;
import grafiosch.entities.Auditable;
import grafiosch.entities.Role;
import grafiosch.entities.User;
import grafiosch.exceptions.DataViolationException;
import grafiosch.rest.UpdateCreateDeleteAuditResource;
import grafiosch.rest.UpdateCreateJpaRepository;
import grafioschtrader.GlobalConstants;
import grafioschtrader.connector.instrument.BaseFeedConnector;
import grafioschtrader.connector.instrument.generic.GenericConnectorTestRequest;
import grafioschtrader.connector.instrument.generic.GenericConnectorTestResult;
import grafioschtrader.connector.instrument.generic.GenericConnectorTestService;
import grafioschtrader.connector.instrument.generic.GenericFeedConnectorFactory;
import grafioschtrader.entities.GenericConnectorDef;
import grafioschtrader.repository.CurrencypairJpaRepository;
import grafioschtrader.repository.GenericConnectorDefJpaRepository;
import grafioschtrader.repository.SecurityJpaRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * REST resource for managing generic feed connector definitions. Any user can create connectors (with daily limits),
 * but only admins can activate them. CRUD operations follow the Auditable ownership model.
 */
@RestController
@RequestMapping(RequestGTMappings.GENERIC_CONNECTOR_MAP)
@Tag(name = GenericConnectorDef.TABNAME, description = "CRUD for generic configurable feed connectors")
public class GenericConnectorResource extends UpdateCreateDeleteAuditResource<GenericConnectorDef> {

  @Autowired
  private GenericConnectorDefJpaRepository genericConnectorDefJpaRepository;

  @Autowired
  private GenericFeedConnectorFactory genericFeedConnectorFactory;

  @Autowired
  private GenericConnectorTestService genericConnectorTestService;

  @Autowired
  private SecurityJpaRepository securityJpaRepository;

  @Autowired
  private CurrencypairJpaRepository currencypairJpaRepository;

  @Override
  protected UpdateCreateJpaRepository<GenericConnectorDef> getUpdateCreateJpaRepository() {
    return genericConnectorDefJpaRepository;
  }

  @Override
  protected String getPrefixEntityLimit() {
    return GlobalConstants.GT_LIMIT_DAY;
  }

  /**
   * Prevents non-owner/non-admin users from falling through to the proposal path. The creator can edit directly
   * (checked by the base class via UserAccessHelper), but other limited users must not create change proposals
   * for connectors — they simply have no editing rights.
   */
  @Override
  protected boolean hasRightsForEditingEntity(User user, GenericConnectorDef newEntity,
      GenericConnectorDef existingEntity, Auditable parentEntity) {
    if (UserAccessHelper.hasRightsForEditingOrDeleteOnEntity(user, (Auditable) existingEntity)) {
      return true;
    }
    throw new SecurityException("No editing rights for this generic connector");
  }

  /**
   * Deletes a generic connector definition only if no securities or currency pairs reference it. After successful
   * deletion, reloads all generic connectors to keep the runtime registry in sync.
   */
  @Override
  public ResponseEntity<Void> deleteResource(@PathVariable final Integer id) {
    GenericConnectorDef def = genericConnectorDefJpaRepository.findById(id)
        .orElseThrow(() -> new SecurityException("Connector not found"));
    populateInstrumentCount(def);
    if (def.getInstrumentCount() > 0) {
      throw new DataViolationException("generic.connector.def", "gt.connector.def.referenced",
          new Object[]{def.getInstrumentCount()});
    }
    ResponseEntity<Void> response = super.deleteResource(id);
    genericFeedConnectorFactory.reload();
    return response;
  }

  @Operation(summary = "Return all generic connector definitions", tags = {GenericConnectorDef.TABNAME})
  @GetMapping(produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<List<GenericConnectorDef>> getAllGenericConnectors() {
    List<GenericConnectorDef> connectors = genericConnectorDefJpaRepository.findAll();
    connectors.forEach(this::populateInstrumentCount);
    return new ResponseEntity<>(connectors, HttpStatus.OK);
  }

  @Operation(summary = "Return a single generic connector definition by ID", tags = {GenericConnectorDef.TABNAME})
  @GetMapping(value = "/{id}", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<GenericConnectorDef> getGenericConnector(@PathVariable final Integer id) {
    return genericConnectorDefJpaRepository.findById(id)
        .map(def -> {
          populateInstrumentCount(def);
          return new ResponseEntity<>(def, HttpStatus.OK);
        })
        .orElse(new ResponseEntity<>(HttpStatus.NOT_FOUND));
  }

  @Operation(summary = "Activate a connector (admin only). Sets activated=true.",
      tags = {GenericConnectorDef.TABNAME})
  @PostMapping(value = "/activate/{id}", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<GenericConnectorDef> activateConnector(@PathVariable final Integer id) {
    checkAdmin();
    return genericConnectorDefJpaRepository.findById(id).map(def -> {
      def.setActivated(true);
      GenericConnectorDef saved = genericConnectorDefJpaRepository.save(def);
      genericFeedConnectorFactory.reload();
      return new ResponseEntity<>(saved, HttpStatus.OK);
    }).orElse(new ResponseEntity<>(HttpStatus.NOT_FOUND));
  }

  @Operation(summary = "Deactivate a connector (admin only). Sets activated=false.",
      tags = {GenericConnectorDef.TABNAME})
  @PostMapping(value = "/deactivate/{id}", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<GenericConnectorDef> deactivateConnector(@PathVariable final Integer id) {
    checkAdmin();
    return genericConnectorDefJpaRepository.findById(id).map(def -> {
      def.setActivated(false);
      GenericConnectorDef saved = genericConnectorDefJpaRepository.save(def);
      genericFeedConnectorFactory.reload();
      populateInstrumentCount(saved);
      return new ResponseEntity<>(saved, HttpStatus.OK);
    }).orElse(new ResponseEntity<>(HttpStatus.NOT_FOUND));
  }

  @Operation(summary = "Reload all generic connectors from the database and re-register them as Spring beans (admin only)",
      tags = {GenericConnectorDef.TABNAME})
  @PostMapping(value = "/reload", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<Void> reloadGenericConnectors() {
    checkAdmin();
    genericFeedConnectorFactory.reload();
    return ResponseEntity.ok().build();
  }

  @Operation(summary = "Test a connector endpoint with a ticker and optional date range",
      tags = {GenericConnectorDef.TABNAME})
  @PostMapping(value = "/test", consumes = APPLICATION_JSON_VALUE, produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<GenericConnectorTestResult> testEndpoint(@RequestBody GenericConnectorTestRequest request) {
    return new ResponseEntity<>(genericConnectorTestService.testEndpoint(request), HttpStatus.OK);
  }

  private void checkAdmin() {
    User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    if (user.getMostPrivilegedRole() != Role.ROLE_ADMIN) {
      throw new SecurityException("Admin access required");
    }
  }

  private void populateInstrumentCount(GenericConnectorDef def) {
    String connectorId = BaseFeedConnector.ID_PREFIX + def.getShortId();
    def.setInstrumentCount(securityJpaRepository.countByAnyConnectorId(connectorId)
        + currencypairJpaRepository.countByAnyConnectorId(connectorId));
  }
}
