package grafiosch.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import grafiosch.entities.Globalparameters;

public interface GlobalparametersJpaRepository
    extends JpaRepository<Globalparameters, String>, GlobalparametersJpaRepositoryCustom {

  /*
   * @Override
   *
   * @Cacheable(value = "globalparameters") Globalparameters getOne(String id);
   */

}
