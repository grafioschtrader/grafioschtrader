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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import grafiosch.entities.Role;
import grafiosch.entities.User;
import grafiosch.rest.UpdateCreateDeleteAuditResource;
import grafiosch.rest.UpdateCreateJpaRepository;
import grafioschtrader.GlobalConstants;
import grafioschtrader.connector.instrument.generic.GenericFeedConnectorFactory;
import grafioschtrader.entities.GenericConnectorDef;
import grafioschtrader.repository.GenericConnectorDefJpaRepository;
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

  @Override
  protected UpdateCreateJpaRepository<GenericConnectorDef> getUpdateCreateJpaRepository() {
    return genericConnectorDefJpaRepository;
  }

  @Override
  protected String getPrefixEntityLimit() {
    return GlobalConstants.GT_LIMIT_DAY;
  }

  @Operation(summary = "Return all generic connector definitions", tags = {GenericConnectorDef.TABNAME})
  @GetMapping(produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<List<GenericConnectorDef>> getAllGenericConnectors() {
    return new ResponseEntity<>(genericConnectorDefJpaRepository.findAll(), HttpStatus.OK);
  }

  @Operation(summary = "Return a single generic connector definition by ID", tags = {GenericConnectorDef.TABNAME})
  @GetMapping(value = "/{id}", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<GenericConnectorDef> getGenericConnector(@PathVariable final Integer id) {
    return genericConnectorDefJpaRepository.findById(id)
        .map(def -> new ResponseEntity<>(def, HttpStatus.OK))
        .orElse(new ResponseEntity<>(HttpStatus.NOT_FOUND));
  }

  @Operation(summary = "Activate a connector (admin only). Sets activated=true and transfers ownership to admin.",
      tags = {GenericConnectorDef.TABNAME})
  @PostMapping(value = "/activate/{id}", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<GenericConnectorDef> activateConnector(@PathVariable final Integer id) {
    checkAdmin();
    User admin = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    return genericConnectorDefJpaRepository.findById(id).map(def -> {
      def.setActivated(true);
      def.setCreatedBy(admin.getIdUser());
      GenericConnectorDef saved = genericConnectorDefJpaRepository.save(def);
      genericFeedConnectorFactory.reload();
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

  private void checkAdmin() {
    User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    if (user.getMostPrivilegedRole() != Role.ROLE_ADMIN) {
      throw new SecurityException("Admin access required");
    }
  }
}
