import {Component, EventEmitter, Injector, Input, OnInit, Output} from '@angular/core';
import {FormsModule} from '@angular/forms';
import {SecurityDividendsPosition} from '../../entities/view/securitydividends/security.dividends.position';
import {TranslateModule, TranslateService} from '@ngx-translate/core';
import {UserSettingsService} from '../../lib/services/user.settings.service';
import {DataType} from '../../lib/dynamic-form/models/data.type';
import {SecurityDividendsGrandTotal} from '../../entities/view/securitydividends/security.dividends.grand.total';
import {GlobalparameterService} from '../../lib/services/globalparameter.service';
import {Security} from '../../entities/security';
import {BusinessHelper} from '../../shared/helper/business.helper';
import {ProcessedActionData} from '../../lib/types/processed.action.data';
import {TransactionSecurityOptionalParam} from '../../transaction/model/transaction.security.optional.param';
import {FilterService, SharedModule} from 'primeng/api';
import {TenantDividendsExtendedBase} from './tenant.dividends.extended.base';
import {TaxDataService} from '../../taxdata/service/tax-data.service';
import {CommonModule} from '@angular/common';
import {TableModule} from 'primeng/table';
import {TooltipModule} from 'primeng/tooltip';
import {CheckboxModule} from 'primeng/checkbox';
import {TransactionSecurityTableComponent} from '../../transaction/component/transaction-security-table.component';
import {TransactionSecurityMarginTreetableComponent} from '../../transaction/component/transaction-security-margin-treetable.component';

/**
 * Shows the dividends and other information of securities for one year in a table. One row per security.
 */
@Component({
    selector: 'tenant-dividends-security-extended',
  template: `
    <div class="datatable">
      <p-table [columns]="fields" [value]="securityDividendsPositions" selectionMode="single"
               dataKey="security.idSecuritycurrency" sortMode="multiple" [multiSortMeta]="multiSortMeta"
               stripedRows showGridlines>
        <ng-template #caption>
          <h5>{{ 'INSTRUMENT'|translate }}</h5>
        </ng-template>
        <ng-template #header let-fields>
          <tr>
            <th style="width:24px"></th>
            <th style="width:40px" [pTooltip]="'EXCLUDED_FROM_TAX_TOOLTIP' | translate">
              {{ 'EXCLUDED_FROM_TAX' | translate }}
            </th>
            @for (field of fields; track field) {
              <th [pSortableColumn]="field.field" [style.max-width.px]="field.width"
                  [ngStyle]="field.width? {'flex-basis': '0 0 ' + field.width + 'px'}: {}"
                  [pTooltip]="field.headerTooltipTranslated"
                  class="word-break-header" [attr.lang]="baseLocale.language">
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
            <td style="text-align:center">
              <p-checkbox [binary]="true" [(ngModel)]="el.excludedFromTax" (onChange)="onExclusionToggle(el)"></p-checkbox>
            </td>
            @for (field of fields; track field) {
              <td [style.max-width.px]="field.width"
                  [ngStyle]="field.width? {'flex-basis': '0 0 ' + field.width + 'px'}: {}"
                  [ngClass]="(field.dataType===DataType.Numeric || field.dataType===DataType.DateTimeNumeric
                || field.dataType===DataType.NumericInteger)? 'text-end': ''">
                <span [pTooltip]="getValueByPath(el, field)" tooltipPosition="top">{{ getValueByPath(el, field) }}</span>
              </td>
            }
          </tr>
        </ng-template>
        <ng-template #expandedrow let-sdp let-columns="fields">
          <tr>
            <td [attr.colspan]="numberOfVisibleColumns + 2">
              @if (!!sdp.security.stockexchange && !isMarginProduct(sdp.security)) {
                <transaction-security-table [idTenant]="idTenant"
                                            [idSecuritycurrency]="sdp.security.idSecuritycurrency"
                                            [idsSecurityaccount]="idsSecurityaccount"
                                            [transactionSecurityOptionalParam]="tsop"
                                            [untilDate]="untilDateForTransactions"
                                            (dateChanged)="transactionDataChanged($event)">
                </transaction-security-table>
              }

              @if (!!sdp.security.stockexchange && isMarginProduct(sdp.security)) {
                <transaction-security-margin-treetable
                  [idTenant]="idTenant"
                  [idSecuritycurrency]="sdp.security.idSecuritycurrency"
                  [idsSecurityaccount]="idsSecurityaccount"
                  [transactionSecurityOptionalParam]="tsop"
                  [untilDate]="untilDateForTransactions"
                  (dateChanged)="transactionDataChanged($event)">
                </transaction-security-margin-treetable>
              }
            </td>
          </tr>
        </ng-template>
      </p-table>
    </div>
  `,
    standalone: true,
    imports: [
        CommonModule,
        FormsModule,
        TableModule,
        TooltipModule,
        TranslateModule,
        SharedModule,
        CheckboxModule,
        TransactionSecurityTableComponent,
        TransactionSecurityMarginTreetableComponent
    ]
})
export class TenantDividendsSecurityExtendedComponent extends TenantDividendsExtendedBase implements OnInit {
  @Input() idsSecurityaccount: number[];
  @Input() securityDividendsGrandTotal: SecurityDividendsGrandTotal;
  @Input() securityDividendsPositions: SecurityDividendsPosition[];
  @Input() year: number;
  @Input() filterTransactionsToYearEnd: boolean;
  idTenant: number;
  tsop = [TransactionSecurityOptionalParam.SHOW_TAXABLE_COLUMN];

