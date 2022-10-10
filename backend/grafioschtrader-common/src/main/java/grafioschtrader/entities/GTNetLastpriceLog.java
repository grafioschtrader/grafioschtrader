package grafioschtrader.entities;

import java.util.Date;

import javax.persistence.Basic;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;
import javax.validation.constraints.NotNull;

import io.swagger.v3.oas.annotations.media.Schema;

@Entity
@Table(name = GTNetLastpriceLog.TABNAME)
@Schema(description = """
  Used to generate an evaluation of the read or written intraday price data. 
  Here it is not recorded which last prices were changed.
  Each read and write operation regarding this price data is written to this log.""") 
public class GTNetLastpriceLog {
  public static final String TABNAME = "gt_net_lastprice_log";
  
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Basic(optional = false)
  @Column(name = "id_gt_net_lastprice_log")
  private Integer idGtNetLastpriceLog;
    
  @Schema(description = "Connection to the remote domain")
  @Column(name = "id_gt_net")
  private Integer idGtNet;
    
  @Schema(description = "The log can be used by the provider as well as consumer, so this must be distinguished.")
  @Column(name = "log_as_supplier ")
  private Integer logAsSupplier;
  
  
  @Schema(description = "Time of the last instraday price update")
  @Column(name = "timestamp")
  @Temporal(TemporalType.TIMESTAMP)
  private Date timestamp;
  
  @Schema(description = "How many prices contain this request.")
  @Column(name = "lastprice_payload")
  @NotNull
  private int lastpricePayload;
  
  
  @Schema(description = "How many prices were read as the supplier's prices were newer.")
  @Column(name = "read_count")
  @NotNull
  private int readCount;
  
  @Schema(description = "How many prices were written, as prices of the request were newer.")
  @Column(name = "write_count")
  @NotNull
  private int writeCount;
  
}
