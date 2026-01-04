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
 * locally in this instance's database.
 *
 * <h3>Local vs. Foreign Instruments</h3>
 * <ul>
 *   <li><b>Local:</b> When {@code idSecuritycurrency} is set, the instrument exists in the local database.
 *       Historical quotes are stored directly in the {@link Historyquote} table.</li>
 *   <li><b>Foreign:</b> When {@code idSecuritycurrency} is null, the instrument only exists in the GTNet pool.
 *       Historical quotes are stored in {@link GTNetHistoryquote} table.</li>
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
    or currency pairs) that can be shared between GTNet peers. When idSecuritycurrency is set, the instrument
    exists locally and historical quotes go to the historyquote table. When null, quotes go to gt_net_historyquote.
    Extended by GTNetInstrumentSecurity (ISIN + currency) and GTNetInstrumentCurrencypair (from/to currency).""")
public abstract class GTNetInstrument extends BaseID<Integer> {
  public static final String TABNAME = "gt_net_instrument";

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Basic(optional = false)
  @Column(name = "id_gt_net_instrument")
  protected Integer idGtNetInstrument;

  @Schema(description = "Reference to the GTNet server that owns this instrument pool entry")
  @Column(name = "id_gt_net", nullable = false)
  protected Integer idGtNet;

  @Schema(description = """
      Reference to the local securitycurrency entity. When set, this instrument exists in the local database
      and historical quotes should be stored in the historyquote table. When null, this is a foreign instrument
      and quotes are stored in gt_net_historyquote.""")
  @Column(name = "id_securitycurrency")
  protected Integer idSecuritycurrency;

  public GTNetInstrument() {
    super();
  }

  public Integer getIdGtNetInstrument() {
    return idGtNetInstrument;
  }

  public Integer getIdGtNet() {
    return idGtNet;
  }

  public void setIdGtNet(Integer idGtNet) {
    this.idGtNet = idGtNet;
  }

  public Integer getIdSecuritycurrency() {
    return idSecuritycurrency;
  }

  public void setIdSecuritycurrency(Integer idSecuritycurrency) {
    this.idSecuritycurrency = idSecuritycurrency;
  }

  /**
   * Returns true if this instrument exists in the local database.
   *
   * Local instruments have their historical quotes stored in the standard historyquote table.
   * Foreign instruments (isLocalInstrument() == false) use gt_net_historyquote instead.
   *
   * @return true if idSecuritycurrency is set, false otherwise
   */
  public boolean isLocalInstrument() {
    return idSecuritycurrency != null;
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
