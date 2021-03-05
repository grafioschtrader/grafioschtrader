package grafioschtrader.entities;

import java.util.Date;

import javax.annotation.Generated;
import javax.persistence.metamodel.SingularAttribute;
import javax.persistence.metamodel.StaticMetamodel;

@Generated(value = "org.hibernate.jpamodelgen.JPAMetaModelEntityProcessor")
@StaticMetamodel(Security.class)
public abstract class Security_ extends grafioschtrader.entities.Securitycurrency_ {

  public static volatile SingularAttribute<Security, Long> sVolume;
  public static volatile SingularAttribute<Security, Date> activeFromDate;
  public static volatile SingularAttribute<Security, Date> activeToDate;
  public static volatile SingularAttribute<Security, Stockexchange> stockexchange;
  public static volatile SingularAttribute<Security, String> name;
  public static volatile SingularAttribute<Security, String> tickerSymbol;
  public static volatile SingularAttribute<Security, Boolean> shortSecurity;
  public static volatile SingularAttribute<Security, String> currency;
  public static volatile SingularAttribute<Security, String> productLink;
  public static volatile SingularAttribute<Security, Assetclass> assetClass;
  public static volatile SingularAttribute<Security, String> isin;
  public static volatile SingularAttribute<Security, Integer> idTenantPrivate;
  public static volatile SingularAttribute<Security, Integer> idLinkSecuritycurrency;
  public static volatile SingularAttribute<Security, Byte> distributionFrequency;
  public static volatile SingularAttribute<Security, String> formulaPrices;
  public static volatile SingularAttribute<Security, Integer> denomination;
  public static volatile SingularAttribute<Security, String> idConnectorDividend;
  public static volatile SingularAttribute<Security, String> urlDividendExtend;
  public static volatile SingularAttribute<Security, String> dividendCurrency;
  public static volatile SingularAttribute<Security, Short> retryDividendLoad;
  public static volatile SingularAttribute<Security, String> idConnectorSplit;
  public static volatile SingularAttribute<Security, String> urlSpltExtend;
  public static volatile SingularAttribute<Security, Short> retrySplitLoad;
}
