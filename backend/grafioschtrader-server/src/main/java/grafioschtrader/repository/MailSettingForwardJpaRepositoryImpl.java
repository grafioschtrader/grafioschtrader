package grafioschtrader.repository;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

import grafioschtrader.GlobalConstants;
import grafioschtrader.dto.MailSendForwardDefault;
import grafioschtrader.dto.ValueKeyHtmlSelectOptions;
import grafioschtrader.entities.MailSettingForward;
import grafioschtrader.entities.Role;
import grafioschtrader.entities.User;

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
        || mailSettingForward.getMessageComType().getValue() >=  MailSendForwardDefault.MAIN_ADMIN_BASE_VALUE)
        || !MailSendForwardDefault.mailSendForwardDefaultMap.get(mailSettingForward.getMessageComType()).canRedirect
        && mailSettingForward.getIdUserRedirect() != null) {
      throw new SecurityException(GlobalConstants.CLIENT_SECURITY_BREACH);
    }
    return RepositoryHelper.saveOnlyAttributes(mailSettingForwardJpaRepository, mailSettingForward, existingEntity,
        updatePropertyLevelClasses);
  }

  @Override
  public MailSendForwardDefault getMailSendForwardDefault() {
    var user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    List<ValueKeyHtmlSelectOptions> vkhsoList = new ArrayList<>();
    var isAdmin = user.getMostPrivilegedRole().equals(Role.ROLE_ADMIN);
    if (isAdmin) {
      userJpaRepository.getIdUserAndNicknameByRoleExcludeUser(Role.ROLE_ADMIN, user.getIdUser()).forEach(
          rs -> vkhsoList.add(new ValueKeyHtmlSelectOptions(String.valueOf(rs.getIdUser()), rs.getIdUser() + " - " + rs.getNickname())));
    }
    return new MailSendForwardDefault(vkhsoList, isAdmin);
  }

  @Transactional
  public int delEntityWithUserId(Integer id, Integer idUser) {
    return mailSettingForwardJpaRepository.deleteByIdUserAndIdMailSettingForward(idUser, id);
  }

}
