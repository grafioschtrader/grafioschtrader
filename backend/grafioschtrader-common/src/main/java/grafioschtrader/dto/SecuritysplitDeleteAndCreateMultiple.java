package grafioschtrader.dto;

import grafioschtrader.entities.Securitysplit;
import jakarta.validation.Valid;

public class SecuritysplitDeleteAndCreateMultiple extends DeleteAndCreateMultiple {

  @Valid
  private Securitysplit[] securitysplits;

  public Securitysplit[] getSecuritysplits() {
    return (securitysplits == null) ? new Securitysplit[0] : securitysplits;
  }

  public void setSecuritysplits(final Securitysplit[] securitysplits) {
    this.securitysplits = securitysplits;
  }

}
