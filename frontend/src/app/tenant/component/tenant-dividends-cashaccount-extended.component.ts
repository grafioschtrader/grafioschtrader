import {Component, EventEmitter, Injector, Input, OnInit, Output} from '@angular/core';
import {FilterService} from 'primeng/api';
import {TranslateModule, TranslateService} from '@ngx-translate/core';
import {GlobalparameterService} from '../../lib/services/globalparameter.service';
import {UserSettingsService} from '../../lib/services/user.settings.service';
import {CashAccountPosition} from '../../entities/view/securitydividends/security.dividends.position';
import {DataType} from '../../lib/dynamic-form/models/data.type';
import {SecurityDividendsGrandTotal} from '../../entities/view/securitydividends/security.dividends.grand.total';
import {ProcessedActionData} from '../../lib/types/processed.action.data';
import {CashAccountTableInputFilter, TransactionCashaccountTableComponent} from '../../transaction/component/transaction-cashaccount-table.component';
import {TransactionType} from '../../shared/types/transaction.type';
import {TenantDividendsExtendedBase} from './tenant.dividends.extended.base';
import {ParentChildRegisterService} from '../../shared/service/parent.child.register.service';
import {CommonModule} from '@angular/common';
import {ConfigurableTableComponent} from '../../lib/datashowbase/configurable-table.component';

/**
 * Displays the cash accounts in the interest/dividend report.
 */
@Component({
  selector: 'tenant-dividends-cashaccount-extended',
  template: `
    <div class="datatable">
      <configurable-table
        [data]="cashAccountPositions"
        [fields]="fields"
        dataKey="cashaccount.idSecuritycashAccount"
        [multiSortMeta]="multiSortMeta"
        [customSortFn]="customSort.bind(this)"
        [valueGetterFn]="getValueByPath.bind(this)"
        selectionMode="single"
        [expandable]="true"
        [expandedRowTemplate]="expandedContent"
        [stripedRows]="true"
        [showGridlines]="true">
        <h5 caption>{{ 'CASHACCOUNTS'|translate }}</h5>
      </configurable-table>

      <ng-template #expandedContent let-row>
        <transaction-cashaccount-table
          (dateChanged)="transactionDataChanged($event)"
          [idSecuritycashAccount]="row.cashaccount.idSecuritycashAccount"
          [cashAccountTableInputFilter]="cashAccountTableInputFilter">
        </transaction-cashaccount-table>
      </ng-template>
    </div>
  `,
  standalone: true,
  imports: [
    CommonModule,
    TranslateModule,
    ConfigurableTableComponent,
    TransactionCashaccountTableComponent
  ]
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
    gps: GlobalparameterService,
    injector: Injector) {
    super(filterService, usersettingsService, translateService, gps, injector);
  }

  ngOnInit(): void {
    this.cashAccountTableInputFilter = new CashAccountTableInputFilter(
      [TransactionType.FEE, TransactionType.INTEREST_CASHACCOUNT], this.year);
    this.addColumn(DataType.String, 'cashaccount.name', 'NAME', true, false, {width: 200});
    this.addColumnFeqH(DataType.String, 'cashaccount.currency', true, false);
    this.addColumnFeqH(DataType.Numeric, 'closeEndOfYear', true, false);
    this.addColumnFeqH(DataType.Numeric, 'feeCashAccount', true, false, {width: 60});
    this.addColumnFeqH(DataType.Numeric, 'feeCashAccountMC', true, false,
      {width: 60, headerSuffix: this.securityDividendsGrandTotal.mainCurrency});
    this.addColumnFeqH(DataType.Numeric, 'feeSecurityAccount', true, false);
    this.addColumnFeqH(DataType.Numeric, 'feeSecurityAccountMC', true, false,
      {headerSuffix: this.securityDividendsGrandTotal.mainCurrency});
    this.addGeneralColumns(this.securityDividendsGrandTotal.mainCurrency);
    this.addColumnFeqH(DataType.Numeric, 'cashBalance', true, false);
    this.addColumnFeqH(DataType.Numeric, 'cashBalanceMC', true, false,
      {headerSuffix: this.securityDividendsGrandTotal.mainCurrency});
    this.addColumnFeqH(DataType.Numeric, 'marginEarningsMC', false, true,
      {headerSuffix: this.securityDividendsGrandTotal.mainCurrency});
    this.addColumnFeqH(DataType.Numeric, 'hypotheticalFinanceCostMC', false, true,
      {headerSuffix: this.securityDividendsGrandTotal.mainCurrency});
    this.addColumnFeqH(DataType.Numeric, 'cashBalancePlusMarginMC', false, true,
      {headerSuffix: this.securityDividendsGrandTotal.mainCurrency});
    this.multiSortMeta.push({field: 'cashaccount.name', order: 1});
    this.prepareTableAndTranslate();
    if (this.securityDividendsGrandTotal.hasMarginData) {
      this.fields.filter(f => f.field === 'marginEarningsMC' || f.field === 'hypotheticalFinanceCostMC'
        || f.field === 'cashBalancePlusMarginMC')
        .forEach(f => f.visible = true);
    }
    this.parentChildRegisterService.initRegistry();
  }

  transactionDataChanged(event: ProcessedActionData) {
    this.dateChanged.emit(event);
  }

}
