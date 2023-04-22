package grafioschtrader.repository;

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

import grafioschtrader.common.ValueFormatConverter;
import grafioschtrader.entities.ProposeChangeField;
import grafioschtrader.entities.ProposeUserTask;
import grafioschtrader.entities.Role;
import grafioschtrader.entities.User;
import grafioschtrader.entities.UserEntityChangeLimit;
import grafioschtrader.types.MessageComType;
import grafioschtrader.usertask.UserTaskType;
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
  private MailSettingForwardJpaRepository mailSettingForwardJpaRepository;

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
      mailSettingForwardJpaRepository.sendMailToMainAdminInternalOrExternal(idTargetUser, "reset.user.misused", note,
          MessageComType.MAIN_ADMIN_RELEASE_LOGOUT);
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
   * To be sure the are only the right properties
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
        userJpaRepository.sendSimpleMessage(user.getUsername(),
            messagesSource.getMessage("mail.subject.admin", null, Locale.forLanguageTag(user.getLocaleStr())),
            rejectNote);
        proposeChangeEntityJpaRepository.deleteById(idProposeRequest);
        return messagesSource.getMessage("mail.send", null, Locale.forLanguageTag(userAdmin.getLocaleStr()));
      }
    }
    throw new IllegalArgumentException("Proposed user task not exists");
  }

}
