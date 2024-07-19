package grafioschtrader.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import grafioschtrader.entities.UDFData;
import grafioschtrader.entities.UDFData.UDFDataKey;
import grafioschtrader.entities.UDFMetadata;
import grafioschtrader.reportviews.securitycurrency.SecuritycurrencyUDFGroup.IUDFEntityValues;


public interface UDFDataJpaRepository extends JpaRepository<UDFData, UDFDataKey>, UDFDataJpaRepositoryCustom  {
 
  @Query(nativeQuery = true)
  List<IUDFEntityValues> getUDFByIdWatchlistAndIdUserAndEntity(Integer idWatchlist, Integer idUser, String[] entities);
  
 // List<UDFEntityValues> getUdfDataResult(Integer idWatchlist, Integer idUser, String entity);
  
  
  public static interface IUDFRepository<S extends UDFMetadata>{
   
    List<S> getMetadataByUserAndEntityAndIdEntity(Integer idUser, String entity, Integer idEntity);
    List<String> getSupportedEntities();
  }
   
  
  
}
