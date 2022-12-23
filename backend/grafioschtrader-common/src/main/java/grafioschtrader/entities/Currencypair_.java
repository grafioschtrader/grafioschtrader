package grafioschtrader.entities;

import jakarta.annotation.Generated;
import jakarta.persistence.metamodel.SingularAttribute;
import jakarta.persistence.metamodel.StaticMetamodel;

@Generated(value = "org.hibernate.jpamodelgen.JPAMetaModelEntityProcessor")
@StaticMetamodel(Currencypair.class)
public abstract class Currencypair_ extends grafioschtrader.entities.Securitycurrency_ {

  public static volatile SingularAttribute<Currencypair, String> toCurrency;
  public static volatile SingularAttribute<Currencypair, String> fromCurrency;

  public static final String TO_CURRENCY = "toCurrency";
  public static final String FROM_CURRENCY = "fromCurrency";

}
