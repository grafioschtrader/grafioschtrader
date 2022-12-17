package grafioschtrader.rest;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import grafioschtrader.entities.ProposeChangeEntity;
import grafioschtrader.entities.User;
import grafioschtrader.repository.ProposeChangeEntityJpaRepository;
import grafioschtrader.repository.ProposeChangeEntityJpaRepositoryImpl.ProposeChangeEntityWithEntity;

@RestController
@RequestMapping(RequestMappings.PROPOSECHANGEENTITY_MAP)
public class ProposeChangeEntityResource extends UpdateCreateDeleteAuditResource<ProposeChangeEntity> {

  @Autowired
  ProposeChangeEntityJpaRepository proposeChangeEntityJpaRepository;

  @GetMapping(produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<List<ProposeChangeEntity>> getProposeChangeEntityListByCreatedBy() {
    User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    return new ResponseEntity<>(proposeChangeEntityJpaRepository.findByCreatedBy(user.getIdUser()), HttpStatus.OK);
  }

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
