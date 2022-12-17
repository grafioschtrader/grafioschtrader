package grafioschtrader.entities;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public class HoldSecurityaccountSecurityKey implements Serializable {
  private static final long serialVersionUID = 1L;

  @Column(name = "id_securitycash_account")
  private Integer idSecuritycashAccount;

  @Column(name = "id_securitycurrency")
  private Integer idSecuritycurrency;

  @Column(name = "from_hold_date")
  private LocalDate fromHoldDate;

  public HoldSecurityaccountSecurityKey() {
  }

  public HoldSecurityaccountSecurityKey(Integer idSecuritycashAccount, Integer idSecuritycurrency,
      LocalDate fromHoldDate) {
    this.idSecuritycashAccount = idSecuritycashAccount;
    this.idSecuritycurrency = idSecuritycurrency;
    this.fromHoldDate = fromHoldDate;
  }

  public Integer getIdSecuritycurrency() {
    return idSecuritycurrency;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o)
      return true;
    if (o == null || getClass() != o.getClass())
      return false;
    HoldSecurityaccountSecurityKey that = (HoldSecurityaccountSecurityKey) o;
    return Objects.equals(idSecuritycashAccount, that.idSecuritycashAccount)
        && Objects.equals(idSecuritycurrency, that.idSecuritycurrency)
        && Objects.equals(fromHoldDate, that.fromHoldDate);
  }

  @Override
  public int hashCode() {
    return Objects.hash(idSecuritycashAccount, idSecuritycurrency, fromHoldDate);
  }

  @Override
  public String toString() {
    return "HoldSecurityaccountSecurityKey [idSecuritycashAccount=" + idSecuritycashAccount + ", idSecuritycurrency="
        + idSecuritycurrency + ", fromHoldDate=" + fromHoldDate + "]";
  }

}
