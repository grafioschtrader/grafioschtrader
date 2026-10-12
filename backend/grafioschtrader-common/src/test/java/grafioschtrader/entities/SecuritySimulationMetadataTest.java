package grafioschtrader.entities;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import grafioschtrader.types.AssetclassType;
import grafioschtrader.types.DistributionFrequency;
import grafioschtrader.types.SpecialInvestmentInstruments;

class SecuritySimulationMetadataTest {

  @Test
  void directBondKeepsIssuerAndSimulationMetadata() {
    Security security = security(AssetclassType.FIXED_INCOME, SpecialInvestmentInstruments.DIRECT_INVESTMENT);

    security.clearProperties();

    assertThat(security.getIssuerCountry()).isEqualTo("CH");
    assertThat(security.getSimulationMetadata()).isNotNull();
  }

  @Test
  void nonBondDropsSimulationMetadataButKeepsIssuer() {
    Security security = security(AssetclassType.EQUITIES, SpecialInvestmentInstruments.DIRECT_INVESTMENT);

    security.clearProperties();

    assertThat(security.getIssuerCountry()).isEqualTo("CH");
    assertThat(security.getSimulationMetadata()).isNull();
  }

  @Test
  void issuerlessInstrumentDropsIssuerAndSimulationMetadata() {
    Security security = security(AssetclassType.EQUITIES, SpecialInvestmentInstruments.CFD);

    security.clearProperties();

    assertThat(security.getIssuerCountry()).isNull();
    assertThat(security.getSimulationMetadata()).isNull();
  }

  @Test
  void nonInvestableIndexDropsDistributionFrequency() {
    Security index = security(AssetclassType.EQUITIES, SpecialInvestmentInstruments.NON_INVESTABLE_INDICES);
    index.setDistributionFrequency(DistributionFrequency.DF_AD_HOC);
    Security stock = security(AssetclassType.EQUITIES, SpecialInvestmentInstruments.DIRECT_INVESTMENT);
    stock.setDistributionFrequency(DistributionFrequency.DF_YEAR);

    index.clearProperties();
    stock.clearProperties();

    assertThat(index.getDistributionFrequency()).isEqualTo(DistributionFrequency.DF_NONE);
    assertThat(stock.getDistributionFrequency()).isEqualTo(DistributionFrequency.DF_YEAR);
  }

  private Security security(AssetclassType categoryType, SpecialInvestmentInstruments instrument) {
    Assetclass assetclass = new Assetclass();
    assetclass.setCategoryType(categoryType);
    assetclass.setSpecialInvestmentInstrument(instrument);
    Stockexchange stockexchange = new Stockexchange();
    stockexchange.setNoMarketValue(false);
    Security security = new Security();
    security.setAssetClass(assetclass);
    security.setStockexchange(stockexchange);
    security.setLeverageFactor(1);
    security.setIssuerCountry("ch");
    SecuritySimulationMetadata metadata = new SecuritySimulationMetadata();
    metadata.setBondTerms(new SecurityBondTerms());
    security.setSimulationMetadata(metadata);
    return security;
  }
}
