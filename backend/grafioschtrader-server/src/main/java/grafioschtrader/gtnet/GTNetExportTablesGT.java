package grafioschtrader.gtnet;

import java.util.Arrays;
import java.util.stream.Stream;

import org.springframework.stereotype.Component;

import grafiosch.entities.GTNetExchangeLog;
import grafiosch.entities.GTNetSupplierDetail;
import grafiosch.gtnet.GTNetExportTables;
import grafiosch.repository.GTNetJpaRepositoryImpl;
import grafioschtrader.entities.GTNetHistoryquote;
import grafioschtrader.entities.GTNetInstrument;
import grafioschtrader.entities.GTNetInstrumentCurrencypair;
import grafioschtrader.entities.GTNetInstrumentSecurity;
import grafioschtrader.entities.GTNetLastprice;
import grafioschtrader.entities.GTNetSecurityImpGap;
import grafioschtrader.entities.GTNetSecurityImpHead;
import grafioschtrader.entities.GTNetSecurityImpPos;
import grafioschtrader.entities.GTNetSupplierDetailHist;
import grafioschtrader.entities.GTNetSupplierDetailLast;

/**
 * Widens the library GTNet export to the tables Grafioschtrader adds on top of it: the price pool, the supplier detail
 * subclasses and the security-import staging tables.
 *
 * <p>
 * The marker stays {@code -- GTNET_EXPORT_V1}, so a file exported by an earlier version still imports and a file
 * exported now is byte-for-byte what the former application-side resource produced.
 * </p>
 */
@Component
public class GTNetExportTablesGT implements GTNetExportTables {

  private static final String EXPORT_HEADER = "-- GTNET_EXPORT_V1";

  /** Tables that are deleted during import but not exported (data is rebuilt by background jobs). */
  private static final String[] DELETE_ONLY_TABLES = { GTNetSecurityImpGap.TABNAME, GTNetSecurityImpPos.TABNAME,
      GTNetSecurityImpHead.TABNAME, GTNetHistoryquote.TABNAME, GTNetLastprice.TABNAME, GTNetInstrumentSecurity.TABNAME,
      GTNetInstrumentCurrencypair.TABNAME, GTNetInstrument.TABNAME, GTNetSupplierDetailHist.TABNAME,
      GTNetSupplierDetailLast.TABNAME, GTNetSupplierDetail.TABNAME };

  /**
   * App tables that are exported and deleted, combined with the base tables. {@code gt_net_supplier_detail} is taken
   * out of the base list because here it is a rebuildable parent of the hist/last subclasses and therefore belongs to
   * {@link #DELETE_ONLY_TABLES}.
   */
  private static final String[] EXPORT_AND_DELETE_TABLES = Stream
      .concat(Stream.of(GTNetExchangeLog.TABNAME), Arrays.stream(GTNetJpaRepositoryImpl.GTNET_BASE_TABLES_DELETE_ORDER)
          .filter(t -> !GTNetSupplierDetail.TABNAME.equals(t)))
      .toArray(String[]::new);

  @Override
  public String header() {
    return EXPORT_HEADER;
  }

  @Override
  public String[] deleteOnlyTables() {
    return DELETE_ONLY_TABLES;
  }

  @Override
  public String[] exportAndDeleteTables() {
    return EXPORT_AND_DELETE_TABLES;
  }
}
