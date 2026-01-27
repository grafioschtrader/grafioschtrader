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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import grafiosch.entities.User;
import grafioschtrader.entities.GTNetSecurityImpPos;
import grafioschtrader.repository.GTNetSecurityImpPosJpaRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/**
 * REST controller for managing GTNet security import positions. Provides CRUD operations
 * for individual security entries within an import batch.
 */
@RestController
@RequestMapping(RequestGTMappings.GTNETSECURITYIMPPOS_MAP)
@Tag(name = RequestGTMappings.GTNETSECURITYIMPPOS, description = "Controller for GTNet Security Import Positions")
public class GTNetSecurityImpPosResource {

  @Autowired
  private GTNetSecurityImpPosJpaRepository gtNetSecurityImpPosJpaRepository;

  @Operation(summary = "Get all positions for an import header",
      description = "Returns all GTNet security import positions belonging to the specified header.",
      tags = { RequestGTMappings.GTNETSECURITYIMPPOS })
  @GetMapping(value = "/head/{idGtNetSecurityImpHead}", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<List<GTNetSecurityImpPos>> getByHead(@PathVariable final Integer idGtNetSecurityImpHead) {
    final User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    return new ResponseEntity<>(
        gtNetSecurityImpPosJpaRepository.findByIdGtNetSecurityImpHeadAndIdTenant(idGtNetSecurityImpHead,
            user.getIdTenant()),
        HttpStatus.OK);
  }

  @Operation(summary = "Create a new import position",
      description = "Creates a new GTNet security import position within the specified header.",
      tags = { RequestGTMappings.GTNETSECURITYIMPPOS })
  @PostMapping(produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<GTNetSecurityImpPos> create(@Valid @RequestBody GTNetSecurityImpPos entity) {
    final User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    return new ResponseEntity<>(gtNetSecurityImpPosJpaRepository.saveWithTenantCheck(entity, user.getIdTenant()),
        HttpStatus.OK);
  }

  @Operation(summary = "Update an existing import position",
      description = "Updates an existing GTNet security import position.",
      tags = { RequestGTMappings.GTNETSECURITYIMPPOS })
  @PutMapping(produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<GTNetSecurityImpPos> update(@Valid @RequestBody GTNetSecurityImpPos entity) {
    final User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    return new ResponseEntity<>(gtNetSecurityImpPosJpaRepository.saveWithTenantCheck(entity, user.getIdTenant()),
        HttpStatus.OK);
  }

  @Operation(summary = "Delete an import position",
      description = "Deletes a GTNet security import position by its ID.",
      tags = { RequestGTMappings.GTNETSECURITYIMPPOS })
  @DeleteMapping(value = "/{id}", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<Void> delete(@PathVariable final Integer id) {
    final User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    gtNetSecurityImpPosJpaRepository.deleteWithTenantCheck(id, user.getIdTenant());
    return ResponseEntity.noContent().build();
  }
}
