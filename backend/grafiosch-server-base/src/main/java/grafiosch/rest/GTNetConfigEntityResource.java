package grafiosch.rest;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import grafiosch.entities.GTNetConfigEntity;
import grafiosch.entities.User;
import grafiosch.repository.GTNetConfigEntityJpaRepository;
import grafiosch.repository.GTNetEntityJpaRepositoryBase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/**
 * REST controller for managing GTNetConfigEntity.
 * Only update operations are supported - entities are created by the system
 * when exchange requests are accepted (GT_NET_UPDATE_SERVERLIST_ACCEPT_S).
 *
 * Only administrators can edit GTNetConfigEntity.
 * Editable fields: useDetailLog, consumerUsage
 */
@RestController
@RequestMapping(RequestMappings.GTNETCONFIGENTITY_MAP)
@Tag(name = RequestMappings.GTNETCONFIGENTITY, description = "Controller for GTNet entity configuration")
public class GTNetConfigEntityResource extends UpdateCreate<GTNetConfigEntity> {

  @Autowired
  private GTNetConfigEntityJpaRepository gtNetConfigEntityJpaRepository;

  @Autowired
  private GTNetEntityJpaRepositoryBase gtNetEntityJpaRepository;

  @Override
  protected UpdateCreateJpaRepository<GTNetConfigEntity> getUpdateCreateJpaRepository() {
    return gtNetConfigEntityJpaRepository;
  }

  /**
   * Allows upsert: if the GTNetConfigEntity does not exist yet but the parent GTNetEntity does,
   * create the config entity instead of returning 404.
   */
  @Override
  protected ResponseEntity<GTNetConfigEntity> updateSpecialEntity(User user, GTNetConfigEntity entity) throws Exception {
    GTNetConfigEntity existingEntity = gtNetConfigEntityJpaRepository.findById(entity.getId()).orElse(null);
    if (existingEntity == null && gtNetEntityJpaRepository.existsById(entity.getIdGtNetEntity())) {
      // Parent entity exists but config entity does not — allow creation
      existingEntity = null;
    } else if (existingEntity == null) {
      return ResponseEntity.notFound().build();
    }
    return updateSaveEntity(entity, existingEntity);
  }

  /**
   * Updates a GTNetConfigEntity. Only useDetailLog and consumerUsage can be modified.
   * Restricted to administrators only.
   */
  @Operation(summary = "Update GTNetConfigEntity configuration",
      description = "Updates useDetailLog and consumerUsage for an existing GTNetConfigEntity. Admin only.",
      tags = { RequestMappings.GTNETCONFIGENTITY })
  @PreAuthorize("hasRole('ADMIN')")
  @PutMapping(produces = APPLICATION_JSON_VALUE)
  @Override
  public ResponseEntity<GTNetConfigEntity> update(@Valid @RequestBody final GTNetConfigEntity entity) throws Exception {
    return updateEntity(entity);
  }


}
