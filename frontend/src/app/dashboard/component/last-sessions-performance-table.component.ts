import { Component, Input, OnChanges } from '@angular/core';
import { FilterService } from '@openng/optimus-ui/api';
import { TranslateService } from '@ngx-translate/core';
import { ConfigurableTableComponent } from '../../lib/datashowbase/configurable-table.component';
import { TableConfigBase } from '../../lib/datashowbase/table.config.base';
import { DataType } from '../../lib/dynamic-form/models/data.type';
import { GlobalparameterService } from '../../lib/services/globalparameter.service';
import { UserSettingsService } from '../../lib/services/user.settings.service';
import { LastSessionsRow } from '../model/last.sessions.performance';

/**
 * The sessions of the last-sessions card, newest first.
 *
 * <p>
 * The result and the change of the total value stand next to each other on purpose. A reader who only sees the change
 * of the value cannot tell a deposit from a gain, and a reader who only sees the result cannot reconcile the card with
 * the balance; the transfer column in between is exactly their difference.
 * </p>
 *
 * <p>
 * Fees and interest are shown for the days on which they explain a movement no market caused. They are contained in the
 * result rather than added to it, which the tooltip of each header says, because a reader would otherwise be tempted to
 * add the columns up.
 * </p>
 */
@Component({
  selector: 'last-sessions-performance-table',
  standalone: true,
  imports: [ConfigurableTableComponent],
  template: `
    <configurable-table
      [data]="sessions"
      [fields]="fields"
      dataKey="date"
      [selectionMode]="null"
      [enableCustomSort]="false"
      [scrollable]="true"
      [scrollHeight]="scrollHeight"
      [valueGetterFn]="getValueByPath.bind(this)"
      [baseLocale]="baseLocale" />
  `
})
export class LastSessionsPerformanceTableComponent extends TableConfigBase implements OnChanges {
  /** Sessions in the order the table shows them; the card reverses the server's ascending order. */
  @Input() sessions: LastSessionsRow[] = [];

  /** Currency of every amount; the card reports one currency for the whole client or portfolio. */
  @Input() currency: string;

  /** Height at which the table starts to scroll, so a card cannot grow past its neighbours. */
  @Input() scrollHeight = '220px';

  private readonly amountFields = [
    'totalGainMC',
    'totalBalanceChangeMC',
    'externalCashTransferMC',
    'feeRealMC',
    'interestCashaccountRealMC',
    'totalBalanceMC'
  ];

  constructor(
    filterService: FilterService,
    usersettingsService: UserSettingsService,
    translateService: TranslateService,
    gps: GlobalparameterService
  ) {
    super(filterService, usersettingsService, translateService, gps);
    this.addColumn(DataType.DateString, 'date', 'DASHBOARD_LAST_SESSIONS_DATE', true, false, {
      width: 90,
      cellTooltipFN: this.sessionTooltip.bind(this)
    });
    this.addColumn(DataType.Numeric, 'totalGainMC', 'TOTAL_GAIN', true, false, {
      width: 110,
      templateName: 'greenRed'
    });
    this.addColumn(DataType.Numeric, 'totalBalanceChangeMC', 'DASHBOARD_LAST_SESSIONS_BALANCE_CHANGE', true, false, {
      width: 110,
      templateName: 'greenRed'
    });
    this.addColumn(DataType.Numeric, 'externalCashTransferMC', 'EXTERNAL_CASH_TRANSFER', true, false, { width: 110 });
    this.addColumn(DataType.Numeric, 'feeRealMC', 'FEE_REAL', true, false, { width: 100 });
    this.addColumn(DataType.Numeric, 'interestCashaccountRealMC', 'INTEREST_CASHACCOUNT_REAL', true, false, {
      width: 110
    });
    this.addColumn(DataType.Numeric, 'totalBalanceMC', 'TOTAL_BALANCE', true, false, { width: 120 });
    this.prepareTableAndTranslate();
  }

  ngOnChanges(): void {
    this.amountFields.forEach(
      (field) => (this.fields.find((columnConfig) => columnConfig.field === field).fixedCurrency = this.currency)
    );
  }

  /**
   * Tells the reader when a session does not rest on market prices alone. Nothing is said about an ordinary session,
   * because a tooltip on every row is a tooltip nobody reads.
   */
  private sessionTooltip(row: LastSessionsRow): string {
    return row.substitute ? this.translateService.instant('DASHBOARD_LAST_SESSIONS_SUBSTITUTE') : undefined;
  }
}
