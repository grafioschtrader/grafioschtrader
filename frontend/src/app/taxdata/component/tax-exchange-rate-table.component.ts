import { ChangeDetectionStrategy, Component, Input, OnChanges, SimpleChanges } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import { FilterService, MenuItem } from '@openng/optimus-ui/api';
import { EditInputType } from '../../lib/datashowbase/column.config';
import { DataType } from '../../lib/dynamic-form/models/data.type';
import { EditableTableComponent, RowEditSaveEvent } from '../../lib/datashowbase/editable-table.component';
import { TableEditConfigBase } from '../../lib/datashowbase/table.edit.config.base';
import { AuditHelper } from '../../lib/helper/audit.helper';
import { HelpIds } from '../../lib/help/help.ids';
import { IGlobalMenuAttach } from '../../lib/mainmenubar/component/iglobal.menu.attach';
import { ActivePanelService } from '../../lib/mainmenubar/service/active.panel.service';
import { InfoLevelType } from '../../lib/message/info.leve.type';
import { MessageToastService } from '../../lib/message/message.toast.service';
import { GlobalparameterService } from '../../lib/services/globalparameter.service';
import { UserSettingsService } from '../../lib/services/user.settings.service';
import { IctaxExchangeRate, TaxYear } from '../model/tax-data.model';
import { TaxDataService } from '../service/tax-data.service';

/**
 * The official exchange rates one tax year was imported with, shown below its node in the tax data tree.
 *
 * <p>The two published rates are read-only: they are what the tax authority declared and an import overwrites them
 * anyway. Only the two correction columns can be edited, so an administrator can replace a rate that is rounded too
 * coarsely to reproduce a figure of the tax statement. A correction survives a re-import of the Kursliste.</p>
 *
 * <p>Rows are never created or deleted here — the Kursliste import owns which currencies exist for a year.</p>
 */
@Component({
  selector: 'tax-exchange-rate-table',
  template: `
    <editable-table
      [data]="exchangeRates"
      [fields]="fields"
      dataKey="idIctaxExchangeRate"
      [selectionMode]="'single'"
      [(selection)]="selectedEntity"
      [showEditColumn]="isAdmin"
      [canDeleteRowFn]="neverDelete"
      [valueGetterFn]="getValueByPath.bind(this)"
      [customSortFn]="customSort.bind(this)"
      [baseLocale]="baseLocale"
      [numberLocale]="numberLocale"
      [scrollable]="false"
      [containerClass]="{
        'data-container': true,
        'active-border': isActivated(),
        'passiv-border': !isActivated()
      }"
      (rowEditSave)="onRowEditSave($event)"
      (rowSelect)="onRowSelect($event)"
      (rowUnselect)="onRowUnselect($event)"
      (componentClick)="onComponentClick($event)">
    </editable-table>
  `,
  standalone: true,
  changeDetection: ChangeDetectionStrategy.Eager,
  imports: [EditableTableComponent]
})
export class TaxExchangeRateTableComponent extends TableEditConfigBase implements OnChanges, IGlobalMenuAttach {
  @Input() taxYear: TaxYear;

  contextMenuItems: MenuItem[] = [];
  exchangeRates: IctaxExchangeRate[] = [];
  selectedEntity: IctaxExchangeRate;
  isAdmin: boolean;

  /** User locale (e.g. 'de-CH') so the correction inputs use the same decimal separator as the rest of GT. */
  numberLocale: string;

  /** The Kursliste publishes up to ten decimals, for example USD 0.8306517857 as annual mean. */
  private static readonly RATE_FRACTION_DIGITS = 10;

  private readonly ICTAX_EXCHANGE_RATE = 'ICTAX_EXCHANGE_RATE';

