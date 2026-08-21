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
import {FilterService, SharedModule} from '@openng/optimus-ui/api';
import {TenantDividendsExtendedBase} from './tenant.dividends.extended.base';
import {ColumnConfig} from '../../lib/datashowbase/column.config';
import {TaxDataService} from '../../taxdata/service/tax-data.service';
import {CommonModule} from '@angular/common';
import {TableModule} from '@openng/optimus-ui/table';
import {TooltipModule} from '@openng/optimus-ui/tooltip';
import {CheckboxModule} from '@openng/optimus-ui/checkbox';
import {ContextMenuModule} from '@openng/optimus-ui/contextmenu';
import {MenuItem} from '@openng/optimus-ui/api';
import {ProcessedAction} from '../../lib/types/processed.action';
import {TranslateHelper} from '../../lib/helper/translate.helper';
import {BaseSettings} from '../../lib/base.settings';
import {TaxYearCorrectionDialogComponent} from '../../taxdata/component/tax-year-correction-dialog.component';
import {TransactionSecurityTableComponent} from '../../transaction/component/transaction-security-table.component';
import {TransactionSecurityMarginTreetableComponent} from '../../transaction/component/transaction-security-margin-treetable.component';
import {AngularSvgIconModule} from 'angular-svg-icon';
import {AppSettings} from '../../shared/app.settings';
import {ProductIconService} from '../../securitycurrency/service/product.icon.service';

/**
 * Shows the dividends and other information of securities for one year in a table. One row per security.
 */
