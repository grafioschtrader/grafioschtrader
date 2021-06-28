package grafioschtrader.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import grafioschtrader.entities.ImportTransactionPos;

public interface ImportTransactionPosJpaRepository
    extends JpaRepository<ImportTransactionPos, Integer>, ImportTransactionPosJpaRepositoryCustom {

  Optional<ImportTransactionPos> findByIdTransaction(Integer idTransaction);

  List<ImportTransactionPos> findByIdTransactionHeadAndIdTenant(Integer idTransactionHead, Integer idTenant);

  ImportTransactionPos findByIdTransactionPosAndIdTenant(Integer idTransactionPos, Integer idTenant);

  @Query(nativeQuery = true)
  Integer[][] getIdTransactionPosWithPossibleTransactionByIdTransactionPos(List<Integer> idTransactionPosList);

  @Query(nativeQuery = true)
  Integer[][] getIdTransactionPosWithPossibleTransactionByIdTransactionHead(Integer idTransactionHead);

}
