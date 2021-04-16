package grafioschtrader.rest;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import grafioschtrader.entities.ProposeUserTask;
import grafioschtrader.entities.User;
import grafioschtrader.entities.User.AdminModify;
import grafioschtrader.repository.ProposeUserTaskJpaRepository;
import grafioschtrader.repository.UserJpaRepository;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping(RequestMappings.USERADMIN_MAP)
@Tag(name = RequestMappings.USERADMIN, description = "Controller for chaning user properites. Accesible only be admin.")
public class UserAdminResource extends UpdateCreateResource<User> {

  @Autowired
  UserJpaRepository userJpaRepository;

  @Autowired
  ProposeUserTaskJpaRepository proposeUserTaskJpaRepository;

  @Autowired
  private MessageSource messages;

  @GetMapping(value = "/", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<List<User>> getAllUsers() {
    return new ResponseEntity<>(userJpaRepository.connectUserWithUserAndLimitProposals(), HttpStatus.OK);
  }

  @GetMapping(value = "/{idUser}", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<User> getUserByIdUser(@PathVariable final Integer idUser) {
    return new ResponseEntity<>(userJpaRepository.findById(idUser).get(), HttpStatus.OK);
  }

  @Override
  @PutMapping(value = "/", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<User> update(@Validated(AdminModify.class) @RequestBody final User user) throws Exception {
    return updateEntity(user);
  }

  @Override
  protected UpdateCreateJpaRepository<User> getUpdateCreateJpaRepository() {
    return userJpaRepository;
  }

  @Override
  protected ResponseEntity<User> changeEntityWithPossibleProposals(final User userAtWork, User entity,
      User existingEntity) throws Exception {
    ResponseEntity<User> result = null;
    Integer idProposeRequest = entity.getIdProposeRequest();
    if (idProposeRequest != null) {
      Optional<ProposeUserTask> proposeUserTaskOpt = proposeUserTaskJpaRepository.findById(idProposeRequest);
      if (proposeUserTaskOpt.isPresent()) {
        result = updateSaveEntity(entity, existingEntity);
        userJpaRepository.sendSimpleMessage(entity.getUsername(),
            messages.getMessage("mail.subject.admin", null, Locale.forLanguageTag(entity.getLocaleStr())),
            entity.userChangePropose.getNoteRequest());
        proposeChangeEntityJpaRepository.deleteById(idProposeRequest);

      }
    } else {
      result = updateSaveEntity(entity, existingEntity);
    }
    return result;
  }

}
