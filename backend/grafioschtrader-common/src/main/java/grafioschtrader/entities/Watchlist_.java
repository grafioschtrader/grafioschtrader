package grafioschtrader.entities;

import java.util.Date;

import javax.annotation.Generated;
import javax.persistence.metamodel.ListAttribute;
import javax.persistence.metamodel.SingularAttribute;
import javax.persistence.metamodel.StaticMetamodel;

@Generated(value = "org.hibernate.jpamodelgen.JPAMetaModelEntityProcessor")
@StaticMetamodel(Watchlist.class)
public abstract class Watchlist_ {

  public static volatile SingularAttribute<Watchlist, Integer> idTenant;
  public static volatile SingularAttribute<Watchlist, Integer> idWatchlist;
  public static volatile SingularAttribute<Watchlist, Date> lastTimestamp;
  public static volatile SingularAttribute<Watchlist, String> name;
  public static volatile ListAttribute<Watchlist, Securitycurrency<?>> securitycurrencyList;

}
