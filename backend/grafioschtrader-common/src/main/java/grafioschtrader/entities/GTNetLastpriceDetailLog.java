package grafioschtrader.entities;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = GTNetLastpriceLog.TABNAME)
@Schema(description = """
    A log that records the changes made to the provider intraday data.
    This gives us a history of the changes made to the last prices.""")
public class GTNetLastpriceDetailLog {
  public static final String TABNAME = "gt_net_lastprice_detail_log";

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Basic(optional = false)
  @Column(name = "id_gt_net_lastprice_detail_log")
  private Integer idGtNetLastpriceDetailLog;

  @Schema(description = "Reference to GTNetLastpriceLog")
  @Column(name = "id_gt_net_lastprice_log")
  private Integer idGtNetLastpriceLog;

  @Schema(description = "Reference to GTNetLastprice")
  @Column(name = "id_net_lastprice")
  protected Integer idNetLastprice;

  @Schema(description = "The last indraday price which came with this request")
  @Column(name = "last")
  protected Double last;

}
