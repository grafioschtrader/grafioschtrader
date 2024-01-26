package grafioschtrader.repository;

import java.lang.annotation.Annotation;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;

import grafioschtrader.GlobalConstants;
import grafioschtrader.dto.MailInboxWithSend;
import grafioschtrader.entities.MailSendRecv;
import grafioschtrader.entities.User;
import grafioschtrader.repository.MailSendRecvJpaRepository.CountRoleSend;
import grafioschtrader.service.SendMailInternalExternalService;
import grafioschtrader.types.SendRecvType;

public class MailSendRecvJpaRepositoryImpl implements MailSendRecvJpaRepositoryCustom {

  @Autowired
  private MailSendRecvJpaRepository mailSendRecvJpaRepository;

  @Autowired
  private MailSendRecvReadDelJpaRepository mailSendRecvReadDelJpaRepository;

  @Autowired
  private SendMailInternalExternalService sendMailInternalExternalService;

  @Override
  public MailInboxWithSend getMailsByUserOrRole() {
    final User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    MailInboxWithSend mws = new MailInboxWithSend(mailSendRecvJpaRepository.findByUserOrGroup(user.getIdUser()),
        mailSendRecvJpaRepository.countRoleSend(user.getIdUser()).stream()
            .collect(Collectors.toMap(CountRoleSend::getIdReplyToLocal, CountRoleSend::getNumberOfAnswer)));
    return mws;
  }

  @Override
  public MailSendRecv markForRead(Integer idMailSendRecv) {
    final User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    mailSendRecvReadDelJpaRepository.markForRead(idMailSendRecv, user.getIdUser());
    MailSendRecv mailSendRecv = mailSendRecvJpaRepository.getReferenceById(idMailSendRecv);
    sendMailInternalExternalService.connectRoleNameToMail(mailSendRecv);
    mailSendRecv.setHasBeenRead(true);
    return mailSendRecv;
  }

  @Override
  public void hideDeleteResource(Integer idMailSendRecv) {
    Optional<MailSendRecv> msrOpt = mailSendRecvJpaRepository.findById(idMailSendRecv);
    if (msrOpt.isEmpty()) {
      throw new SecurityException(GlobalConstants.CLIENT_SECURITY_BREACH);
    }
    final User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    MailSendRecv msrTarget = msrOpt.get();
    if (msrTarget.getIdReplyToLocal() == null || msrTarget.getIdRoleTo() != null) {
      // Group header message
      if (user.getIdUser().equals(msrTarget.getIdUserFrom()) && msrTarget.getSendRecvAsType() == SendRecvType.SEND
          || user.getIdUser().equals(msrTarget.getIdUserTo())
              && msrTarget.getSendRecvAsType() == SendRecvType.RECEIVE) {
        // Group is one to one message, can be deleted
        mailSendRecvJpaRepository.deleteByIdMailSendRecvAndIdUser(idMailSendRecv, user.getIdUser());
        mailSendRecvJpaRepository.deleteByIdReplyToLocalAndIdUser(idMailSendRecv, user.getIdUser());
      } else {
        // Group is a role message, it will be marked for delete
        mailSendRecvReadDelJpaRepository.markforDelGroup(user.getIdUser(),
            msrTarget.getIdReplyToLocal() != null ? msrTarget.getIdReplyToLocal() : idMailSendRecv);
      }
    } else {
      // Non group header message
      MailSendRecv msrGrp = mailSendRecvJpaRepository.findById(msrTarget.getIdReplyToLocal()).orElse(
          mailSendRecvJpaRepository.findFirstByIdReplyToLocalOrderByIdMailSendRecv(msrTarget.getIdReplyToLocal()));
      if (msrGrp.getIdRoleTo() == null) {
        // Belongs to a group that is one to one message, target is single message
        mailSendRecvJpaRepository.deleteByIdMailSendRecvAndIdUser(idMailSendRecv, user.getIdUser());
      } else {
        // Belongs to a group is a role message
        mailSendRecvReadDelJpaRepository.markRoleSingleForDelete(idMailSendRecv, user.getIdUser());
      }
    }
  }

  /**
   * This message comes in via the REST API and needs additional validation.
   */
  @Override
  public MailSendRecv saveOnlyAttributes(MailSendRecv mailSendRecv, MailSendRecv existingMailSendRecv,
      Set<Class<? extends Annotation>> updatePropertyLevelClasses) throws Exception {
    return sendMailInternalExternalService.sendFromRESTApiMultiOrSingle(mailSendRecv);
  }


  @Override
  public Set<Class<? extends Annotation>> getUpdatePropertyLevels(MailSendRecv existingEntity) {
    return null;
  }





}
