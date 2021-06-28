package grafioschtrader.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import grafioschtrader.connector.instrument.IFeedConnector.FeedSupport;
import grafioschtrader.dto.ValueKeyHtmlSelectOptions;
import grafioschtrader.entities.Stockexchange;
import grafioschtrader.repository.AssetclassJpaRepository;
import grafioschtrader.repository.GlobalparametersJpaRepository;
import grafioschtrader.repository.SecurityJpaRepository;
import grafioschtrader.repository.StockexchangeJpaRepository;

@Service
public class MultipleRequestToOneService {

  @Autowired
  private AssetclassJpaRepository assetclassJpaRepository;

  @Autowired
  private GlobalparametersJpaRepository globalparametersJpaRepository;

  @Autowired
  private StockexchangeJpaRepository stockexchangeJpaRepository;

  @Autowired
  private SecurityJpaRepository securityJpaRepository;

  public DataForCurrencySecuritySearch getDataForCurrencySecuritySearch() {
    return new DataForCurrencySecuritySearch(globalparametersJpaRepository.getCurrencies(),
        assetclassJpaRepository.getSubcategoryForLanguage(),
        securityJpaRepository.getAllFeedConnectorsAsKeyValue(FeedSupport.HISTORY),
        securityJpaRepository.getAllFeedConnectorsAsKeyValue(FeedSupport.INTRA),
        stockexchangeJpaRepository.getAllStockExchanges(false));
  }

  public static class DataForCurrencySecuritySearch {
    public final List<ValueKeyHtmlSelectOptions> currencies;
    public final List<ValueKeyHtmlSelectOptions> assetclasses;
    public final List<ValueKeyHtmlSelectOptions> feedConnectorsHistory;
    public final List<ValueKeyHtmlSelectOptions> feedConnectorsIntra;
    public final List<Stockexchange> stockexchanges;

    public DataForCurrencySecuritySearch(List<ValueKeyHtmlSelectOptions> currencies,
        List<ValueKeyHtmlSelectOptions> assetclasses, List<ValueKeyHtmlSelectOptions> feedConnectorsHistory,
        List<ValueKeyHtmlSelectOptions> feedConnectorsIntra, List<Stockexchange> stockexchanges) {
      this.currencies = currencies;
      this.assetclasses = assetclasses;
      this.feedConnectorsHistory = feedConnectorsHistory;
      this.feedConnectorsIntra = feedConnectorsIntra;
      this.stockexchanges = stockexchanges;
    }

  }
}
