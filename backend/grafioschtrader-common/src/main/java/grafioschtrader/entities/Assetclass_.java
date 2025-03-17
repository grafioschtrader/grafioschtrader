package grafioschtrader.entities;

import grafiosch.entities.MultilanguageString;
import jakarta.annotation.Generated;
import jakarta.persistence.metamodel.SingularAttribute;
import jakarta.persistence.metamodel.StaticMetamodel;

@Generated(value = "org.hibernate.jpamodelgen.JPAMetaModelEntityProcessor")
@StaticMetamodel(Assetclass.class)
public abstract class Assetclass_ extends grafiosch.entities.Auditable_ {

  public static volatile SingularAttribute<Assetclass, Byte> categoryType;
  public static volatile SingularAttribute<Assetclass, MultilanguageString> subCategoryNLS;
  public static volatile SingularAttribute<Assetclass, Integer> idAssetClass;
  public static volatile SingularAttribute<Assetclass, Byte> specialInvestmentInstrument;

}
