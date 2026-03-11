package grafioschtrader.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import grafiosch.entities.TaxUpload;

public interface TaxUploadJpaRepository extends JpaRepository<TaxUpload, Integer> {

  List<TaxUpload> findByIdTaxYear(int idTaxYear);
}
