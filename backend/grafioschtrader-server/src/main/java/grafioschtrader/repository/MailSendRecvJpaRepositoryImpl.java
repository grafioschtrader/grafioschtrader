package grafioschtrader.repository;

import java.lang.annotation.Annotation;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;

import grafioschtrader.GlobalConstants;
import grafioschtrader.dto.MailInboxWithSend;
import grafioschtrader.entities.MailSendRecv;
import grafioschtrader.entities.Role;
import grafioschtrader.entities.User;
import grafioschtrader.repository.MailSendRecvJpaRepository.CountRoleSend;
import grafioschtrader.types.ReplyToRolePrivateType;
import grafioschtrader.types.SendRecvType;

public class MailSendRecvJpaRepositoryImpl implements MailSendRecvJpaRepositoryCustom {

  @Autowired
  private MailSendRecvJpaRepository mailSendRecvJpaRepository;

  @Autowired
  private RoleJpaRepository roleJpaRepository;

  @Autowired
  private MailSendRecvReadDelJpaRepository mailSendRecvReadDelJpaRepository;

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
    connectRoleNameToMail(mailSendRecv);
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

  @Override
  public Integer sendInternalMail(Integer idUserFrom, Integer idUserTo, String subject, String message) {
    return this.sendInternalMail(idUserFrom, idUserTo, null, subject, message, null,
        ReplyToRolePrivateType.REPLY_NORMAL);

  }

  @Override
  public Integer sendInternalMail(Integer idUserFrom, Integer idUserTo, String roleName, String subject, String message,
      Integer idReplyToLocal, ReplyToRolePrivateType replyToRolePrivate) {
    return saveInternalMail(idUserFrom, idUserTo, roleName, subject, message, idReplyToLocal, replyToRolePrivate)
        .getIdMailSendRecv();
  }

  private MailSendRecv saveInternalMail(Integer idUserFrom, Integer idUserTo, String roleName, String subject,
      String message, Integer idReplyToLocal, ReplyToRolePrivateType replyToRolePrivate) {
    MailSendRecv mailSendRecvS = new MailSendRecv(SendRecvType.SEND, idUserFrom, idUserTo, roleName, subject, message,
        idReplyToLocal, replyToRolePrivate);
    setIdRoleToFromRoleName(mailSendRecvS);
    mailSendRecvS = mailSendRecvJpaRepository.save(mailSendRecvS);
    MailSendRecv mailSendRecvR = new MailSendRecv(SendRecvType.RECEIVE, idUserFrom, idUserTo, roleName, subject,
        message, mailSendRecvS.getIdReplyToLocal() == null ? mailSendRecvS.getIdMailSendRecv()
            : mailSendRecvS.getIdReplyToLocal(),
        replyToRolePrivate);
    mailSendRecvR.setIdRoleTo(mailSendRecvS.getIdRoleTo());
    return mailSendRecvJpaRepository.save(mailSendRecvR);

  }

  @Override
  public MailSendRecv saveOnlyAttributes(MailSendRecv mailSendRecv, MailSendRecv existingMailSendRecv,
      Set<Class<? extends Annotation>> updatePropertyLevelClasses) throws Exception {
    MailSendRecv mailSendRecvS = saveInternalMail(mailSendRecv.getIdUserFrom(), mailSendRecv.getIdUserTo(),
        mailSendRecv.getRoleNameTo(), mailSendRecv.getSubject(), mailSendRecv.getMessage(),
        mailSendRecv.getIdReplyToLocal(), isRoleReplySend(mailSendRecv));
    connectRoleNameToMail(mailSendRecvS);
    return mailSendRecvS;
  }

  private ReplyToRolePrivateType isRoleReplySend(MailSendRecv mailSendRecv) {
    if (mailSendRecv.getIdReplyToLocal() != null && (mailSendRecv.getReplyToRolePrivate() != null
        && mailSendRecv.getReplyToRolePrivate() != ReplyToRolePrivateType.REPLY_IS_PRIVATE
        || mailSendRecv.getReplyToRolePrivate() == null)) {
      Optional<MailSendRecv> groupMsr = mailSendRecvJpaRepository.findById(mailSendRecv.getIdReplyToLocal());
      if (groupMsr.isPresent() && groupMsr.get().getIdRoleTo() != null) {
        final User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
        return user.hasIdRole(groupMsr.get().getIdRoleTo()) ? ReplyToRolePrivateType.REPLY_AS_ROLE
            : mailSendRecv.getReplyToRolePrivate();
      }
    }
    return mailSendRecv.getReplyToRolePrivate();
  }

  @Override
  public Set<Class<? extends Annotation>> getUpdatePropertyLevels(MailSendRecv existingEntity) {
    // TODO Auto-generated method stub
    return null;
  }

  private void connectRoleNameToMail(MailSendRecv mailSendRecv) {
    if (mailSendRecv.getIdRoleTo() != null) {
      List<Role> roles = roleJpaRepository.findAll();
      roles.stream().filter(r -> r.getIdRole().equals(mailSendRecv.getIdRoleTo())).findFirst()
          .ifPresent(r -> mailSendRecv.setRoleNameTo(r.getRolename()));
    }
  }

  private void setIdRoleToFromRoleName(MailSendRecv mailSendRecv) {
    if (mailSendRecv.getRoleNameTo() != null) {
      Role role = roleJpaRepository.findByRolename(mailSendRecv.getRoleNameTo());
      mailSendRecv.setIdRoleTo(role.getIdRole());
    }
  }

}
