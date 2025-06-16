package grafioschtrader.entities;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/**
 * Composite primary key for security holding records that uniquely identifies holding periods.
 * 
 * <p>
 * <strong>Key Composition:</strong>
 * </p>
 * <p>
 * This embeddable key combines three essential elements to create unique holding records:
 * </p>
 * <ul>
 * <li><strong>Security Account</strong> - The specific account where the security is held</li>
 * <li><strong>Security</strong> - The particular security being held</li>
 * <li><strong>Start Date</strong> - The date when this holding period began</li>
 * </ul>
 * 
 * <p>
 * <strong>Temporal Uniqueness:</strong>
 * </p>
 * <p>
 * The inclusion of the start date in the primary key enables the system to maintain a complete historical timeline of
 * position changes. Each time a transaction changes the position size, a new holding record is created with a new start
 * date, while the previous record receives an end date.
 * </p>
 * 
 * <p>
 * <strong>Data Integrity:</strong>
 * </p>
 * <p>
 * This composite key ensures that:
 * </p>
 * <ul>
 * <li>No overlapping holding periods exist for the same security in the same account</li>
 * <li>Historical position data is preserved across all transactions</li>
 * <li>Efficient querying by account, security, or time period</li>
 * <li>Proper referential integrity for related entities</li>
 * </ul>
 * 
 * <p>
 * <strong>Usage Context:</strong>
 * </p>
 * <p>
 * This key is used exclusively by the {@link HoldSecurityaccountSecurity} entity to provide unique identification for
 * security holding records across time periods.
 * </p>
 */
@Embeddable
public class HoldSecurityaccountSecurityKey implements Serializable {
  private static final long serialVersionUID = 1L;

  /**
   * Identifier of the security account where the security is held.
   * 
   * <p>
   * This references the specific account within a portfolio where the security position exists. Multiple security
   * accounts can exist within a single portfolio, each potentially holding different securities or the same security
   * with different strategies.
   * </p>
   */
  @Column(name = "id_securitycash_account")
  private Integer idSecuritycashAccount;

  /**
   * Identifier of the security being held.
   * 
   * <p>
   * This references the specific financial instrument (stock, bond, etc.) that is being tracked in this holding record.
   * The same security can be held across multiple accounts and time periods.
   * </p>
   */
  @Column(name = "id_securitycurrency")
  private Integer idSecuritycurrency;

  /**
   * Start date of this holding period.
   * 
   * <p>
   * This date marks when this particular holding period began, typically corresponding to a transaction date that
   * changed the position size. The date enables temporal querying and ensures unique identification of holding periods
   * over time.
   * </p>
   * 
   * <p>
   * <strong>Important:</strong> This is the start date only. The end date is stored separately in the main entity to
   * maintain proper temporal relationships between consecutive holding periods.
   * </p>
   */
  @Column(name = "from_hold_date")
  private LocalDate fromHoldDate;

  public HoldSecurityaccountSecurityKey() {
  }

  /**
   * Creates a new composite key with all required components.
   * 
   * @param idSecuritycashAccount the security account identifier
   * @param idSecuritycurrency    the security identifier
   * @param fromHoldDate          the start date of this holding period
   */
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
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
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
