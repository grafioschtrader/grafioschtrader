package grafiosch.repository;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

import grafiosch.BaseConstants;
import grafiosch.dto.MailSendForwardDefaultBase;
import grafiosch.dto.ValueKeyHtmlSelectOptions;
import grafiosch.entities.MailSettingForward;
import grafiosch.entities.Role;
import grafiosch.entities.User;

public class MailSettingForwardJpaRepositoryImpl extends BaseRepositoryImpl<MailSettingForward>
    implements MailSettingForwardJpaRepositoryCustom {

  @Autowired
  private MailSettingForwardJpaRepository mailSettingForwardJpaRepository;

  @Autowired
  private UserJpaRepository userJpaRepository;


  @Override
  public MailSettingForward saveOnlyAttributes(MailSettingForward mailSettingForward, MailSettingForward existingEntity,
      Set<Class<? extends Annotation>> updatePropertyLevelClasses) throws Exception {
    final User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    if(user.getMostPrivilegedRole() != Role.ROLE_ADMIN && (mailSettingForward.getIdUserRedirect() != null
        || mailSettingForward.getMessageComType().getValue() >=  MailSettingForward.MAIN_ADMIN_BASE_VALUE)
        || !MailSendForwardDefaultBase.mailSendForwardDefaultMap.get(mailSettingForward.getMessageComType()).canRedirect
        && mailSettingForward.getIdUserRedirect() != null) {
      throw new SecurityException(BaseConstants.CLIENT_SECURITY_BREACH);
    }
    return RepositoryHelper.saveOnlyAttributes(mailSettingForwardJpaRepository, mailSettingForward, existingEntity,
        updatePropertyLevelClasses);
  }

  @Override
  public MailSendForwardDefaultBase getMailSendForwardDefault() {
    var user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    List<ValueKeyHtmlSelectOptions> vkhsoList = new ArrayList<>();
    var isAdmin = user.getMostPrivilegedRole().equals(Role.ROLE_ADMIN);
    if (isAdmin) {
      userJpaRepository.getIdUserAndNicknameByRoleExcludeUser(Role.ROLE_ADMIN, user.getIdUser()).forEach(
          rs -> vkhsoList.add(new ValueKeyHtmlSelectOptions(String.valueOf(rs.getIdUser()), rs.getIdUser() + " - " + rs.getNickname())));
    }
    return new MailSendForwardDefaultBase(vkhsoList, isAdmin);
  }

  @Transactional
  public int delEntityWithUserId(Integer id, Integer idUser) {
    return mailSettingForwardJpaRepository.deleteByIdUserAndIdMailSettingForward(idUser, id);
  }

}
