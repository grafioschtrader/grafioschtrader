package grafioschtrader.rest;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import grafioschtrader.dto.ValueKeyHtmlSelectOptions;
import grafioschtrader.entities.ProposeUserTask;
import grafioschtrader.entities.User;
import grafioschtrader.entities.UserEntityChangeLimit;
import grafioschtrader.repository.MailSendboxJpaRepository;
import grafioschtrader.repository.ProposeUserTaskJpaRepository;
import grafioschtrader.repository.UserEntityChangeLimitJpaRepository;
import grafioschtrader.repository.UserJpaRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping(RequestMappings.USER_ENTITY_CHANGE_LIMIT_MAP)
@Tag(name = RequestMappings.USER_ENTITY_CHANGE_LIMIT, description = "Controller for user change limits")
public class UserEntityChangeLimitRessource extends UpdateCreateDeleteAuditResource<UserEntityChangeLimit> {

  @Autowired
  UserEntityChangeLimitJpaRepository userEntityChangeLimitJpaRepository;

  @Autowired
  ProposeUserTaskJpaRepository proposeUserTaskJpaRepository;

  @Autowired
  private MailSendboxJpaRepository mailSendboxJpaRepository;

  @Autowired
  private MessageSource messages;

  @Autowired
  private UserJpaRepository userJpaRepository;

  @Operation(summary = "Get the limits for shated information classes by user if there are", description = "", tags = {
      RequestMappings.USER_ENTITY_CHANGE_LIMIT })
  @GetMapping(value = "/{idUser}/entities", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<List<ValueKeyHtmlSelectOptions>> getPossibleEntities(@PathVariable final Integer idUser,
      @RequestParam("idUserEntityChangeLimit") Optional<Integer> idUserEntityChangeLimitOpt) {
    return new ResponseEntity<>(userEntityChangeLimitJpaRepository.getPublicEntitiesAsHtmlSelectOptions(idUser,
        idUserEntityChangeLimitOpt.orElse(null)), HttpStatus.OK);
  }

  @Override
  protected UpdateCreateJpaRepository<UserEntityChangeLimit> getUpdateCreateJpaRepository() {
    return userEntityChangeLimitJpaRepository;
  }

  @Override
  protected ResponseEntity<UserEntityChangeLimit> changeEntityWithPossibleProposals(final User userAtWork,
      UserEntityChangeLimit entity, UserEntityChangeLimit existingEntity) throws Exception {
    ResponseEntity<UserEntityChangeLimit> result = null;
    Integer idProposeRequest = entity.getIdProposeRequest();
    if (idProposeRequest != null) {
      Optional<ProposeUserTask> proposeUserTaskOpt = proposeUserTaskJpaRepository.findById(idProposeRequest);
      if (proposeUserTaskOpt.isPresent()) {
        User targetUser = userJpaRepository.getReferenceById(entity.getIdUser());
        result = updateSaveEntity(entity, existingEntity);
        // Create internal mail
        mailSendboxJpaRepository.sendInternalMail(userAtWork.getIdUser(), entity.getIdUser(), null,
            messages.getMessage("mail.subject.admin", null, Locale.forLanguageTag(targetUser.getLocaleStr())),
            entity.getNoteRequestOrReject());
        proposeChangeEntityJpaRepository.deleteById(idProposeRequest);

      }
    } else {
      result = updateSaveEntity(entity, existingEntity);
    }
    return result;
  }

}
