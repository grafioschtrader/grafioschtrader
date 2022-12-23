package grafioschtrader.entities;

import java.util.Date;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;
import jakarta.validation.constraints.NotNull;

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
