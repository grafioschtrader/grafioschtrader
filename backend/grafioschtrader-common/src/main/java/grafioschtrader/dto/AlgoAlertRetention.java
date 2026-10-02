package grafioschtrader.dto;

/**
 * Retention of the recorded alert notifications, read from the global parameter {@code gt.algo.alert.retention}. A
 * notification is removed once it exceeds either limit.
 *
 * @param days       notifications whose alert day lies more than this many days back are removed
 * @param maxRecords the number of the newest notifications each tenant keeps
 */
public record AlgoAlertRetention(int days, int maxRecords) {
}
