package grafioschtrader.entities;

import static jakarta.persistence.InheritanceType.JOINED;

import grafiosch.entities.BaseID;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorColumn;
import jakarta.persistence.DiscriminatorType;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Inheritance;
import jakarta.persistence.Table;

/**
 * Abstract base class for instrument identification in the GT-Network pool.
 *
 * This entity serves as the central instrument registry for GTNet data exchange. It identifies instruments
 * (securities or currency pairs) that can be shared between GTNet peers, regardless of whether they exist
 * locally in this instance's database. Each PUSH_OPEN instance maintains a single instrument pool.
 *
 * <h3>Local vs. Foreign Instruments</h3>
 * Locality is determined dynamically via JOIN to security/currencypair tables by ISIN+currency or
 * fromCurrency+toCurrency. This allows the pool to remain independent of local database state.
 * <ul>
 *   <li><b>Local:</b> When a matching entry exists in security/currencypair table (determined via JOIN),
 *       historical quotes are stored directly in the {@link Historyquote} table.</li>
 *   <li><b>Foreign:</b> When no local match exists, historical quotes are stored in {@link GTNetHistoryquote}.</li>
 * </ul>
 *
 * <h3>Related Tables</h3>
 * <ul>
 *   <li>{@link GTNetLastprice} - Intraday price data linked to this instrument</li>
 *   <li>{@link GTNetHistoryquote} - Historical price data for foreign instruments</li>
 * </ul>
 *
 * Uses JPA JOINED inheritance strategy, with discriminator values 'S' for Security and 'C' for Currencypair.
 *
 * @see GTNetInstrumentSecurity for securities (identified by ISIN + currency)
 * @see GTNetInstrumentCurrencypair for currency pairs (identified by from/to currency)
 */
@Entity
@Table(name = GTNetInstrument.TABNAME)
@Inheritance(strategy = JOINED)
@DiscriminatorColumn(name = "dtype", discriminatorType = DiscriminatorType.STRING)
@Schema(description = """
    Abstract base class for instrument identification in the GT-Network pool. Identifies instruments (securities
    or currency pairs) that can be shared between GTNet peers. Locality (whether the instrument exists locally)
    is determined via JOIN to security/currencypair tables. Extended by GTNetInstrumentSecurity (ISIN + currency)
    and GTNetInstrumentCurrencypair (from/to currency).""")
public abstract class GTNetInstrument extends BaseID<Integer> {
  public static final String TABNAME = "gt_net_instrument";

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Basic(optional = false)
  @Column(name = "id_gt_net_instrument")
  protected Integer idGtNetInstrument;

  public GTNetInstrument() {
    super();
  }

  public Integer getIdGtNetInstrument() {
    return idGtNetInstrument;
  }

  /**
   * Returns the unique key for matching this instrument across GTNet instances.
   * Subclasses must implement this to provide their identification string.
   *
   * @return unique identification string (e.g., "ISIN:CURRENCY" or "FROM:TO")
   */
  public abstract String getInstrumentKey();

  @Override
  public Integer getId() {
    return idGtNetInstrument;
  }

}