@Component({
    selector: 'tenant-dividends-security-extended',
  template: `
    <div class="datatable">
      <p-table [columns]="fields" [value]="securityDividendsPositions" selectionMode="single"
               dataKey="security.idSecuritycurrency" sortMode="multiple" [multiSortMeta]="multiSortMeta"
               [expandedRowKeys]="expandedRowKeys"
               [contextMenu]="isSwissTenant ? cmSec : null" [(contextMenuSelection)]="contextMenuSelectedPosition"
               (onContextMenuSelect)="onContextMenuSelect($event)"
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
          <tr [pSelectableRow]="el" [pContextMenuRow]="el">
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
                  [ngStyle]="getCellStyle(el, field)"
                  [ngClass]="(field.dataType===DataType.Numeric || field.dataType===DataType.DateTimeNumeric
                || field.dataType===DataType.NumericInteger)? 'text-end': ''">
                @switch (field.templateName) {
                  @case ('icon') {
                    <svg-icon [name]="getValueByPath(el, field)"
                              [svgStyle]="{ 'width.px':14, 'height.px':14 }"></svg-icon>
                  }
                  @default {
                    <span [pTooltip]="getCellTooltip(el, field)"
                          tooltipPosition="top">{{ getValueByPath(el, field) }}</span>
                  }
                }
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
      <p-contextMenu #cmSec [model]="contextMenuItems" appendTo="body"></p-contextMenu>
      @if (visibleCorrectionDialog) {
        <tax-year-correction-dialog [visibleDialog]="visibleCorrectionDialog"
                                    [security]="dialogSecurity"
                                    [yearOptions]="correctionYearOptions"
                                    [suggestedYear]="year"
                                    (closeDialog)="handleCorrectionDialogClose($event)">
        </tax-year-correction-dialog>
      }
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
        ContextMenuModule,
        TransactionSecurityTableComponent,
        TransactionSecurityMarginTreetableComponent,
        TaxYearCorrectionDialogComponent,
        AngularSvgIconModule
    ]
})
export class TenantDividendsSecurityExtendedComponent extends TenantDividendsExtendedBase implements OnInit {
  @Input() idsSecurityaccount: number[];
  @Input() securityDividendsGrandTotal: SecurityDividendsGrandTotal;
  @Input() securityDividendsPositions: SecurityDividendsPosition[];
  @Input() year: number;
  @Input() filterTransactionsToYearEnd: boolean;

  /**
   * Expansion state of the security rows, keyed by {@code security.idSecuritycurrency}. The object is owned by the
   * parent {@code TenantDividendsComponent} and passed in by reference, so the expansion survives this child being
   * recreated on a parent reload. Optimus mutates it in place on toggle, keeping the parent map in sync.
   */
  @Input() expandedRowKeys: { [id: string]: boolean } = {};

  idTenant: number;
  tsop = [TransactionSecurityOptionalParam.SHOW_TAXABLE_COLUMN];

  /** Dialog state for maintaining the tax year corrections of one security. */
  visibleCorrectionDialog = false;
  dialogSecurity: Security;
  correctionYearOptions: number[] = [];

  /** Right-click context menu of the security rows (Swiss tenants only). */
  contextMenuItems: MenuItem[] = [];
  contextMenuSelectedPosition: SecurityDividendsPosition;

  /** True for Swiss tenants; gates the ICTax columns and the tax year correction context menu. */
  get isSwissTenant(): boolean {
    return this.securityDividendsGrandTotal.tenantCountry === 'CH';
  }

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
    private taxDataService: TaxDataService,
    private productIconService: ProductIconService) {
    super(filterService, usersettingsService, translateService, gps, injector);
    this.idTenant = this.gps.getIdTenant();
  }

  /** Translated reason texts for the name-cell background colors, keyed by NLS key. */
  private nameColorReasons: { [key: string]: string } = {};

  ngOnInit(): void {
    this.translateService.get(['BOND_REDEEMED_MATURITY_YEAR', 'POSITION_ZERO_START_END_YEAR',
      'POSITION_SOLD_OUT_YEAR', 'POSITION_OPENED_YEAR', 'TAX_YEAR_CORRECTION_EXISTS'])
      .subscribe(translations => this.nameColorReasons = translations);
    this.addColumn(DataType.String, 'security.name', 'NAME', true, false, {width: 200});
    this.addColumn(DataType.String, 'security', AppSettings.INSTRUMENT_HEADER, true, false,
      {fieldValueFN: this.getInstrumentIcon.bind(this), templateName: 'icon', width: 20});
    this.addColumnFeqH(DataType.String, 'security.isin', true, false, {width: 90});
    this.addColumn(DataType.String, 'security.currency', 'CURRENCY', true, false);
    this.addColumnFeqH(DataType.String, 'exchangeRateEndOfYear', true, false);
    this.addColumnFeqH(DataType.Numeric, 'unitsAtEndOfYear', true, false);
    this.addColumnFeqH(DataType.Numeric, 'closeEndOfYear', true, false);
    this.addColumnFeqH(DataType.Numeric, 'taxFreeIncome', true, false,
      {currencyPrecisionField: 'security.currency'});
    this.addColumnFeqH(DataType.Numeric, 'financeCostMC', false, true,
      {
        width: 80, headerSuffix: this.securityDividendsGrandTotal.mainCurrency,
        fixedCurrency: this.securityDividendsGrandTotal.mainCurrency
      });
    this.addGeneralColumns(this.securityDividendsGrandTotal.mainCurrency, 'security.currency');
    this.addIctaxColumns();
    this.addColumnFeqH(DataType.Numeric, 'valueAtEndOfYearMC', true, false,
      {
        width: 70, headerSuffix: this.securityDividendsGrandTotal.mainCurrency,
        fixedCurrency: this.securityDividendsGrandTotal.mainCurrency
      });
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

  /**
   * Computes the inline style for a data cell. Preserves the column-width flex-basis and, for the
   * `ictaxTotalPaymentValueChf` column, adds a red/green background whose intensity reflects the
   * percentage deviation from `taxableAmountMC`.
   *
   * Tax year correction highlights take precedence: the ISIN cell turns yellow when a correction record exists
   * for this or the previous tax year, the `taxableAmountMC` cell turns green when it overrides the ICTax value
   * (TAXABLE_AMOUNT), and the `ictaxTotalPaymentValueChf` cell turns yellow when a directly entered value
   * replaced it (DIRECT_VALUE), suppressing the deviation shading.
   *
   * Reddish when the ICTax payment value is higher than the taxable amount, greenish when it is lower.
   * Both directions scale the same way: full intensity is reached at +/-20% deviation. When the two
   * values are equal (deviation 0) no background is applied. When `taxableAmountMC` is missing or zero
   * while the ICTax payment value is non-zero (e.g. reinvesting ETFs without dividend transactions),
   * the deviation is infinite, so the darkest red shade is applied.
   *
   * @param row the security dividends position for the current row
   * @param field the column configuration being rendered
   * @returns an ngStyle object for the cell
   */
  getCellStyle(row: SecurityDividendsPosition, field: ColumnConfig): { [key: string]: string } {
    const widthStyle = field.width ? {'flex-basis': '0 0 ' + field.width + 'px'} : {};
    if (field.field === 'security.name') {
      const color = this.getNameCellColor(row);
      return color ? {...widthStyle, 'background-color': color} : widthStyle;
    }
    if (field.field === 'security.isin' && row.taxYearCorrectionNearby) {
      return {...widthStyle, 'background-color': 'hsl(54, 100%, 78%)'};  // yellow — correction this or previous year
    }
    if (field.field === 'taxableAmountMC' && row.taxYearCorrectionType === 'TAXABLE_AMOUNT') {
      return {...widthStyle, 'background-color': 'hsl(120, 60%, 82%)'};  // green — this value overrides ICTax
    }
    if (field.field === 'ictaxTotalPaymentValueChf' && row.taxYearCorrectionType === 'DIRECT_VALUE') {
      return {...widthStyle, 'background-color': 'hsl(54, 100%, 78%)'};  // yellow — manually entered override
    }
    if (field.field === 'ictaxTotalPaymentValueChf'
      && row.ictaxTotalPaymentValueChf != null && row.ictaxTotalPaymentValueChf !== 0
      && (row.taxableAmountMC == null || row.taxableAmountMC === 0)) {
      return {...widthStyle, 'background-color': 'hsl(0, 70%, 80%)'};  // no taxable amount -> darkest red
    }
    if (field.field === 'ictaxTotalPaymentValueChf'
      && row.ictaxTotalPaymentValueChf != null
      && row.taxableAmountMC != null && row.taxableAmountMC !== 0) {
      const deviation = (row.ictaxTotalPaymentValueChf - row.taxableAmountMC) / row.taxableAmountMC;
      if (deviation === 0) {                                      // equal values -> no shading at all
        return widthStyle;
      }
      const maxDeviation = 0.20;                                  // +/-20% = full shade
      const intensity = Math.min(Math.abs(deviation), maxDeviation) / maxDeviation;
      const hue = deviation > 0 ? 0 : 120;                        // 0 = red (ictax higher), 120 = green (lower)
      const lightness = 95 - intensity * 15;                      // 95% (subtle) -> 80% (strong)
      return {...widthStyle, 'background-color': `hsl(${hue}, 70%, ${lightness}%)`};
    }
    return widthStyle;
  }

  /**
   * Returns the tooltip text for a data cell. All columns show the formatted cell value; the security name
   * column additionally appends the translated reason when its background is colored by a position lifecycle
   * state (opened, sold out, redeemed at maturity).
   *
   * @param row the security dividends position for the current row
   * @param field the column configuration being rendered
   * @returns the tooltip text
   */
  getCellTooltip(row: SecurityDividendsPosition, field: ColumnConfig): string {
    const value = this.getValueByPath(row, field);
    if (field.field === 'security.name') {
      const reasonKey = this.getNameColorReasonKey(row);
      if (reasonKey && this.nameColorReasons[reasonKey]) {
        return value + '\n' + this.nameColorReasons[reasonKey];
      }
    }
    if (field.field === 'security.isin' && row.taxYearCorrectionNearby) {
      return value + '\n' + (row.taxYearCorrectionNote || this.nameColorReasons['TAX_YEAR_CORRECTION_EXISTS'] || '');
    }
    if (row.taxYearCorrectionNote
      && ((field.field === 'taxableAmountMC' && row.taxYearCorrectionType === 'TAXABLE_AMOUNT')
        || (field.field === 'ictaxTotalPaymentValueChf' && row.taxYearCorrectionType === 'DIRECT_VALUE'))) {
      return value + '\n' + row.taxYearCorrectionNote;
    }
    return value;
  }

  /**
   * Rebuilds the security-row right-click context menu. Currently offers opening the tax year correction dialog
   * for the selected security (Swiss tenants only, gated via the table's contextMenu binding).
   */
  private buildContextMenu(): void {
    const menuItems: MenuItem[] = [{
      label: 'TAX_YEAR_CORRECTIONS' + BaseSettings.DIALOG_MENU_SUFFIX,
      command: () => this.openCorrectionDialog(this.contextMenuSelectedPosition)
    }];
    TranslateHelper.translateMenuItems(menuItems, this.translateService);
    this.contextMenuItems = menuItems;
  }

  onContextMenuSelect(event: any): void {
    this.contextMenuSelectedPosition = event.data;
    this.buildContextMenu();
  }

  /**
   * Opens the tax year correction maintenance dialog for the row's security. The dialog offers the report years
   * as selectable tax years for new records; the child table adds the years with ICTax data itself.
   *
   * @param sdp the security dividends position of the right-clicked row
   */
  openCorrectionDialog(sdp: SecurityDividendsPosition): void {
    if (!sdp) {
      return;
    }
    this.dialogSecurity = sdp.security;
    this.correctionYearOptions = this.securityDividendsGrandTotal.securityDividendsYearGroup
      .map(yg => yg.year).sort((a, b) => b - a);
    this.visibleCorrectionDialog = true;
  }

  /**
   * Closes the correction dialog and unconditionally notifies the parent so the whole dividends report is
   * reloaded. Corrections influence the ISIN/value cell highlights and the year sums, so a stale display after
   * any create/update/delete in the dialog must be avoided.
   *
   * @param processedActionData always UPDATED, emitted by the dialog on close
   */
  handleCorrectionDialogClose(processedActionData: ProcessedActionData): void {
    this.visibleCorrectionDialog = false;
    this.dateChanged.emit(new ProcessedActionData(ProcessedAction.UPDATED));
  }

  /**
   * Background color of the security name cell reflecting the position's lifecycle state in this year.
   * Priority: bond maturity redemption (burlywood) over zero balance at start and end of year (light pink)
   * over fully sold (yellow) over position opened (green).
   *
   * @param row the security dividends position for the current row
   * @returns a CSS color or null when no state applies
   */
  private getNameCellColor(row: SecurityDividendsPosition): string | null {
    switch (this.getNameColorReasonKey(row)) {
      case 'BOND_REDEEMED_MATURITY_YEAR':
        return '#FFD39B';                                // Burlywood 1 — bond redeemed at/after maturity
      case 'POSITION_ZERO_START_END_YEAR':
        return 'LightPink';                              // balance 0 at beginning and end of the year
      case 'POSITION_SOLD_OUT_YEAR':
        return 'hsl(54, 100%, 78%)';                     // yellow — position fully sold this year
      case 'POSITION_OPENED_YEAR':
        return 'hsl(120, 60%, 82%)';                     // green — position opened this year (0 -> X)
      default:
        return null;
    }
  }

  /**
   * Determines which lifecycle state applies to the row's name cell, in color priority order.
   *
   * @param row the security dividends position for the current row
   * @returns the NLS key of the applying state or null
   */
  private getNameColorReasonKey(row: SecurityDividendsPosition): string | null {
    if (row.redeemedAtMaturityInYear) {
      return 'BOND_REDEEMED_MATURITY_YEAR';
    }
    if (row.zeroUnitsAtStartAndEndOfYear) {
      return 'POSITION_ZERO_START_END_YEAR';
    }
    if (row.unitsAtEndOfYear != null && Math.abs(row.unitsAtEndOfYear) < 1e-8) {
      return 'POSITION_SOLD_OUT_YEAR';
    }
    if (row.positionOpenedInYear) {
      return 'POSITION_OPENED_YEAR';
    }
    return null;
  }

  /**
   * Resolves the asset class icon name for the row's security, shown in the instrument icon column.
   *
   * @param sdp the security dividends position for the current row
   * @param field the column configuration being rendered
   * @param valueField the raw value resolved from the field path (unused)
   * @returns the registered SVG icon name for the security's product type
   */
  getInstrumentIcon(sdp: SecurityDividendsPosition, field: ColumnConfig, valueField: any): string {
    return this.productIconService.getIconForInstrument(sdp.security, null);
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
