package grafioschtrader.dto;

import grafiosch.entities.Auditable;
import grafiosch.entities.User;
import grafioschtrader.entities.Currencypair;
import grafioschtrader.entities.Security;

public class UserAuditable {
  public Auditable auditable;
  public User user;

  public UserAuditable(Auditable auditable, User user) {
    super();
    this.auditable = auditable;
    this.user = user;
  }

  /**
   * Name of the daily CUD limit key that a bulk operation on the price data of this instrument consumes. Such an
   * operation is accounted as one edit of the instrument itself, and a security and a currency pair have separate
   * budgets, so the key follows the actual type of the parent.
   *
   * <p>
   * The test is {@code instanceof} rather than {@code getClass().getSimpleName()} on purpose: a currency pair is
   * resolved through {@code getReferenceById}, which yields a Hibernate proxy whose simple name is not
   * {@code Currencypair}. A proxy is still an instance of the entity it stands for.
   * </p>
   *
   * @return {@code Security} or {@code Currencypair}
   */
  public String getInstrumentEntityName() {
    return auditable instanceof Security ? Security.class.getSimpleName() : Currencypair.class.getSimpleName();
  }
}
