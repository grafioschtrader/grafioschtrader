import {Component, EventEmitter, Input, OnInit, Output} from '@angular/core';
import {FilterService} from 'primeng/api';
import {TranslateService} from '@ngx-translate/core';
import {GlobalparameterService} from '../../lib/services/globalparameter.service';
import {UserSettingsService} from '../../lib/services/user.settings.service';
import {CashAccountPosition} from '../../entities/view/securitydividends/security.dividends.position';
import {DataType} from '../../lib/dynamic-form/models/data.type';
import {SecurityDividendsGrandTotal} from '../../entities/view/securitydividends/security.dividends.grand.total';
import {ProcessedActionData} from '../../lib/types/processed.action.data';
import {CashAccountTableInputFilter} from '../../transaction/component/transaction-cashaccount-table.component';
import {TransactionType} from '../../shared/types/transaction.type';
import {TenantDividendsExtendedBase} from './tenant.dividends.extended.base';
import {ParentChildRegisterService} from '../../shared/service/parent.child.register.service';

/**
 * Displays the cash accounts in the interest/dividend report.
 */
@Component({
  selector: 'tenant-dividends-cashaccount-extended',
  template: `
    <div class="datatable">
      <p-table [columns]="fields" [value]="cashAccountPositions" selectionMode="single"
               dataKey="cashaccount.idSecuritycashAccount" sortMode="multiple" [multiSortMeta]="multiSortMeta"
               stripedRows showGridlines>
        <ng-template #caption>
          <h5>{{ 'CASHACCOUNTS'|translate }}</h5>
        </ng-template>
        <ng-template #header let-fields>
          <tr>
            <th style="width:24px"></th>
            @for (field of fields; track field) {
              <th [pSortableColumn]="field.field" [style.max-width.px]="field.width"
                  [ngStyle]="field.width? {'flex-basis': '0 0 ' + field.width + 'px'}: {}"
                  [pTooltip]="field.headerTooltipTranslated">
                {{ field.headerTranslated }}
                <p-sortIcon [field]="field.field"></p-sortIcon>
              </th>
            }
          </tr>
        </ng-template>
        <ng-template #body let-expanded="expanded" let-el let-columns="fields">
          <tr [pSelectableRow]="el">
            <td>
              <a href="#" [pRowToggler]="el">
                <i [ngClass]="expanded ? 'fa fa-fw fa-chevron-circle-down' : 'fa fa-fw fa-chevron-circle-right'"></i>
              </a>
            </td>
            @for (field of fields; track field) {
              <td [style.max-width.px]="field.width"
                  [ngStyle]="field.width? {'flex-basis': '0 0 ' + field.width + 'px'}: {}"
                  [ngClass]="(field.dataType===DataType.Numeric || field.dataType===DataType.DateTimeNumeric
                || field.dataType===DataType.NumericInteger)? 'text-right': ''">
                <span [pTooltip]="getValueByPath(el, field)"
                      tooltipPosition="top">{{ getValueByPath(el, field) }}</span>
              </td>
            }
          </tr>
        </ng-template>
        <ng-template let-cap let-columns="fields" #expandedrow>
          <tr>
            <td [attr.colspan]="numberOfVisibleColumns + 1" style="overflow:visible;">
              <transaction-cashaccount-table (dateChanged)="transactionDataChanged($event)"
                                             [idSecuritycashAccount]="cap.cashaccount.idSecuritycashAccount"
                                             [cashAccountTableInputFilter]="cashAccountTableInputFilter">
              </transaction-cashaccount-table>
            </td>
          </tr>
        </ng-template>
      </p-table>
    </div>
  `,
  standalone: false
})
export class TenantDividendsCashaccountExtendedComponent extends TenantDividendsExtendedBase implements OnInit {
  @Input() securityDividendsGrandTotal: SecurityDividendsGrandTotal;
  @Input() year: number;
  @Input() cashAccountPositions: CashAccountPosition[];

  @Output() dateChanged = new EventEmitter<ProcessedActionData>();

  cashAccountTableInputFilter: CashAccountTableInputFilter;

  constructor(private parentChildRegisterService: ParentChildRegisterService,
    filterService: FilterService,
    usersettingsService: UserSettingsService,
    translateService: TranslateService,
    gps: GlobalparameterService) {
    super(filterService, usersettingsService, translateService, gps);
  }

  ngOnInit(): void {
    this.cashAccountTableInputFilter = new CashAccountTableInputFilter(
      [TransactionType.FEE, TransactionType.INTEREST_CASHACCOUNT], this.year);
    this.addColumn(DataType.String, 'cashaccount.name', 'NAME', true, false, {width: 200});
    this.addColumnFeqH(DataType.String, 'cashaccount.currency', true, false);
    this.addColumnFeqH(DataType.Numeric, 'closeEndOfYear', true, false);
    this.addColumnFeqH(DataType.Numeric, 'feeCashAccount', true, false);
    this.addColumnFeqH(DataType.Numeric, 'feeCashAccountMC', true, false,
      {headerSuffix: this.securityDividendsGrandTotal.mainCurrency});
    this.addColumnFeqH(DataType.Numeric, 'feeSecurityAccount', true, false);
    this.addColumnFeqH(DataType.Numeric, 'feeSecurityAccountMC', true, false,
      {headerSuffix: this.securityDividendsGrandTotal.mainCurrency});
    this.addGeneralColumns(this.securityDividendsGrandTotal.mainCurrency);
    this.addColumnFeqH(DataType.Numeric, 'cashBalance', true, false);
    this.addColumnFeqH(DataType.Numeric, 'cashBalanceMC', true, false,
      {headerSuffix: this.securityDividendsGrandTotal.mainCurrency});
    this.multiSortMeta.push({field: 'cashaccount.name', order: 1});
    this.prepareTableAndTranslate();
    this.parentChildRegisterService.initRegistry();
  }

  transactionDataChanged(event: ProcessedActionData) {
    this.dateChanged.emit(event);
  }

}
