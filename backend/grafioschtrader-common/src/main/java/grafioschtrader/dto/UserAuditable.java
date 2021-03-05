package grafioschtrader.dto;

import grafioschtrader.entities.Auditable;
import grafioschtrader.entities.User;

public class UserAuditable {
  public Auditable auditable;
  public User user;

  public UserAuditable(Auditable auditable, User user) {
    super();
    this.auditable = auditable;
    this.user = user;
  }

}
