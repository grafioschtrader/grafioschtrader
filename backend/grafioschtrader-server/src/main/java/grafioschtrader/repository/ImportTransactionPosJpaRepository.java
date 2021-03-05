package grafioschtrader.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import grafioschtrader.entities.ImportTransactionPos;

public interface ImportTransactionPosJpaRepository
    extends JpaRepository<ImportTransactionPos, Integer>, ImportTransactionPosJpaRepositoryCustom {

  List<ImportTransactionPos> findByIdTransactionHeadAndIdTenant(Integer idTransactionHead, Integer idTenant);

  ImportTransactionPos findByIdTransactionPosAndIdTenant(Integer idTransactionPos, Integer idTenant);

}
