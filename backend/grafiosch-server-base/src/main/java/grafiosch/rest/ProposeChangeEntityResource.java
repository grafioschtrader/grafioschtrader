package grafiosch.rest;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import grafiosch.entities.ProposeChangeEntity;
import grafiosch.entities.User;
import grafiosch.repository.ProposeChangeEntityJpaRepository;
import grafiosch.repository.ProposeChangeEntityJpaRepositoryImpl.ProposeChangeEntityWithEntity;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping(RequestMappings.PROPOSECHANGEENTITY_MAP)
@Tag(name = RequestMappings.PROPOSECHANGEENTITY, description = "Controller for propose change entity")
public class ProposeChangeEntityResource extends UpdateCreateDeleteAuditResource<ProposeChangeEntity> {

  @Autowired
  private ProposeChangeEntityJpaRepository proposeChangeEntityJpaRepository;

  @Operation(summary = """
      Return of self-created change requests to the entities. This shows whether the change request has been accepted and implemented.""", description = "", tags = {
      RequestMappings.PROPOSECHANGEENTITY })
  @GetMapping(produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<List<ProposeChangeEntity>> getProposeChangeEntityListByCreatedBy() {
    User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    return new ResponseEntity<>(proposeChangeEntityJpaRepository.findByCreatedBy(user.getIdUser()), HttpStatus.OK);
  }

  @Operation(summary = "Return a list of change requests to the shared entities", description = "", tags = {
      RequestMappings.PROPOSECHANGEENTITY })
  @GetMapping(value = "/withentity", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<List<ProposeChangeEntityWithEntity>> getProposeChangeEntityWithEntity() throws Exception {
    List<ProposeChangeEntityWithEntity> proposeChangeEntityWithEntityList = proposeChangeEntityJpaRepository
        .getProposeChangeEntityWithEntity();
    return new ResponseEntity<>(proposeChangeEntityWithEntityList, HttpStatus.OK);
  }

  @Override
  protected UpdateCreateJpaRepository<ProposeChangeEntity> getUpdateCreateJpaRepository() {
    return proposeChangeEntityJpaRepository;
  }

}
