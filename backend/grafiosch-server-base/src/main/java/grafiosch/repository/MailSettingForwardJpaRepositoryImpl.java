package grafiosch.repository;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.HashSet;
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
import grafiosch.exceptions.DataViolationException;

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
    if (user.getMostPrivilegedRole() != Role.ROLE_ADMIN
        && (mailSettingForward.getIdUserRedirect() != null
            || mailSettingForward.getMessageComType().getValue() >= MailSettingForward.MAIN_ADMIN_BASE_VALUE)
        || !MailSendForwardDefaultBase.mailSendForwardDefaultMap.get(mailSettingForward.getMessageComType()).canRedirect
            && mailSettingForward.getIdUserRedirect() != null) {
      throw new SecurityException(BaseConstants.CLIENT_SECURITY_BREACH);
    }
    checkForRedirectCycle(mailSettingForward);
    return RepositoryHelper.saveOnlyAttributes(mailSettingForwardJpaRepository, mailSettingForward, existingEntity,
        updatePropertyLevelClasses);
  }

  /**
   * Ensures that the redirect chain for the given setting does not form a cycle. Redirects are kept per
   * {@code message_com_type}: each row maps {@code id_user -> id_user_redirect}. Starting from the new target this
   * walks the existing chain and rejects the save if it returns to a user already on the path. This also rejects a
   * self-redirect (target equals the origin user).
   *
   * @param mailSettingForward the setting being saved, whose {@code idUserRedirect} introduces the new edge
   * @throws DataViolationException if the redirect would create a forwarding cycle between administrators
   */
  private void checkForRedirectCycle(MailSettingForward mailSettingForward) {
    Integer current = mailSettingForward.getIdUserRedirect();
    if (current == null) {
      return;
    }
    byte comType = mailSettingForward.getMessageComType().getValue();
    Set<Integer> visited = new HashSet<>();
    visited.add(mailSettingForward.getIdUser());
    while (current != null) {
      if (!visited.add(current)) {
        throw new DataViolationException("id.user.redirect", "g.mail.redirect.cycle", null);
      }
      current = mailSettingForwardJpaRepository.findByIdUserAndMessageComType(current, comType)
          .map(MailSettingForward::getIdUserRedirect).orElse(null);
    }
  }

  @Override
  public MailSendForwardDefaultBase getMailSendForwardDefault() {
    var user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    List<ValueKeyHtmlSelectOptions> vkhsoList = new ArrayList<>();
    var isAdmin = user.getMostPrivilegedRole().equals(Role.ROLE_ADMIN);
    if (isAdmin) {
      userJpaRepository.getIdUserAndNicknameByRoleExcludeUser(Role.ROLE_ADMIN, user.getIdUser())
          .forEach(rs -> vkhsoList
              .add(new ValueKeyHtmlSelectOptions(String.valueOf(rs.getIdUser()), rs.getNickname())));
    }
    return new MailSendForwardDefaultBase(vkhsoList, isAdmin);
  }

  @Transactional
  public int delEntityWithUserId(Integer id, Integer idUser) {
    return mailSettingForwardJpaRepository.deleteByIdUserAndIdMailSettingForward(idUser, id);
  }

}
