package grafioschtrader.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import grafioschtrader.entities.GTNet;
import grafioschtrader.entities.GTNetSupplier;

public interface GTNetSupplierJpaRepository extends JpaRepository<GTNetSupplier, Integer> {

  GTNetSupplier findByGtNet(GTNet gtNet);

  List<GTNetSupplier> findByGtNet_IdGtNet(Integer idGtNet);

}
