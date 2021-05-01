package grafioschtrader.repository;

import java.lang.annotation.Annotation;
import java.util.List;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;

import grafioschtrader.GlobalConstants;
import grafioschtrader.entities.MailInbox;
import grafioschtrader.entities.MailSendbox;
import grafioschtrader.entities.User;

public class MailSendboxJpaRepositoryImpl extends MailInOutService<MailSendbox>
    implements MailSendboxJpaRepositoryCustom {

  @Autowired
  private MailSendboxJpaRepository mailSendboxJpaRepository;

  @Autowired
  private MailInboxJpaRepository mailInboxJpaRepository;

  @Override
  public List<MailSendbox> getMailSendboxByUser() {
    final User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    List<MailSendbox> mailSendboxList = mailSendboxJpaRepository.findByIdUserFromOrderBySendTimeAsc(user.getIdUser());
    connectRoleNameToMail(mailSendboxList);
    return mailSendboxList;
  }

  @Override
  public MailSendbox sendInternalMail(Integer idUserFrom, Integer idUserTo, String roleName, String subject,
      String message) {
    MailSendbox mailSendbox = new MailSendbox(idUserFrom, idUserTo, roleName, subject, message);
    setIdRoleToFromRoleName(mailSendbox);
    mailSendbox = mailSendboxJpaRepository.save(mailSendbox);
    MailInbox mailInbox = new MailInbox(idUserFrom, idUserTo, roleName, subject, message);
    mailInbox.setIdRoleTo(mailSendbox.getIdRoleTo());
    mailInboxJpaRepository.save(mailInbox);
    return mailSendbox;
  }

  @Override
  public MailSendbox saveOnlyAttributes(MailSendbox entity, MailSendbox existingEntity,
      Set<Class<? extends Annotation>> updatePropertyLevelClasses) throws Exception {
    MailSendbox mailSendbox = sendInternalMail(entity.getIdUserFrom(), entity.getIdUserTo(), entity.getRoleNameTo(),
        entity.getSubject(), entity.getMessage());
    connectRoleNameToMail(mailSendbox);
    return mailSendbox;
  }

  @Override
  public void deleteByIdMailInOut(Integer idMailInOut) {
    final User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    int number = mailSendboxJpaRepository.deleteByIdMailInOutAndIdUserTo(idMailInOut, user.getIdUser());
    if (number == 0) {
      throw new SecurityException(GlobalConstants.CLIENT_SECURITY_BREACH);
    }
  }

}
