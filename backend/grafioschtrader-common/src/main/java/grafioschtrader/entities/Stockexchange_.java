package grafioschtrader.entities;

import java.time.LocalDateTime;
import java.time.LocalTime;

import javax.annotation.Generated;
import javax.persistence.metamodel.SingularAttribute;
import javax.persistence.metamodel.StaticMetamodel;

@Generated(value = "org.hibernate.jpamodelgen.JPAMetaModelEntityProcessor")
@StaticMetamodel(Stockexchange.class)
public abstract class Stockexchange_ extends grafioschtrader.entities.Auditable_ {

  public static volatile SingularAttribute<Stockexchange, Boolean> secondaryMarket;
  public static volatile SingularAttribute<Stockexchange, Integer> idStockexchange;
  public static volatile SingularAttribute<Stockexchange, String> mic;
  public static volatile SingularAttribute<Stockexchange, String> countryCode;
  public static volatile SingularAttribute<Stockexchange, String> name;
  public static volatile SingularAttribute<Stockexchange, String> timeZone;
  public static volatile SingularAttribute<Stockexchange, LocalTime> timeClose;
  public static volatile SingularAttribute<Stockexchange, LocalTime> timeOpen;
  public static volatile SingularAttribute<Stockexchange, Boolean> noMarketValue;
  public static volatile SingularAttribute<Stockexchange, Integer> idIndexUpdCalendar;
  public static volatile SingularAttribute<Stockexchange, LocalDateTime> lastDirectPriceUpdate;
  public static volatile SingularAttribute<Stockexchange, String> website;

}