  constructor(
    private activePanelService: ActivePanelService,
    private taxDataService: TaxDataService,
    private messageToastService: MessageToastService,
    filterService: FilterService,
    translateService: TranslateService,
    gps: GlobalparameterService,
    usersettingsService: UserSettingsService
  ) {
    super(filterService, usersettingsService, translateService, gps);
    this.isAdmin = AuditHelper.hasAdminRole(gps);
    this.numberLocale = gps.getLocale();

    this.addColumnFeqH(DataType.String, 'currency', true, false, { width: 90 });
    this.addColumnFeqH(DataType.NumericInteger, 'denomination', true, false, { width: 110 });
    // NumericRaw rather than Numeric: a rate must be shown with every published decimal and without padding,
    // whereas Numeric shortens 0.8306517857 to five decimals. The header keys are explicit because
    // AppHelper.fieldToLabelRegex strips a leading "year", which would turn yearEndRate into END_RATE.
    this.addColumn(DataType.NumericRaw, 'yearEndRate', 'YEAR_END_RATE', true, false, { width: 170 });
    this.addColumn(DataType.NumericRaw, 'annualMeanRate', 'ANNUAL_MEAN_RATE', true, false, { width: 170 });
    this.addRateOverrideColumn('yearEndRateOverride', 'YEAR_END_RATE_OVERRIDE');
    this.addRateOverrideColumn('annualMeanRateOverride', 'ANNUAL_MEAN_RATE_OVERRIDE');
    this.prepareTableAndTranslate();
  }

  /**
   * Adds one editable correction column. NumericRaw has no edit input of its own, so the numeric editor is requested
   * explicitly and given the full precision the tax authority publishes.
   *
   * @param field - the entity property holding the correction
   * @param headerKey - the NLS key of the column header
   */
  private addRateOverrideColumn(field: string, headerKey: string): void {
    const cc = this.addEditColumn(DataType.NumericRaw, field, headerKey, false, { width: 190 });
    cc.cec.inputType = EditInputType.InputNumber;
    cc.cec.maxFractionDigits = TaxExchangeRateTableComponent.RATE_FRACTION_DIGITS;
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['taxYear'] && this.taxYear) {
      this.readData();
    }
  }

  // ============================================================================
  // IGlobalMenuAttach Implementation
  // ============================================================================

  isActivated(): boolean {
    return this.activePanelService.isActivated(this);
  }

  hideContextMenu(): void {}

  callMeDeactivate(): void {}

  getHelpContextId(): string {
    return HelpIds.HELP_TAX_DATA;
  }

  /**
   * Marks the click as handled so the surrounding tax data tree does not replace this table's panel registration.
   *
   * @param event - the DOM click event bubbling up from the table
   */
  onComponentClick(event: any): void {
    event[this.consumedGT] = true;
    this.resetMenu();
  }

  onRowSelect(event: any): void {
    this.resetMenu();
  }

  onRowUnselect(event: any): void {
    this.resetMenu();
  }

  /** Deleting a rate is never offered — the Kursliste import decides which currencies a year has. */
  neverDelete = (): boolean => false;

  /**
   * Persists the two correction values of one row. The published rates are not sent back as changeable data; the
   * backend accepts the overrides only.
   *
   * @param event - the row the user finished editing
   */
  onRowEditSave(event: RowEditSaveEvent<IctaxExchangeRate>): void {
    this.taxDataService.updateExchangeRate(event.row).subscribe({
      next: () => {
        this.messageToastService.showMessageI18n(InfoLevelType.SUCCESS, 'MSG_RECORD_SAVED', {
          i18nRecord: this.ICTAX_EXCHANGE_RATE
        });
        this.readData();
      }
    });
  }

  private readData(): void {
    this.taxDataService.getExchangeRates(this.taxYear.idTaxYear).subscribe((rates: IctaxExchangeRate[]) => {
      this.exchangeRates = rates;
      this.createTranslatedValueStore(this.exchangeRates);
    });
  }

  private resetMenu(): void {
    this.activePanelService.activatePanel(this, {
      showMenu: null,
      editMenu: this.contextMenuItems
    });
  }
}
