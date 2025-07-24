import {Component, EventEmitter, Input, OnInit, Output} from '@angular/core';
import {SecurityDividendsPosition} from '../../../entities/view/securitydividends/security.dividends.position';
import {TranslateService} from '@ngx-translate/core';
import {UserSettingsService} from '../../../shared/service/user.settings.service';
import {DataType} from '../../dynamic-form/models/data.type';
import {SecurityDividendsGrandTotal} from '../../../entities/view/securitydividends/security.dividends.grand.total';
import {GlobalparameterService} from '../../../shared/service/globalparameter.service';
import {Security} from '../../../entities/security';
import {BusinessHelper} from '../../../shared/helper/business.helper';
import {ProcessedActionData} from '../../types/processed.action.data';
import {TransactionSecurityOptionalParam} from '../../../transaction/model/transaction.security.optional.param';
import {FilterService} from 'primeng/api';
import {TenantDividendsExtendedBase} from './tenant.dividends.extended.base';

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
                <span [pTooltip]="getValueByPath(el, field)" tooltipPosition="top">{{ getValueByPath(el, field) }}</span>
              </td>
            }
          </tr>
        </ng-template>
        <ng-template #expandedrow let-sdp let-columns="fields">
          <tr>
            <td [attr.colspan]="numberOfVisibleColumns + 1">
              @if (!!sdp.security.stockexchange && !isMarginProduct(sdp.security)) {
                <transaction-security-table [idTenant]="idTenant"
                                            [idSecuritycurrency]="sdp.security.idSecuritycurrency"
                                            [idsSecurityaccount]="idsSecurityaccount"
                                            [transactionSecurityOptionalParam]="tsop"
                                            (dateChanged)="transactionDataChanged($event)">
                </transaction-security-table>
              }

              @if (!!sdp.security.stockexchange && isMarginProduct(sdp.security)) {
                <transaction-security-margin-treetable
                  [idTenant]="idTenant"
                  [idSecuritycurrency]="sdp.security.idSecuritycurrency"
                  [idsSecurityaccount]="idsSecurityaccount"
                  [transactionSecurityOptionalParam]="tsop"
                  (dateChanged)="transactionDataChanged($event)">
                </transaction-security-margin-treetable>
              }
            </td>
          </tr>
        </ng-template>
      </p-table>
    </div>
  `,
    standalone: false
})
export class TenantDividendsSecurityExtendedComponent extends TenantDividendsExtendedBase implements OnInit {
  @Input() idsSecurityaccount: number[];
  @Input() securityDividendsGrandTotal: SecurityDividendsGrandTotal;
  @Input() securityDividendsPositions: SecurityDividendsPosition[];
  idTenant: number;
  tsop = [TransactionSecurityOptionalParam.SHOW_TAXABLE_COLUMN];

  // Output
  @Output() dateChanged = new EventEmitter<ProcessedActionData>();

  constructor(filterService: FilterService,
    usersettingsService: UserSettingsService,
    translateService: TranslateService,
    gps: GlobalparameterService) {
    super(filterService, usersettingsService, translateService, gps);
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
    this.addGeneralColumns(this.securityDividendsGrandTotal.mainCurrency);
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
