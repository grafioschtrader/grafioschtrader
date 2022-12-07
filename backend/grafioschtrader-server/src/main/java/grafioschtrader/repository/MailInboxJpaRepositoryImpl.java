package grafioschtrader.repository;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;

import grafioschtrader.GlobalConstants;
import grafioschtrader.entities.MailInbox;
import grafioschtrader.entities.User;

public class MailInboxJpaRepositoryImpl extends MailInOutService<MailInbox> implements MailInboxJpaRepositoryCustom {

  @Autowired
  MailInboxJpaRepository mailInboxJpaRepository;

  @Override
  public List<MailInbox> getMailInboxByUser() {
    final User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    List<MailInbox> mailInboxList = mailInboxJpaRepository.findByUserOrGroup(user.getIdUser());
    connectRoleNameToMail(mailInboxList);
    return mailInboxList;
  }

  @Override
  public void deleteByIdMailInOut(Integer idMailInOut) {
    final User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    int number = mailInboxJpaRepository.deleteByIdMailInOutAndIdUserTo(idMailInOut, user.getIdUser());
    if (number == 0) {
      throw new SecurityException(GlobalConstants.CLIENT_SECURITY_BREACH);
    }
  }

  @Override
  public MailInbox markForRead(Integer idMailInOut) {
    final User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    final MailInbox mailInbox = mailInboxJpaRepository.getReferenceById(idMailInOut);
    if (mailInbox == null || mailInbox.getIdRoleTo() != null
        && user.getRoles().stream().filter(r -> r.getIdRole().equals(mailInbox.getIdRoleTo())).findFirst().isEmpty()) {
      throw new SecurityException(GlobalConstants.CLIENT_SECURITY_BREACH);
    }
    mailInbox.setHasBeenRead(true);
    MailInbox mailInboxSaved = mailInboxJpaRepository.save(mailInbox);
    connectRoleNameToMail(mailInboxSaved);
    return mailInboxSaved;
  }

}
