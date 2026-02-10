package grafioschtrader.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import grafioschtrader.entities.GTNetSupplierDetailHist;

public interface GTNetSupplierDetailHistJpaRepository extends JpaRepository<GTNetSupplierDetailHist, Integer> {

  List<GTNetSupplierDetailHist> findByIdGtNetSupplierDetailIn(List<Integer> ids);
}
