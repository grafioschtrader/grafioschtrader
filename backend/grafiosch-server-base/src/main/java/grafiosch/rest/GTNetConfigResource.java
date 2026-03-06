package grafiosch.rest;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import grafiosch.entities.GTNetConfig;
import grafiosch.repository.GTNetConfigJpaRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/**
 * REST controller for managing GTNetConfig.
 * Only update of the connectionTimeout field is supported - the entity itself is created
 * during the handshake process.
 *
 * Only administrators can edit GTNetConfig.
 */
@RestController
@RequestMapping(RequestMappings.GTNETCONFIG_MAP)
@Tag(name = RequestMappings.GTNETCONFIG, description = "Controller for GTNet connection configuration")
public class GTNetConfigResource extends UpdateCreate<GTNetConfig> {

  @Autowired
  private GTNetConfigJpaRepository gtNetConfigJpaRepository;

  @Override
  protected UpdateCreateJpaRepository<GTNetConfig> getUpdateCreateJpaRepository() {
    return gtNetConfigJpaRepository;
  }

  /**
   * Updates a GTNetConfig. Only connectionTimeout can be modified by the user.
   * Restricted to administrators only.
   */
  @Operation(summary = "Update GTNetConfig connection settings",
      description = "Updates connectionTimeout for an existing GTNetConfig. Admin only.",
      tags = { RequestMappings.GTNETCONFIG })
  @PreAuthorize("hasRole('ADMIN')")
  @PutMapping(produces = APPLICATION_JSON_VALUE)
  @Override
  public ResponseEntity<GTNetConfig> update(@Valid @RequestBody final GTNetConfig entity) throws Exception {
    return updateEntity(entity);
  }

}
