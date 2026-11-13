package grafioschtrader.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import grafiosch.dto.ValueKeyHtmlSelectOptions;
import grafioschtrader.connector.instrument.IFeedConnector.FeedSupport;
import grafioschtrader.entities.Stockexchange;
import grafioschtrader.repository.AssetclassJpaRepository;
import grafioschtrader.repository.SecurityJpaRepository;
import grafioschtrader.repository.StockexchangeJpaRepository;
import grafioschtrader.search.AdvancedSearchField;

@Service
public class MultipleRequestToOneService {

  @Autowired
  private AssetclassJpaRepository assetclassJpaRepository;

  @Autowired
  private GlobalparametersService globalparametersService;

  @Autowired
  private StockexchangeJpaRepository stockexchangeJpaRepository;

  @Autowired
  private SecurityJpaRepository securityJpaRepository;

  @Autowired
  private AdvancedSearchFieldService advancedSearchFieldService;

  public DataForCurrencySecuritySearch getDataForCurrencySecuritySearch() {
    return new DataForCurrencySecuritySearch(globalparametersService.getCurrencies(),
        assetclassJpaRepository.getSubcategoryForLanguage(),
        securityJpaRepository.getAllFeedConnectorsAsKeyValue(FeedSupport.FS_HISTORY),
        securityJpaRepository.getAllFeedConnectorsAsKeyValue(FeedSupport.FS_INTRA),
        stockexchangeJpaRepository.getAllStockExchanges(false), advancedSearchFieldService.getFields());
  }

  public static class DataForCurrencySecuritySearch {
    public final List<ValueKeyHtmlSelectOptions> currencies;
    public final List<ValueKeyHtmlSelectOptions> assetclasses;
    public final List<ValueKeyHtmlSelectOptions> feedConnectorsHistory;
    public final List<ValueKeyHtmlSelectOptions> feedConnectorsIntra;
    public final List<Stockexchange> stockexchanges;
    /** Fields the user can choose in a criterion of the advanced search. */
    public final List<AdvancedSearchField> advancedSearchFields;

    public DataForCurrencySecuritySearch(List<ValueKeyHtmlSelectOptions> currencies,
        List<ValueKeyHtmlSelectOptions> assetclasses, List<ValueKeyHtmlSelectOptions> feedConnectorsHistory,
        List<ValueKeyHtmlSelectOptions> feedConnectorsIntra, List<Stockexchange> stockexchanges,
        List<AdvancedSearchField> advancedSearchFields) {
      this.advancedSearchFields = advancedSearchFields;
      this.currencies = currencies;
      this.assetclasses = assetclasses;
      this.feedConnectorsHistory = feedConnectorsHistory;
      this.feedConnectorsIntra = feedConnectorsIntra;
      this.stockexchanges = stockexchanges;
    }

  }
}
