package grafioschtrader.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import grafioschtrader.entities.Globalparameters;

public interface GlobalparametersJpaRepository
    extends JpaRepository<Globalparameters, String>, GlobalparametersJpaRepositoryCustom {

  /*
   * @Override
   *
   * @Cacheable(value = "globalparameters") Globalparameters getOne(String id);
   */

}
