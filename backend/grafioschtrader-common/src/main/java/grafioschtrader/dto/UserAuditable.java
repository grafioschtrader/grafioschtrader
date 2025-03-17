package grafioschtrader.dto;

import grafiosch.entities.Auditable;
import grafiosch.entities.User;

public class UserAuditable {
  public Auditable auditable;
  public User user;

  public UserAuditable(Auditable auditable, User user) {
    super();
    this.auditable = auditable;
    this.user = user;
  }

}
