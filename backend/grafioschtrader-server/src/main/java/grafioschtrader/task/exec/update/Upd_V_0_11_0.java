package grafioschtrader.task.exec.update;

import java.util.List;
import java.util.Optional;

import org.apache.commons.lang3.time.DateUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import grafioschtrader.common.DateHelper;
import grafioschtrader.entities.Assetclass;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.Stockexchange;
import grafioschtrader.entities.TaskDataChange;
import grafioschtrader.repository.AssetclassJpaRepository;
import grafioschtrader.repository.SecurityJpaRepository;
import grafioschtrader.repository.StockexchangeJpaRepository;
import grafioschtrader.task.ITask;
import grafioschtrader.types.AssetclassType;
import grafioschtrader.types.DistributionFrequency;
import grafioschtrader.types.SpecialInvestmentInstruments;
import grafioschtrader.types.TaskType;

@Component
public class Upd_V_0_11_0 implements ITask {

  private final Logger log = LoggerFactory.getLogger(this.getClass());

  @Autowired
  private AssetclassJpaRepository assetclassJpaRepository;

  @Autowired
  private SecurityJpaRepository securityJpaRepository;

  @Autowired
  private StockexchangeJpaRepository stockexchangeJpaRepository;

  @Override
  public TaskType getTaskType() {
    return TaskType.UPD_V_0_11_0;
  }

  @Override
  public void doWork(TaskDataChange taskDataChange) {
    Assetclass assetclass = addAssetclassStockCanada();
    addStockIndexCanada(assetclass);
    connectIndexToStockexchange();
  }

  private Assetclass addAssetclassStockCanada() {
    Assetclass assetclass = assetclassJpaRepository.findByCategorySpecInvestmentSubCategory(
        AssetclassType.EQUITIES.getValue(), SpecialInvestmentInstruments.NON_INVESTABLE_INDICES.getValue(),
        "Stocks Canada", "en");
    if (assetclass == null) {
      assetclass = assetclassJpaRepository.save(new Assetclass(AssetclassType.EQUITIES,
          SpecialInvestmentInstruments.NON_INVESTABLE_INDICES, "Aktien Kanada", "Stocks Canada"));
    }
    return assetclass;
  }

  private void addStockIndexCanada(Assetclass assetclass) {
    Optional<Stockexchange> stockexchangeOpt = stockexchangeJpaRepository.findBySymbol("TSX");
    String isin = "XC0009693034";
    if (stockexchangeOpt.isPresent()) {
      List<Security> securities = securityJpaRepository.findByIsin("XC0009693034");
      if (securities.isEmpty()) {
        try {
          Security security = new Security("S&P/TSX Composite Index", "CAD", assetclass, stockexchangeOpt.get(),
              DateHelper.getOldestTradingDay(), DateUtils.parseDate("2029-12-31", new String[] { "yyyy-MM-dd" }),
              DistributionFrequency.DF_NONE, "OSPTX", isin);
          security.setIdConnectorHistory("gt.datafeed.yahoo");
          security.setUrlHistoryExtend("^GSPTSE");
          security.setIdConnectorIntra("gt.datafeed.yahoo");
          security.setUrlIntraExtend("^GSPTSE");
          securityJpaRepository.save(security);
        } catch (Exception e) {
          log.error("Failed to create stock index for ISIN {}", isin);
        }
      }
    }
  }

  private void connectIndexToStockexchange() {
    String[][] cv = { { "SIX", "CH0009980894", "SMI", "CHF" }, { "NYSE", "US78378X1072", "SPX", "USD" },
        { "FSX", "DE0008469008", "DAX", "EUR" }, { "PAR", "FR0003500008", "C40", "EUR" },
        { "MCE", "ES0SI0000005", "I35", "EUR" }, { "MIL", "IT0003465736", "XMB", "EUR" },
        { "LSE", "GB0001383545", "FTSE", "GBP" }, { "VIE", "AT0000999982", "A20", "EUR" },
        { "JPX", "XC0009692440", "N225", "JPY" }, { "HKEX", "HK0000004322", "HSI", "HKD" },
        { "OMX", "SE0000337842", "OMXS30", "SEK" }, { "OSE", "NO0000000021", "OBX", "NOK" },
        { "NAS", "US6311011026", "NDX", "USD" }, { "OMXC", "DK0016268840", "OMXC20", "EUR" },
        { "BER", "DE0007203275", "TDXP", "EUR" }, { "XAMS", "NL0000000107", "AEX", "EUR" },
        { "BMFBOVES", "BRIBOVINDM18", "BVSP", "BRL" }, { "TSX", "XC0009693034", "OSPTX", "CAD" },
        { "NZE", null, "NZ50", "NZD" }, { "ASX", "XC0009693018", "XAO", "AUD" } };

    for (int i = 0; i < cv.length; i++) {
      Optional<Stockexchange> stockexchangeOpt = stockexchangeJpaRepository.findBySymbol(cv[i][0]);
      if (stockexchangeOpt.isPresent() && stockexchangeOpt.get().getIdIndexUpdCalendar() == null) {
        Security security = null;
        if (cv[i][1] != null) {
          List<Security> securities = securityJpaRepository.findByIsin(cv[i][1]);
          if (securities.size() == 1 && securities.get(0).getAssetClass()
              .getSpecialInvestmentInstrument() == SpecialInvestmentInstruments.NON_INVESTABLE_INDICES) {
            security = securities.get(0);
          }
        } else {
          security = securityJpaRepository.findByTickerSymbolAndCurrency(cv[i][2], cv[i][3]);
        }
        if (security != null) {
          Stockexchange stockexchange = stockexchangeOpt.get();
          stockexchange.setIdIndexUpdCalendar(security.getIdSecuritycurrency());
          stockexchangeJpaRepository.save(stockexchange);
        }

      }
    }
  }

}
