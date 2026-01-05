import {DataType} from '../../lib/dynamic-form/models/data.type';
import {FieldConfig} from '../../lib/dynamic-form/models/field.config';
import {DynamicFieldHelper} from '../../lib/helper/dynamic.field.helper';
import {Portfolio} from '../../entities/portfolio';
import {GlobalparameterGTService} from '../../gtservice/globalparameter.gt.service';

export abstract class FormDefinitionHelper {

  /**
   * Creates a transaction time field configuration with weekend restrictions.
   * Weekends (Saturday/Sunday) are always disabled.
   *
   * Note: For closedUntil period locking, use updateTransactionTimeMinDate() after
   * the portfolio is determined, since the portfolio selection happens after form creation.
   *
   * @returns FieldConfig for transaction time input
   */
  public static getTransactionTime(): FieldConfig {
    const transactionTime: FieldConfig = DynamicFieldHelper.createFieldPcalendarHeqF(DataType.DateTimeNumeric, 'transactionTime', true);
    transactionTime.calendarConfig.disabledDays = [0, 6];
    return transactionTime;
  }

  /**
   * Dynamically updates the transaction time field's minDate based on closedUntil.
   * Call this when the portfolio selection changes to enforce period locking.
   *
   * @param transactionTimeField The transaction time FieldConfig to update
   * @param closedUntil The effective closedUntil date (or null to remove restriction)
   */
  public static updateTransactionTimeMinDate(transactionTimeField: FieldConfig, closedUntil: Date | null): void {
    if (closedUntil) {
      // Set minDate to day after closedUntil (transactions on closedUntil are still locked)
      const nextDay = new Date(closedUntil);
      nextDay.setDate(nextDay.getDate() + 1);
      transactionTimeField.calendarConfig.minDate = nextDay;
    } else {
      transactionTimeField.calendarConfig.minDate = null;
    }
  }

  /**
   * Calculates the effective closedUntil date considering both portfolio and tenant levels.
   * Portfolio's closedUntil takes priority; if null, falls back to tenant's value.
   *
   * @param portfolio The portfolio to check (may have its own closedUntil)
   * @param gpsGT GlobalparameterGTService to get tenant-level closedUntil
   * @returns The effective closedUntil date, or null if no restriction applies
   */
  public static getEffectiveClosedUntil(portfolio: Portfolio, gpsGT: GlobalparameterGTService): Date | null {
    if (portfolio?.closedUntil) {
      return new Date(portfolio.closedUntil);
    }
    return gpsGT.getTenantClosedUntil();
  }

}
