package grafioschtrader.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import grafioschtrader.entities.Globalparameters;

@Repository
public interface GlobalparametersJpaRepository
    extends JpaRepository<Globalparameters, String>, GlobalparametersJpaRepositoryCustom {

  /*
   * @Override
   *
   * @Cacheable(value = "globalparameters") Globalparameters getOne(String id);
   */

}
