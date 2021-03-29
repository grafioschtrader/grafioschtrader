package grafioschtrader.entities;

import java.time.LocalTime;

import javax.persistence.metamodel.SingularAttribute;
import javax.persistence.metamodel.StaticMetamodel;

@StaticMetamodel(Stockexchange.class)
public abstract class Stockexchange_ {

  public static volatile SingularAttribute<Stockexchange, Integer> idStockexchange;
  public static volatile SingularAttribute<Stockexchange, String> name;
  public static volatile SingularAttribute<Stockexchange, String> countryCode;
  public static volatile SingularAttribute<Stockexchange, Boolean> noMarketValue;
  public static volatile SingularAttribute<Stockexchange, Boolean> secondaryMarket;
  public static volatile SingularAttribute<Stockexchange, LocalTime> timeOpen;
  public static volatile SingularAttribute<Stockexchange, LocalTime> timeClose;
  public static volatile SingularAttribute<Stockexchange, String> symbol;
  public static volatile SingularAttribute<Stockexchange, String> timeZone;
  public static volatile SingularAttribute<Stockexchange, Integer> idIndexUpdCalendar;
}
