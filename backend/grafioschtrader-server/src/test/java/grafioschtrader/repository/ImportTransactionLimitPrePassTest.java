package grafioschtrader.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import grafioschtrader.entities.Cashaccount;
import grafioschtrader.entities.ImportTransactionHead;
import grafioschtrader.entities.ImportTransactionPos;

/**
 * Unit tests of the accounting behind the all-or-nothing import pre-check.
 *
 * <p>
 * The count must mirror the write loop exactly. Using the number of positions instead would fail an update-only batch
 * that creates nothing, and would double-count a connected cash-account transfer, which is two positions producing at
 * most two transactions but is processed once.
 * </p>
 */
@DisplayName("Import transaction limit pre-check accounting")
class ImportTransactionLimitPrePassTest {

  private static final Integer ID_TENANT = 7;
  private static final Integer ID_TRANSACTION_HEAD = 3;
  private static final Integer ID_OTHER_HEAD = 4;

  private final ImportTransactionHead importTransactionHead = head();

  @Test
  @DisplayName("A batch of new positions counts one transaction each")
  void newPositionsCountOne() {
    List<ImportTransactionPos> positions = List.of(pos(1, null), pos(2, null), pos(3, null));
    assertThat(count(positions, null)).isEqualTo(3);
  }

  /**
   * The reason an update-only batch can never fail: it adds nothing, so it must succeed however close the tenant is
   * to its cap.
   */
  @Test
  @DisplayName("An update-only batch sums to zero")
  void updateOnlyBatchSumsToZero() {
    List<ImportTransactionPos> positions = List.of(pos(1, 100), pos(2, 101));
    assertThat(count(positions, null)).isZero();
  }

  @Test
  @DisplayName("A connected transfer with two new sides counts two, not four")
  void connectedTransferWithTwoNewSidesCountsTwo() {
    ImportTransactionPos withdrawal = pos(1, null);
    ImportTransactionPos deposit = pos(2, null);
    assertThat(count(List.of(withdrawal, deposit), connect(withdrawal, deposit))).isEqualTo(2);
  }

  @Test
  @DisplayName("A connected transfer with one new and one existing side counts one")
  void connectedTransferWithOneNewSideCountsOne() {
    ImportTransactionPos withdrawal = pos(1, null);
    ImportTransactionPos deposit = pos(2, 100);
    assertThat(count(List.of(withdrawal, deposit), connect(withdrawal, deposit))).isEqualTo(1);
  }

  @Test
  @DisplayName("A position that is not ready produces none")
  void notReadyPositionCountsZero() {
    ImportTransactionPos notReady = pos(1, null);
    notReady.setReadyForTransaction(false);
    assertThat(count(List.of(notReady, pos(2, null)), null)).isEqualTo(1);
  }

  @Test
  @DisplayName("A position of another import head produces none")
  void foreignHeadPositionCountsZero() {
    ImportTransactionPos foreign = pos(1, null);
    foreign.setIdTransactionHead(ID_OTHER_HEAD);
    assertThat(count(List.of(foreign, pos(2, null)), null)).isEqualTo(1);
  }

  @Test
  @DisplayName("A position of another tenant produces none")
  void foreignTenantPositionCountsZero() {
    ImportTransactionPos foreign = pos(1, null);
    foreign.setCashaccount(cashaccount(ID_TENANT + 1));
    assertThat(count(List.of(foreign, pos(2, null)), null)).isEqualTo(1);
  }

  private int count(List<ImportTransactionPos> positions, Map<Integer, ImportTransactionPos> idItpMap) {
    return ImportTransactionPosJpaRepositoryImpl.countNewTransactions(positions, idItpMap, importTransactionHead,
        ID_TENANT);
  }

  private Map<Integer, ImportTransactionPos> connect(ImportTransactionPos one, ImportTransactionPos other) {
    one.setConnectedIdTransactionPos(other.getIdTransactionPos());
    other.setConnectedIdTransactionPos(one.getIdTransactionPos());
    Map<Integer, ImportTransactionPos> idItpMap = new HashMap<>();
    idItpMap.put(one.getIdTransactionPos(), one);
    idItpMap.put(other.getIdTransactionPos(), other);
    return idItpMap;
  }

  private ImportTransactionPos pos(Integer idTransactionPos, Integer idTransaction) {
    ImportTransactionPos itp = new ImportTransactionPos();
    itp.setIdTransactionPos(idTransactionPos);
    itp.setIdTransactionHead(ID_TRANSACTION_HEAD);
    itp.setIdTransaction(idTransaction);
    itp.setReadyForTransaction(true);
    itp.setCashaccount(cashaccount(ID_TENANT));
    return itp;
  }

  private Cashaccount cashaccount(Integer idTenant) {
    Cashaccount cashaccount = new Cashaccount();
    cashaccount.setIdTenant(idTenant);
    return cashaccount;
  }

  private ImportTransactionHead head() {
    ImportTransactionHead importTransactionHead = new ImportTransactionHead();
    importTransactionHead.setIdTransactionHead(ID_TRANSACTION_HEAD);
    return importTransactionHead;
  }
}
