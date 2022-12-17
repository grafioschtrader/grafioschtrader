package grafioschtrader.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

import grafioschtrader.entities.Securitysplit;

public class SecuritysplitDeleteAndCreateMultiple extends DeleteAndCreateMultiple {

  @Size(max = 20)
  @Valid
  private Securitysplit[] securitysplits;

  public Securitysplit[] getSecuritysplits() {
    return (securitysplits == null) ? new Securitysplit[0] : securitysplits;
  }

  public void setSecuritysplits(final Securitysplit[] securitysplits) {
    this.securitysplits = securitysplits;
  }

}
