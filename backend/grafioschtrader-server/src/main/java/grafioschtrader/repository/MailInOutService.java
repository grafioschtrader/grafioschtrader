package grafioschtrader.repository;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;

import grafioschtrader.entities.MailInOut;
import grafioschtrader.entities.Role;

public abstract class MailInOutService<T extends MailInOut> extends BaseRepositoryImpl<T> {

  @Autowired
  RoleJpaRepository roleJpaRepository;

  protected void connectRoleNameToMail(List<T> mailInOut) {
    List<T> mailsWithRoleList = mailInOut.stream().filter(mailInbox -> mailInbox.getIdRoleTo() != null)
        .collect(Collectors.toList());
    if (!mailsWithRoleList.isEmpty()) {
      List<Role> roles = roleJpaRepository.findAll();
      mailsWithRoleList.forEach(m -> roles.stream().filter(r -> r.getIdRole().equals(m.getIdRoleTo())).findFirst()
          .ifPresent(r -> m.setRoleNameTo(r.getRolename())));
    }
  }

  protected void connectRoleNameToMail(T mailInOut) {
    if (mailInOut.getIdRoleTo() != null) {
      List<Role> roles = roleJpaRepository.findAll();
      roles.stream().filter(r -> r.getIdRole().equals(mailInOut.getIdRoleTo())).findFirst()
          .ifPresent(r -> mailInOut.setRoleNameTo(r.getRolename()));
    }
  }

  protected void setIdRoleToFromRoleName(T mailInOut) {
    if (mailInOut.getRoleNameTo() != null) {
      Role role = roleJpaRepository.findByRolename(mailInOut.getRoleNameTo());
      mailInOut.setIdRoleTo(role.getIdRole());
    }
  }

}
