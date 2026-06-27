package grafiosch.repository;

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.apache.commons.lang3.SerializationUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

import grafiosch.common.ValueFormatConverter;
import grafiosch.entities.ProposeChangeField;
import grafiosch.entities.ProposeUserTask;
import grafiosch.entities.Role;
import grafiosch.entities.User;
import grafiosch.entities.UserEntityChangeLimit;
import grafiosch.exceptions.DataViolationException;
import grafiosch.service.MailExternalService;
import grafiosch.service.SendMailInternalExternalService;
import grafiosch.types.MessageComType;
import grafiosch.usertask.UserTaskType;
import jakarta.mail.MessagingException;

public class ProposeUserTaskJpaRepositoryImpl extends ProposeRequestService<ProposeUserTask>
    implements ProposeUserTaskJpaRepositoryCustom {

  private final Logger log = LoggerFactory.getLogger(this.getClass());

  @Autowired
  private ProposeUserTaskJpaRepository proposeUserTaskJpaRepository;

  @Autowired
  private ProposeChangeFieldJpaRepository proposeChangeFieldJpaRepository;

  @Autowired
  private ProposeChangeEntityJpaRepository proposeChangeEntityJpaRepository;

  @Autowired
  private RoleJpaRepository roleJpaRepository;

  @Autowired
  private MessageSource messagesSource;

  @Autowired
  private UserJpaRepository userJpaRepository;

  @Autowired
  private SendMailInternalExternalService sendMailInternalExternalService;

  @Autowired
  private MailExternalService mailExternalService;

  @Override
  public void createReleaseLougout(Integer idTargetUser, String field, String note) throws Exception {
    ProposeUserTask proposeUserTask = new ProposeUserTask();
    proposeUserTask.setIdTargetUser(idTargetUser);
    proposeUserTask.setUserTaskType(UserTaskType.RELEASE_LOGOUT);
    proposeUserTask.setNoteRequest(note);
    ProposeChangeField proposeChangeField = new ProposeChangeField();
    proposeChangeField.setField(field);
    proposeChangeField.setValue(SerializationUtils.serialize((short) 0));
    proposeUserTask.setProposeChangeFieldList(List.of(proposeChangeField));
    save(proposeUserTask, null, null);
    try {
      sendMailInternalExternalService.sendMailToMainAdminInternalOrExternal(idTargetUser, "reset.user.misused", null,
          note, MessageComType.MAIN_ADMIN_RELEASE_LOGOUT);
    } catch (MessagingException me) {
      log.warn("Could not send email to admin");
    }
  }

  @Override
  public ProposeUserTask saveOnlyAttributes(ProposeUserTask proposeUserTask, ProposeUserTask existingEntity,
      final Set<Class<? extends Annotation>> updatePropertyLevelClasses) throws Exception {
    final User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    proposeUserTask.setIdTargetUser(user.getIdUser());
    return save(proposeUserTask, existingEntity, updatePropertyLevelClasses);
  }

  private ProposeUserTask save(ProposeUserTask proposeUserTask, ProposeUserTask existingEntity,
      final Set<Class<? extends Annotation>> updatePropertyLevelClasses) throws Exception {
    proposeUserTask
        .setEntity(proposeUserTask.getUserTaskType() == UserTaskType.RELEASE_LOGOUT ? User.class.getSimpleName()
            : UserEntityChangeLimit.class.getSimpleName());
    checkPropertiesInModel(proposeUserTask);
    rejectDuplicatePendingLimitRequest(proposeUserTask);
    Role role = roleJpaRepository.findByRolename(Role.ROLE_ADMIN);
    proposeUserTask.setIdRoleTo(role.getIdRole());
    List<ProposeChangeField> proposeChangeField = proposeUserTask.getProposeChangeFieldList();
    proposeUserTask.setProposeChangeFieldList(null);
    final ProposeUserTask newProposeUserTask = proposeUserTaskJpaRepository.save(proposeUserTask);
    proposeChangeField.forEach(pcf -> pcf.setIdProposeRequest(newProposeUserTask.getIdProposeRequest()));
    proposeChangeFieldJpaRepository.saveAll(proposeChangeField);
    return proposeUserTaskJpaRepository.findById(newProposeUserTask.getIdProposeRequest()).get();
  }

  /**
   * Validates proposal change fields against the corresponding model class properties. This method ensures that only
   * valid properties with correct data types are included in the proposal by:<br>
   * - Dynamically instantiating the model class for the task type<br>
   * - Extracting property data types from the model<br>
   * - Validating each proposed change field against the model structure<br>
   * - Converting and setting values to verify compatibility
   * 
   * @param proposeUserTask the user task proposal containing fields to validate
   */
  private void checkPropertiesInModel(ProposeUserTask proposeUserTask) throws Exception {
    Constructor<?> modelClazz = ProposeUserTask.getModelByUserTaskType(proposeUserTask.getUserTaskType())
        .getConstructor();
    Object bean = modelClazz.newInstance();
    ValueFormatConverter valueFormatConverter = new ValueFormatConverter();
    Map<String, Class<?>> dataTypesMap = ValueFormatConverter.getDataTypeOfPropertiesByBean(bean);

    for (ProposeChangeField proposeChangeField : proposeUserTask.getProposeChangeFieldList()) {
      valueFormatConverter.convertAndSetValue(bean, proposeChangeField.getField(),
          proposeChangeField.getValueDesarialized(), dataTypesMap.get(proposeChangeField.getField()));
    }
  }

  /**
   * Prevents a user from filing a second open CUD-limit increase request for an entity they already have a pending
   * request for. Two pending requests for the same (user, entity) could both be approved, and the second approval would
   * collide with the unique key on {@code user_entity_change_limit (id_user, entity_name)}. Only
   * {@link UserTaskType#LIMIT_CUD_CHANGE} requests carry an entity and are checked.
   *
   * @param proposeUserTask the incoming request whose target entity is matched against existing pending requests
   * @throws DataViolationException if another pending request of the same user already targets the same entity
   */
  private void rejectDuplicatePendingLimitRequest(ProposeUserTask proposeUserTask) {
    if (proposeUserTask.getUserTaskType() != UserTaskType.LIMIT_CUD_CHANGE) {
      return;
    }
    String requestedEntity = extractEntityName(proposeUserTask.getProposeChangeFieldList());
    if (requestedEntity == null) {
      return;
    }
    boolean duplicate = proposeUserTaskJpaRepository
        .findByIdTargetUserAndUserTaskType(proposeUserTask.getIdTargetUser(),
            UserTaskType.LIMIT_CUD_CHANGE.getValue())
        .stream()
        .filter(existing -> !existing.getIdProposeRequest().equals(proposeUserTask.getIdProposeRequest()))
        .anyMatch(existing -> requestedEntity.equals(extractEntityName(existing.getProposeChangeFieldList())));
    if (duplicate) {
      throw new DataViolationException("entity.name", "g.user.entity.change.limit.request.pending",
          new Object[] { requestedEntity });
    }
  }

  /**
   * Reads the requested entity class name from a {@link grafiosch.usertask.LimitCudChange} proposal's change fields.
   *
   * @param proposeChangeFieldList the propose-change fields of a {@link UserTaskType#LIMIT_CUD_CHANGE} request
   * @return the entity name carried in the {@code entity} field, or {@code null} if not present
   */
  private String extractEntityName(List<ProposeChangeField> proposeChangeFieldList) {
    if (proposeChangeFieldList == null) {
      return null;
    }
    return proposeChangeFieldList.stream().filter(field -> "entity".equals(field.getField()))
        .map(ProposeChangeField::getValueDesarialized).findFirst().orElse(null);
  }

  @Override
  @Transactional
  @Modifying
  public String rejectUserTask(Integer idProposeRequest, String rejectNote) throws MessagingException {
    Optional<ProposeUserTask> proposeUserTaskOpt = proposeUserTaskJpaRepository.findById(idProposeRequest);
    if (proposeUserTaskOpt.isPresent()) {
      ProposeUserTask propposeUserTask = proposeUserTaskOpt.get();
      Optional<User> userOpt = userJpaRepository.findById(propposeUserTask.getIdTargetUser());
      if (userOpt.isPresent()) {
        final User userAdmin = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
        User user = userOpt.get();
        mailExternalService.sendSimpleMessageAsync(user.getUsername(),
            messagesSource.getMessage("mail.subject.admin", null, Locale.forLanguageTag(user.getLocaleStr())),
            rejectNote);
        proposeChangeEntityJpaRepository.deleteById(idProposeRequest);
        return messagesSource.getMessage("mail.send", null, Locale.forLanguageTag(userAdmin.getLocaleStr()));
      }
    }
    throw new IllegalArgumentException("Proposed user task not exists");
  }

}