  get untilDateForTransactions(): string | undefined {
    return this.filterTransactionsToYearEnd && this.year ? `${this.year}-12-31` : undefined;
  }

  // Output
  @Output() dateChanged = new EventEmitter<ProcessedActionData>();

  constructor(filterService: FilterService,
    usersettingsService: UserSettingsService,
    translateService: TranslateService,
    gps: GlobalparameterService,
    injector: Injector,
    private taxDataService: TaxDataService) {
    super(filterService, usersettingsService, translateService, gps, injector);
    this.idTenant = this.gps.getIdTenant();
  }

  ngOnInit(): void {
    this.addColumn(DataType.String, 'security.name', 'NAME', true, false, {width: 200});
    this.addColumnFeqH(DataType.String, 'security.isin', true, false, {width: 90});
    this.addColumn(DataType.String, 'security.currency', 'CURRENCY', true, false);
    this.addColumnFeqH(DataType.String, 'exchangeRateEndOfYear', true, false);
    this.addColumnFeqH(DataType.Numeric, 'unitsAtEndOfYear', true, false);
    this.addColumnFeqH(DataType.Numeric, 'closeEndOfYear', true, false);
    this.addColumnFeqH(DataType.Numeric, 'taxFreeIncome', true, false);
    this.addColumnFeqH(DataType.Numeric, 'financeCostMC', false, true,
      {width: 80, headerSuffix: this.securityDividendsGrandTotal.mainCurrency});
    this.addGeneralColumns(this.securityDividendsGrandTotal.mainCurrency);
    this.addIctaxColumns();
    this.addColumnFeqH(DataType.Numeric, 'valueAtEndOfYearMC', true, false,
      {width: 70, headerSuffix: this.securityDividendsGrandTotal.mainCurrency});
    this.multiSortMeta.push({field: 'security.name', order: 1});
    this.prepareTableAndTranslate();
    if (this.securityDividendsGrandTotal.hasMarginData) {
      const marginCol = this.fields.find(f => f.field === 'financeCostMC');
      if (marginCol) { marginCol.visible = true; }
    }
    if (this.securityDividendsGrandTotal.tenantCountry === 'CH') {
      this.fields.filter(f => f.field === 'ictaxTotalPaymentValueChf' || f.field === 'ictaxTotalTaxValueChf')
        .forEach(f => f.visible = true);
    }
  }

  isMarginProduct(security: Security): boolean {
    return BusinessHelper.isMarginProduct(security);
  }

  onExclusionToggle(sdp: SecurityDividendsPosition): void {
    this.taxDataService.toggleSecurityExclusion(this.year, sdp.security.idSecuritycurrency).subscribe({
      error: () => {
        sdp.excludedFromTax = !sdp.excludedFromTax;
      }
    });
  }

  transactionDataChanged(event: ProcessedActionData) {
    this.dateChanged.emit(event);
  }

}
