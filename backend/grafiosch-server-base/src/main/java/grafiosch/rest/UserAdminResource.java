package grafiosch.rest;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import java.sql.SQLException;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import grafiosch.dto.ValueKeyHtmlSelectOptions;
import grafiosch.entities.ProposeUserTask;
import grafiosch.entities.User;
import grafiosch.entities.User.AdminModify;
import grafiosch.repository.ProposeUserTaskJpaRepository;
import grafiosch.repository.UserJpaRepository;
import grafiosch.service.MailExternalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping(RequestMappings.USERADMIN_MAP)
@Tag(name = RequestMappings.USERADMIN, description = "Crontroller for changing the user settings. Accesible only be admin.")
public class UserAdminResource extends UpdateCreateResource<User> {

  @Autowired
  private UserJpaRepository userJpaRepository;

  @Autowired
  private ProposeUserTaskJpaRepository proposeUserTaskJpaRepository;

  @Autowired
  private MessageSource messages;

  @Autowired
  private MailExternalService mailExternalService;

  @Operation(summary = "Get all users with limited proposal information", description = """
      Returns a list of all users. For each user, it also connects information about their proposals,
      limited for performance.""")
  @GetMapping(produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<List<User>> getAllUsers() {
    return new ResponseEntity<>(userJpaRepository.connectUserWithUserAndLimitProposals(), HttpStatus.OK);
  }

//  @GetMapping(value = "{idUser}", produces = APPLICATION_JSON_VALUE)
//  public ResponseEntity<User> getUserByIdUser(@PathVariable final Integer idUser) {
//    return new ResponseEntity<>(userJpaRepository.findById(idUser).get(), HttpStatus.OK);
//  }

  @Override
  @PutMapping(produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<User> update(@Validated(AdminModify.class) @RequestBody final User user) throws Exception {
    return updateEntity(user);
  }

  @Operation(summary = "Change the owner of shared entities", description = "", tags = { RequestMappings.USERADMIN })
  @PatchMapping("{fromIdUser}/{toIdUser}")
  public ResponseEntity<Integer> moveCreatedByUserToOtherUser(
      @Parameter(description = "Id of user who give up his entities", required = true) @PathVariable Integer fromIdUser,
      @Parameter(description = "Id of user who gets the entities", required = true) @PathVariable Integer toIdUser)
      throws SQLException {
    return new ResponseEntity<>(userJpaRepository.moveCreatedByUserToOtherUser(fromIdUser, toIdUser), HttpStatus.OK);
  }

  @Operation(summary = "Get user ID and nickname, excluding the current user", description = """
      Returns a list of value-key pairs representing user IDs and nicknames,
       excluding the currently authenticated user. Useful for selection lists.""")
  @GetMapping(value = "/idnicknameexcludeme", produces = APPLICATION_JSON_VALUE)
  public List<ValueKeyHtmlSelectOptions> getIdUserAndNicknameExcludeMe() {
    return userJpaRepository.getIdUserAndNicknameExcludeMe();
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
        mailExternalService.sendSimpleMessageAsync(entity.getUsername(),
            messages.getMessage("mail.subject.admin", null, Locale.forLanguageTag(entity.getLocaleStr())),
            entity.getNoteRequestOrReject());
        proposeChangeEntityJpaRepository.deleteById(idProposeRequest);
      }
    } else {
      result = updateSaveEntity(entity, existingEntity);
    }
    return result;
  }

}
