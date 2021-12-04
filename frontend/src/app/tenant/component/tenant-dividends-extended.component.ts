import {Component, EventEmitter, Input, OnInit, Output} from '@angular/core';
import {SecurityDividendsPosition} from '../../entities/view/securitydividends/security.dividends.position';
import {TableConfigBase} from '../../shared/datashowbase/table.config.base';
import {TranslateService} from '@ngx-translate/core';
import {UserSettingsService} from '../../shared/service/user.settings.service';
import {DataType} from '../../dynamic-form/models/data.type';
import {SecurityDividendsGrandTotal} from '../../entities/view/securitydividends/security.dividends.grand.total';
import {GlobalparameterService} from '../../shared/service/globalparameter.service';
import {Security} from '../../entities/security';
import {BusinessHelper} from '../../shared/helper/business.helper';
import {ProcessedActionData} from '../../shared/types/processed.action.data';
import {TransactionSecurityOptionalParam} from '../../transaction/model/transaction.security.optional.param';
import {FilterService} from 'primeng/api';

/**
 * Shows the dividends and other information of securities for one year in a table. One row per security.
 */
@Component({
  selector: 'tenant-dividends-extended',
  template: `
    <div class="datatable">
      <p-table [columns]="fields" [value]="securityDividendsPositions" selectionMode="single"
               styleClass="sticky-table p-datatable-striped p-datatable-gridlines" responsiveLayout="scroll"
               dataKey="idSecuritycurrency" [responsive]="true" sortMode="multiple" [multiSortMeta]="multiSortMeta">
        <ng-template pTemplate="header" let-fields>
          <tr>
            <th style="width:24px"></th>
            <th *ngFor="let field of fields" [pSortableColumn]="field.field" [style.max-width.px]="field.width"
                [ngStyle]="field.width? {'flex-basis': '0 0 ' + field.width + 'px'}: {}"
                [pTooltip]="field.headerTooltipTranslated">
              {{field.headerTranslated}}
              <p-sortIcon [field]="field.field"></p-sortIcon>
            </th>
          </tr>
        </ng-template>
        <ng-template pTemplate="body" let-expanded="expanded" let-el let-columns="fields">
          <tr [pSelectableRow]="el">
            <td>
              <a href="#" [pRowToggler]="el">
                <i [ngClass]="expanded ? 'fa fa-fw fa-chevron-circle-down' : 'fa fa-fw fa-chevron-circle-right'"></i>
              </a>
            </td>
            <td *ngFor="let field of fields" [style.max-width.px]="field.width"
                [ngStyle]="field.width? {'flex-basis': '0 0 ' + field.width + 'px'}: {}"
                [ngClass]="(field.dataType===DataType.Numeric || field.dataType===DataType.DateTimeNumeric
                || field.dataType===DataType.NumericInteger)? 'text-right': ''">
              <span [pTooltip]="getValueByPath(el, field)" tooltipPosition="top">{{getValueByPath(el, field)}}</span>
            </td>
          </tr>
        </ng-template>
        <ng-template pTemplate="rowexpansion" let-sdp let-columns="fields">
          <tr>
            <td [attr.colspan]="numberOfVisibleColumns + 1">
              <transaction-security-table *ngIf="sdp.security.stockexchange && !isMarginProduct(sdp.security)"
                                          [idTenant]="idTenant"
                                          [idSecuritycurrency]="sdp.idSecuritycurrency"
                                          [idsSecurityaccount]="idsSecurityaccount"
                                          [transactionSecurityOptionalParam]="tsop"
                                          (dateChanged)="transactionDataChanged($event)">
              </transaction-security-table>

              <transaction-security-margin-treetable
                *ngIf="sdp.security.stockexchange && isMarginProduct(sdp.security)"
                [idTenant]="idTenant"
                [idSecuritycurrency]="sdp.idSecuritycurrency"
                [idsSecurityaccount]="idsSecurityaccount"
                [transactionSecurityOptionalParam]="tsop"
                (dateChanged)="transactionDataChanged($event)">
              </transaction-security-margin-treetable>
            </td>
          </tr>
        </ng-template>
      </p-table>
    </div>
  `
})
export class TenantDividendsExtendedComponent extends TableConfigBase implements OnInit {
  @Input() idsSecurityaccount: number[];
  @Input() securityDividendsGrandTotal: SecurityDividendsGrandTotal;
  @Input() securityDividendsPositions: SecurityDividendsPosition[];
  idTenant: number;
  tsop = [TransactionSecurityOptionalParam.SHOW_TAXABLE_COLUMN];

  // Output
  @Output() dateChanged = new EventEmitter<ProcessedActionData>();

  constructor(filterService: FilterService,
              translateService: TranslateService,
              gps: GlobalparameterService,
              usersettingsService: UserSettingsService) {
    super(filterService, usersettingsService, translateService, gps);
    this.idTenant = this.gps.getIdTenant();
  }

  ngOnInit(): void {
    this.addColumn(DataType.String, 'security.name', 'NAME', true, false, {width: 200});
    this.addColumnFeqH(DataType.String, 'security.isin', true, false, {width: 90});
    this.addColumn(DataType.String, 'security.currency', 'CURRENCY', true, false);
    this.addColumnFeqH(DataType.String, 'exchangeRateEndOfYear', true, false);
    this.addColumnFeqH(DataType.Numeric, 'unitsAtEndOfYear', true, false);
    this.addColumn(DataType.Numeric, 'historyquote.close', 'QUOTATION_END_OF_YEAR', true, false);
    this.addColumnFeqH(DataType.Numeric, 'taxFreeIncome', true, false);
    this.addColumn(DataType.Numeric, 'autoPaidTax', 'AUTO_PAID_TAX', true, false);
    this.addColumn(DataType.Numeric, 'autoPaidTaxMC', 'AUTO_PAID_TAX', true, false,
      {headerSuffix: this.securityDividendsGrandTotal.mainCurrency});
    this.addColumnFeqH(DataType.Numeric, 'taxableAmount', true, false);
    this.addColumnFeqH(DataType.Numeric, 'taxableAmountMC', true, false,
      {headerSuffix: this.securityDividendsGrandTotal.mainCurrency});
    this.addColumnFeqH(DataType.Numeric, 'realReceivedDivInterestMC', true, false,
      {headerSuffix: this.securityDividendsGrandTotal.mainCurrency});
    this.addColumnFeqH(DataType.Numeric, 'valueAtEndOfYearMC', true, false,
      {width: 70, headerSuffix: this.securityDividendsGrandTotal.mainCurrency});
    this.multiSortMeta.push({field: 'security.name', order: 1});
    this.prepareTableAndTranslate();
  }

  isMarginProduct(security: Security): boolean {
    return BusinessHelper.isMarginProduct(security);
  }

  transactionDataChanged(event: ProcessedActionData) {
    this.dateChanged.emit(event);
  }

}
