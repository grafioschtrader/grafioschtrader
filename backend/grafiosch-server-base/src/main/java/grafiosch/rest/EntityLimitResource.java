package grafiosch.rest;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import grafiosch.dto.LimitKeyDefinition;
import grafiosch.dto.ValueKeyHtmlSelectOptions;
import grafiosch.entities.EntityLimit;
import grafiosch.entities.ProposeUserTask;
import grafiosch.entities.User;
import grafiosch.exceptions.DataViolationException;
import grafiosch.limit.EntityLimitCache;
import grafiosch.limit.LimitKeyRegistry;
import grafiosch.repository.EntityLimitJpaRepository;
import grafiosch.repository.ProposeUserTaskJpaRepository;
import grafiosch.repository.RoleJpaRepository;
import grafiosch.repository.UserJpaRepository;
import grafiosch.service.SendMailInternalExternalService;
import grafiosch.types.ReplyToRolePrivateType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping(RequestMappings.ENTITY_LIMIT_MAP)
@Tag(name = RequestMappings.ENTITY_LIMIT, description = "Controller for the configurable entity limits")
public class EntityLimitResource extends UpdateCreateDeleteAuditResource<EntityLimit> {

  @Autowired
  private EntityLimitJpaRepository entityLimitJpaRepository;

  @Autowired
  private EntityLimitCache entityLimitCache;

  @Autowired
  private ProposeUserTaskJpaRepository proposeUserTaskJpaRepository;

  @Autowired
  private SendMailInternalExternalService sendMailInternalExternalService;

  @Autowired
  private MessageSource messages;

  @Autowired
  private UserJpaRepository userJpaRepository;

  @Autowired
  private RoleJpaRepository roleJpaRepository;

  @Operation(summary = "Get every configured limit row", description = """
      Feeds the administration table, which shows the limits of every limit type and every scope in a single list.""", tags = {
      RequestMappings.ENTITY_LIMIT })
  @GetMapping(produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<List<EntityLimit>> getAllEntityLimits() {
    return new ResponseEntity<>(entityLimitJpaRepository.findAll(), HttpStatus.OK);
  }

  @Operation(summary = "Get every limit key an administrator may configure, with the metadata the edit form needs", description = """
      Returns the registered MAX keys together with the derived daily keys. Keys the given user already holds a row
      for are filtered out.""", tags = { RequestMappings.ENTITY_LIMIT })
  @GetMapping(value = "/keys", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<List<LimitKeyDefinition>> getLimitKeyDefinitions(
      @RequestParam("idUser") Optional<Integer> idUserOpt,
      @RequestParam("idEntityLimit") Optional<Integer> idEntityLimitOpt) {
    return new ResponseEntity<>(
        entityLimitJpaRepository.getLimitKeyDefinitions(idUserOpt.orElse(null), idEntityLimitOpt.orElse(null)),
        HttpStatus.OK);
  }

  @Operation(summary = "Get the roles a limit can be narrowed to", description = """
      The administration UI must not hardcode the role list, so that a value posted directly against the REST API can
      be validated against the same set.""", tags = { RequestMappings.ENTITY_LIMIT })
  @GetMapping(value = "/roles", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<List<ValueKeyHtmlSelectOptions>> getRoles() {
    return new ResponseEntity<>(roleJpaRepository.findAll().stream()
        .map(role -> new ValueKeyHtmlSelectOptions(String.valueOf(role.getIdRole()), role.getRolename())).toList(),
        HttpStatus.OK);
  }

  @Override
  protected UpdateCreateJpaRepository<EntityLimit> getUpdateCreateJpaRepository() {
    return entityLimitJpaRepository;
  }

  /**
   * Rejects deletion of the default row of a registered MAX key. Its value is editable, the row itself is permanent:
   * deleting it would silently turn a mandatory cap into no cap, because a key with no row at all resolves as
   * unlimited. Role and user override rows, and every daily row, stay freely deletable.
   */
  @Override
  protected Optional<EntityLimit> deleteById(final Integer id) {
    entityLimitJpaRepository.findById(id).ifPresent(entityLimit -> {
      if (entityLimit.getIdRole() == null && entityLimit.getIdUser() == null
          && LimitKeyRegistry.find(entityLimit.getLimitKey()).isPresent()) {
        throw new DataViolationException("key.id", "g.entity.limit.mandatory.row",
            new Object[] { entityLimit.getLimitKey().keyId() });
      }
    });
    Optional<EntityLimit> deleted = super.deleteById(id);
    entityLimitCache.evictAll();
    return deleted;
  }

  @Override
  protected ResponseEntity<EntityLimit> changeEntityWithPossibleProposals(final User userAtWork, EntityLimit entity,
      EntityLimit existingEntity) throws Exception {
    ResponseEntity<EntityLimit> result = null;
    Integer idProposeRequest = entity.getIdProposeRequest();
    if (idProposeRequest != null) {
      Optional<ProposeUserTask> proposeUserTaskOpt = proposeUserTaskJpaRepository.findById(idProposeRequest);
      if (proposeUserTaskOpt.isPresent()) {
        User targetUser = userJpaRepository.getReferenceById(entity.getIdUser());
        result = updateSaveEntity(entity, existingEntity);
        // Create internal mail
        sendMailInternalExternalService.sendInternalMail(userAtWork.getIdUser(), entity.getIdUser(), null,
            messages.getMessage("mail.subject.admin", null, Locale.forLanguageTag(targetUser.getLocaleStr())),
            entity.getNoteRequestOrReject(), null, ReplyToRolePrivateType.REPLY_NORMAL);
        proposeChangeEntityJpaRepository.deleteById(idProposeRequest);
      }
    } else {
      result = updateSaveEntity(entity, existingEntity);
    }
    return result;
  }
}
