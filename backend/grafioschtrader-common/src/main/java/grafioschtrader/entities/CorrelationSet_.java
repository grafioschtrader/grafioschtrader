package grafioschtrader.entities;

import javax.persistence.metamodel.ListAttribute;
import javax.persistence.metamodel.SingularAttribute;
import javax.persistence.metamodel.StaticMetamodel;

@StaticMetamodel(Stockexchange.class)
public class CorrelationSet_ {

  public static volatile SingularAttribute<CorrelationSet, Integer> idTenant;
  public static volatile SingularAttribute<CorrelationSet, Integer> idCorrelationSet;
  public static volatile SingularAttribute<CorrelationSet, String> name;
  public static volatile SingularAttribute<CorrelationSet, String> note;
  public static volatile ListAttribute<CorrelationSet, Securitycurrency<?>> securitycurrencyList;
}
