import { Component, input, output } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { TableModule } from '@openng/optimus-ui/table';
import { TableConfigBase } from '../../lib/datashowbase/table.config.base';
import { ShowRecordConfigBase } from '../../lib/datashowbase/show.record.config.base';
import { DataType } from '../../lib/dynamic-form/models/data.type';
import { ValueKeyHtmlSelectOptions } from '../../lib/dynamic-form/models/value.key.html.select.options';
import { SpinOffAccountDecision, SpinOffPreviewAccount } from '../model/security-action.model';

/** Shows the proposed bookings and any existing transactions that may already cover an account. */
@Component({
  selector: 'spin-off-preview-table',
  template: `
    <p-table [value]="rows()" dataKey="idSecurityaccount" [scrollable]="true" stripedRows>
      <ng-template #header>
        <tr>
          @for (field of fields; track field.field) {
            <th>{{ field.headerTranslated }}</th>
          }
          <th>{{ 'SPIN_OFF_CANDIDATES' | translate }}</th>
          <th>{{ 'SPIN_OFF_DECISION' | translate }}</th>
        </tr>
      </ng-template>
      <ng-template #body let-row>
        <tr>
          @for (field of fields; track field.field) {
            <td>{{ getValueByPath(row, field) }}</td>
          }
          <td>
            @if (row.candidates.length) {
              <details>
                <summary>{{ 'SPIN_OFF_CANDIDATE_COUNT' | translate: { count: row.candidates.length } }}</summary>
                <ul>
                  @for (candidate of row.candidates; track candidate.idTransaction) {
                    <li>
                      {{ getValueByPath(candidate, candidateTimeField) }}
                      · {{ (candidate.child ? 'SECURITY_NEW' : 'SECURITY_OLD') | translate }} ·
                      {{ candidate.type | translate }}
                      @if (candidate.units != null) {
                        · {{ getValueByPath(candidate, candidateUnitsField) }}
                      }
                      @if (candidate.quotation != null) {
                        · {{ getValueByPath(candidate, candidateQuotationField) }}
                      }
                    </li>
                  }
                </ul>
              </details>
            }
          </td>
          <td>
            <select
              class="form-select"
              [attr.aria-label]="('SPIN_OFF_DECISION' | translate) + ' ' + row.accountName"
              [value]="row.decision"
              [disabled]="busy()"
              (change)="decide(row, decision.value)"
              #decision>
              @for (option of options(); track option.key) {
                <option [value]="option.key" [selected]="option.key === row.decision">
                  {{ option.value | translate }}
                </option>
              }
            </select>
          </td>
        </tr>
      </ng-template>
    </p-table>
  `,
  imports: [TableModule, TranslatePipe]
})
export class SpinOffPreviewTableComponent extends TableConfigBase {
  readonly rows = input.required<SpinOffPreviewAccount[]>();
  readonly options = input.required<ValueKeyHtmlSelectOptions[]>();
  readonly busy = input(false);
  readonly decisionChanged = output<SpinOffAccountDecision>();

  /** Formats the time of an existing transaction in the user's date and time format. */
  readonly candidateTimeField = ShowRecordConfigBase.createColumnConfig(
    DataType.DateTimeString,
    'transactionTime',
    null
  );
  /** Formats the units of an existing transaction like the units column of the transaction tables. */
  readonly candidateUnitsField = ShowRecordConfigBase.createColumnConfig(DataType.Numeric, 'units', null);
  /** Formats the price of an existing transaction with the precision of the transaction tables. */
  readonly candidateQuotationField = ShowRecordConfigBase.createColumnConfig(
    DataType.Numeric,
    'quotation',
    null,
    true,
    false,
    { maxFractionDigits: this.gps.getMaxFractionDigits() }
  );

  constructor() {
    super();
    this.addColumn(DataType.String, 'accountName', 'SECURITYACCOUNT', true, false);
    this.addColumn(DataType.Numeric, 'parentUnits', 'SPIN_OFF_PARENT_UNITS', true, false);
    this.addColumn(DataType.Numeric, 'childUnits', 'SPIN_OFF_CHILD_UNITS', true, false);
    this.addColumn(DataType.NumericShowZero, 'fraction', 'SPIN_OFF_FRACTION', true, false);
    this.addColumn(DataType.String, 'cashCurrency', 'CURRENCY_CASHACCOUNT', true, false);
    // Exchange rates with the precision of the exchange rate column of the transaction tables
    this.addColumn(DataType.Numeric, 'fxParentToCash', 'SPIN_OFF_PARENT_FX', true, false, {
      maxFractionDigits: this.gps.getMaxFractionDigits()
    });
    this.addColumn(DataType.Numeric, 'fxChildToCash', 'SPIN_OFF_CHILD_FX', true, false, {
      maxFractionDigits: this.gps.getMaxFractionDigits()
    });
    this.addColumn(DataType.NumericShowZero, 'saleAmount', 'SPIN_OFF_SALE', true, false, {
      currencyPrecisionField: 'cashCurrency'
    });
    this.addColumn(DataType.NumericShowZero, 'repurchaseAmount', 'SPIN_OFF_REPURCHASE', true, false, {
      currencyPrecisionField: 'cashCurrency'
    });
    this.addColumn(DataType.NumericShowZero, 'childAmount', 'SPIN_OFF_CHILD', true, false, {
      currencyPrecisionField: 'cashCurrency'
    });
    this.translateHeadersAndColumns();
  }

  decide(row: SpinOffPreviewAccount, decision: string): void {
    if (decision === 'BOOK' || decision === 'SKIP') {
      this.decisionChanged.emit({ idSecurityaccount: row.idSecurityaccount, parentUnits: row.parentUnits, decision });
    }
  }
}
