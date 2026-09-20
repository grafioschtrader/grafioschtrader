package grafioschtrader.entities;

import java.io.Serializable;

import jakarta.validation.Valid;

/** Optional assumptions used when a historical simulation has to synthesize security cash flows. */
public class SecuritySimulationMetadata implements Serializable {

  private static final long serialVersionUID = 1L;

  @Valid
  private SecurityBondTerms bondTerms;

  public SecurityBondTerms getBondTerms() {
    return bondTerms;
  }

  public void setBondTerms(SecurityBondTerms bondTerms) {
    this.bondTerms = bondTerms;
  }
}
