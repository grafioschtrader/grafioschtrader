package grafioschtrader.instrument;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import grafioschtrader.reportviews.securityaccount.CostLedger;
import grafioschtrader.reportviews.securityaccount.CostLot;
import grafioschtrader.types.CostBasisMethod;

/**
 * Which lots a sale consumes and what they cost. The amounts are calculated by hand; the equality of the total gain
 * over all methods would not catch a wrongly chosen lot, because that only moves gain between realized and unrealized.
 */
@DisplayName("Cost ledger of a security account")
class CostLedgerTest {

  private static final double EXACT = 1e-9;

  @Test
  @DisplayName("FIFO consumes the oldest lot completely and the next one in part")
  void fifoAcrossLots() {
    CostLedger ledger = ledger(lot(1, 100, 1_000), lot(2, 100, 2_000), lot(3, 100, 3_000));

    CostLedger.Reduction reduction = ledger.reduce(150, CostBasisMethod.FIFO);

    assertThat(reduction.consumedLots()).extracting(lot -> lot.idTransaction).containsExactly(1, 2);
    assertThat(reduction.consumedLots()).extracting(lot -> lot.units).containsExactly(100.0, 50.0);
    assertThat(reduction.consumedLots()).extracting(lot -> lot.cost).containsExactly(1_000.0, 1_000.0);
    assertThat(reduction.cost()).isEqualTo(2_000.0, within(EXACT));
    assertThat(reduction.uncoveredUnits()).isZero();
    assertThat(ledger.getLots()).extracting(lot -> lot.idTransaction).containsExactly(2, 3);
    assertThat(ledger.getUnits()).isEqualTo(150.0, within(EXACT));
    assertThat(ledger.getCost()).isEqualTo(4_000.0, within(EXACT));
  }

  @Test
  @DisplayName("LIFO consumes the newest lot completely and the one before in part")
  void lifoAcrossLots() {
    CostLedger ledger = ledger(lot(1, 100, 1_000), lot(2, 100, 2_000), lot(3, 100, 3_000));

    CostLedger.Reduction reduction = ledger.reduce(150, CostBasisMethod.LIFO);

    assertThat(reduction.consumedLots()).extracting(lot -> lot.idTransaction).containsExactly(3, 2);
    assertThat(reduction.consumedLots()).extracting(lot -> lot.cost).containsExactly(3_000.0, 1_000.0);
    assertThat(reduction.cost()).isEqualTo(4_000.0, within(EXACT));
    assertThat(ledger.getLots()).extracting(lot -> lot.idTransaction).containsExactly(1, 2);
    assertThat(ledger.getCost()).isEqualTo(2_000.0, within(EXACT));
  }

  @Test
  @DisplayName("A consumed part is a copy: a later sale does not change what an earlier one reported")
  void consumedPartsAreCopies() {
    CostLedger ledger = ledger(lot(1, 100, 1_000));

    CostLot first = ledger.reduce(40, CostBasisMethod.FIFO).consumedLots().getFirst();
    ledger.reduce(60, CostBasisMethod.FIFO);

    assertThat(first.units).isEqualTo(40.0);
    assertThat(first.cost).isEqualTo(400.0, within(EXACT));
  }

  @Test
  @DisplayName("Ten sales of a tenth each leave neither units nor cost behind")
  void roundingRemainderGoesToTheLastSale() {
    CostLedger ledger = ledger(lot(1, 1, 1_000));

    // Nine subtractions of 0.1 leave 0.10000000000000003 units, a remainder below the precision units are stored with
    double cost = 0.0;
    CostLedger.Reduction last = null;
    for (int i = 0; i < 10; i++) {
      last = ledger.reduce(0.1, CostBasisMethod.FIFO);
      cost += last.cost();
    }

    assertThat(cost).isEqualTo(1_000.0, within(EXACT));
    assertThat(last.uncoveredUnits()).isZero();
    assertThat(ledger.isEmpty()).isTrue();
    assertThat(ledger.getUnits()).isZero();
  }

  @Test
  @DisplayName("A lot received for free covers a sale with a cost of zero")
  void freeLot() {
    CostLedger ledger = ledger(lot(1, 10, 0), lot(2, 10, 500));

    CostLedger.Reduction reduction = ledger.reduce(10, CostBasisMethod.FIFO);

    assertThat(reduction.cost()).isZero();
    assertThat(reduction.uncoveredUnits()).isZero();
    assertThat(ledger.getCost()).isEqualTo(500.0, within(EXACT));
  }

  @Test
  @DisplayName("An uncovered sale reports the missing units and invents no cost for them")
  void uncoveredSale() {
    CostLedger ledger = ledger(lot(1, 4, 40));

    CostLedger.Reduction reduction = ledger.reduce(10, CostBasisMethod.FIFO);

    assertThat(reduction.cost()).isEqualTo(40.0, within(EXACT));
    assertThat(reduction.uncoveredUnits()).isEqualTo(6.0, within(EXACT));
    assertThat(ledger.isEmpty()).isTrue();
  }

  private static CostLedger ledger(CostLot... lots) {
    CostLedger ledger = new CostLedger();
    for (CostLot lot : lots) {
      ledger.add(lot);
    }
    return ledger;
  }

  private static CostLot lot(int idTransaction, double units, double cost) {
    return new CostLot(idTransaction, LocalDateTime.of(2024, 1, idTransaction, 10, 0), null, units, cost);
  }
}
