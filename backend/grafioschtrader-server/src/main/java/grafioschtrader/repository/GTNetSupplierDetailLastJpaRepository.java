package grafioschtrader.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import grafioschtrader.entities.GTNetSupplierDetailLast;

public interface GTNetSupplierDetailLastJpaRepository extends JpaRepository<GTNetSupplierDetailLast, Integer> {

  List<GTNetSupplierDetailLast> findByIdGtNetSupplierDetailIn(List<Integer> ids);
}
