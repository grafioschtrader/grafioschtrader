import {Component, OnInit, ViewChild} from '@angular/core';
import {ButtonModule} from 'primeng/button';
import {ConfirmationService, FilterService, MenuItem} from 'primeng/api';
import {TranslateModule, TranslateService} from '@ngx-translate/core';
import {forkJoin} from 'rxjs';

import {RiskFreeInstrumentOption, RiskFreeRateMapping} from '../../../entities/risk.free.rate.mapping';
import {RiskFreeRateMappingService} from '../service/risk.free.rate.mapping.service';
import {GlobalparameterService} from '../../../lib/services/globalparameter.service';
import {GlobalparameterGTService} from '../../../gtservice/globalparameter.gt.service';
import {UserSettingsService} from '../../../lib/services/user.settings.service';
import {ActivePanelService} from '../../../lib/mainmenubar/service/active.panel.service';
import {IGlobalMenuAttach} from '../../../lib/mainmenubar/component/iglobal.menu.attach';
import {MessageToastService} from '../../../lib/message/message.toast.service';
import {InfoLevelType} from '../../../lib/message/info.leve.type';
import {DataType} from '../../../lib/dynamic-form/models/data.type';
import {TableEditConfigBase} from '../../../lib/datashowbase/table.edit.config.base';
import {EditableTableComponent, RowEditEvent, RowEditSaveEvent} from '../../../lib/datashowbase/editable-table.component';
import {ValueKeyHtmlSelectOptions} from '../../../lib/dynamic-form/models/value.key.html.select.options';
import {ColumnConfig} from '../../../lib/datashowbase/column.config';
import {GlobalSessionNames} from '../../../lib/global.session.names';
import {BaseSettings} from '../../../lib/base.settings';
import {AppHelper} from '../../../lib/helper/app.helper';
import {AppHelpIds} from '../../help/help.ids';

/**
 * Editable-table admin for the risk-free-rate currency-to-security mapping. Shows 3 columns:
 * <ol>
 *   <li>Currency code (editable dropdown — picks an ISO currency not yet used in another row)</li>
 *   <li>Risk-free instrument (editable dropdown — picks a Security in the seeded 'Risk-free rate' assetclass that is
 *       not yet used in another row)</li>
 *   <li>FRED series id (derived display — auto-filled from the picked instrument's urlHistoryExtend)</li>
 * </ol>
 *
 * <p>
 * All authenticated users can read and create. Update/delete is enabled per-row only when the current user is the
 * row's creator OR holds ROLE_ADMIN / ROLE_ALL_EDIT — no use of AuditHelper, just bare {@code createdBy} comparison.
 * The backend additionally enforces a daily CUD limit of 2 for ROLE_LIMIT_EDIT users; the toast shows the error if
 * exceeded.
 */
@Component({
  template: `
    <editable-table #entityTable
      [(data)]="entityList"
      [fields]="fields"
      dataKey="rowKey"
      [selectionMode]="'single'"
      [(selection)]="selectedEntity"
      [valueGetterFn]="getValueByPath.bind(this)"
      [baseLocale]="baseLocale"
      [customSortFn]="customSort.bind(this)"
      [createNewEntityFn]="createNewEntity"
      [canEditRowFn]="canEditOrDeleteRow"
      [canDeleteRowFn]="canEditOrDeleteRow"
      [contextMenuEnabled]="false"
      [contextMenuItems]="contextMenuItems"
      [showContextMenu]="isActivated()"
      [containerClass]="{'data-container': true, 'active-border': isActivated(), 'passiv-border': !isActivated()}"
      (rowEditSave)="onRowEditSave($event)"
      (rowDelete)="onRowDelete($event)"
      (componentClick)="onComponentClick()">
      <div caption style="display: flex; align-items: center;">
        <h6 style="margin: 0;">{{ 'RISK_FREE_RATE_MAPPING' | translate }}</h6>
        <p-button [rounded]="true" [text]="true"
                  (click)="entityTable.addNewRow()" [style]="{'margin-left': '0.5rem'}">
          <i class="pi pi-plus" pButtonIcon></i>
        </p-button>
      </div>
    </editable-table>
  `,
  standalone: true,
  imports: [EditableTableComponent, TranslateModule, ButtonModule]
})
export class RiskFreeRateMappingTableComponent extends TableEditConfigBase implements OnInit, IGlobalMenuAttach {

  static readonly I18N_RECORD = 'RISK_FREE_RATE_MAPPING';

  @ViewChild('entityTable') entityTable: EditableTableComponent<RiskFreeRateMapping>;

  contextMenuItems: MenuItem[] = [];
  entityList: RiskFreeRateMapping[] = [];
  selectedEntity: RiskFreeRateMapping;

  /** All ISO currency options as returned by GlobalparametersGT.getCurrencies(). */
  private allCurrencyOptions: ValueKeyHtmlSelectOptions[] = [];

  /** All risk-free instrument candidates (already filtered server-side to unmapped + RFR-assetclass). */
  private allInstrumentOptions: RiskFreeInstrumentOption[] = [];

  /** Lookup id_securitycurrency -> RiskFreeInstrumentOption (used to derive FRED series id for column 3). */
  private instrumentsById = new Map<number, RiskFreeInstrumentOption>();

  /** Id of the current authenticated user, for the per-row ownership check. */
  private currentUserId: number;

  private newRowCounter = 0;

  constructor(private activePanelService: ActivePanelService,
              private riskFreeRateMappingService: RiskFreeRateMappingService,
              private gpsGT: GlobalparameterGTService,
              private confirmationService: ConfirmationService,
              private messageToastService: MessageToastService,
              filterService: FilterService,
              translateService: TranslateService,
              gps: GlobalparameterService,
              usersettingsService: UserSettingsService) {
    super(filterService, usersettingsService, translateService, gps);

    this.currentUserId = +sessionStorage.getItem(GlobalSessionNames.ID_USER);

    // Column 1: currency dropdown — already-used filter applied per row.
    this.addEditColumnFeqH(DataType.String, 'currency', true, {width: 90});
    const currencyCol = this.getColumnConfigByField('currency');
    currencyCol.cec.optionsProviderFn = (row: RiskFreeRateMapping) => this.getCurrencyOptions(row);
    currencyCol.cec.canEditFn = (row: RiskFreeRateMapping) => !row.idRiskFreeRateMapping;

    // Column 2: instrument-picker dropdown.
    // - DataType.String (not NumericInteger): the editable-table picks the dropdown editor when DataType is String AND
    //   `optionsProviderFn` is set (`hasDropdownOptions`); NumericInteger would force a number input and right-align
    //   the cell — and the user never sees id_securitycurrency, only the security name.
    // - fieldValueFN resolves the underlying id_securitycurrency to the security name for display mode.
    this.addEditColumn(DataType.String, 'idSecuritycurrency',
      RiskFreeRateMappingTableComponent.I18N_RECORD + '_INSTRUMENT', true,
      {fieldValueFN: this.getInstrumentNameForRow.bind(this)});
    const instrumentCol = this.getColumnConfigByField('idSecuritycurrency');
    instrumentCol.cec.optionsProviderFn = (row: RiskFreeRateMapping) => this.getInstrumentOptions(row);
    // EditableTableComponent.updateDependentFields clears row.idSecuritycurrency and evicts the cached dropdown
    // options for the row when row.currency changes, so the instrument list re-resolves through optionsProviderFn.
    instrumentCol.cec.dependsOnField = 'currency';

    // Column 3: derived display of FRED series id. Non-editable; populated by fieldValueFN.
    this.addColumn(DataType.String, 'fredSeriesId', 'FRED_SERIES_ID', true, false,
      {fieldValueFN: this.getFredSeriesForRow.bind(this)});
  }

  isActivated(): boolean {
    return this.activePanelService.isActivated(this);
  }

  hideContextMenu(): void {
  }

  callMeDeactivate(): void {
  }

  getHelpContextId(): string {
    return AppHelpIds.HELP_BASEDATA_RISK_FREE_RATE_MAPPING;
  }

  ngOnInit(): void {
    this.readData();
    this.resetMenu();
  }

  private readData(): void {
    forkJoin([
      this.riskFreeRateMappingService.getAll(),
      this.riskFreeRateMappingService.getAllInstruments(),
      this.gpsGT.getCurrencies()
    ]).subscribe(([mappings, instruments, currencies]) => {
      this.entityList = (mappings || []).map(m => {
        (m as any).rowKey = `existing_${m.idRiskFreeRateMapping}`;
        return m;
      });
      this.allInstrumentOptions = instruments || [];
      this.allCurrencyOptions = currencies || [];
      this.rebuildInstrumentLookup();
      this.prepareTableAndTranslate();
    });
  }

  /**
   * Re-populates {@link instrumentsById} from {@link allInstrumentOptions}. Server returns all risk-free Securities
   * (including the already-mapped ones), so this lookup is sufficient for the name and FRED-series-id display columns
   * of every row.
   */
  private rebuildInstrumentLookup(): void {
    this.instrumentsById.clear();
    this.allInstrumentOptions.forEach(opt => this.instrumentsById.set(opt.idSecuritycurrency, opt));
  }

  // ============================================================================
  // Dropdown options
  // ============================================================================

  private getCurrencyOptions(row: RiskFreeRateMapping): ValueKeyHtmlSelectOptions[] {
    const usedCurrencies = new Set<string>(
      this.entityList
        .filter(m => m !== row && m.currency != null)
        .map(m => m.currency)
    );
    return this.allCurrencyOptions.filter(opt => !usedCurrencies.has(String(opt.key)));
  }

  private getInstrumentOptions(row: RiskFreeRateMapping): ValueKeyHtmlSelectOptions[] {
    if (!row.currency) {
      return [];
    }
    const usedIds = new Set<number>(
      this.entityList
        .filter(m => m !== row && m.idSecuritycurrency != null)
        .map(m => m.idSecuritycurrency)
    );
    return this.allInstrumentOptions
      .filter(opt => opt.currency === row.currency && !usedIds.has(opt.idSecuritycurrency))
      .map(opt => new ValueKeyHtmlSelectOptions(opt.idSecuritycurrency, opt.name));
  }

  // ============================================================================
  // Derived display: security name (column 2 display mode) + FRED series id (column 3)
  // ============================================================================

  private getInstrumentNameForRow(row: RiskFreeRateMapping, _field: ColumnConfig): string {
    const opt = row.idSecuritycurrency != null ? this.instrumentsById.get(row.idSecuritycurrency) : null;
    return opt ? opt.name : '';
  }

  private getFredSeriesForRow(row: RiskFreeRateMapping, _field: ColumnConfig): string {
    const opt = row.idSecuritycurrency != null ? this.instrumentsById.get(row.idSecuritycurrency) : null;
    return opt ? opt.urlHistoryExtend : '';
  }

  // ============================================================================
  // Per-row enable/disable
  // ============================================================================

  canEditOrDeleteRow = (row: RiskFreeRateMapping): boolean => {
    if (!row.idRiskFreeRateMapping) {
      // Unsaved/new rows are always editable by the row's author (current session).
      return true;
    }
    if (row.createdBy === this.currentUserId) {
      return true;
    }
    return this.gps.hasRole(BaseSettings.ROLE_ADMIN) || this.gps.hasRole(BaseSettings.ROLE_ALL_EDIT);
  };

  // ============================================================================
  // CRUD plumbing
  // ============================================================================

  createNewEntity = (): RiskFreeRateMapping => {
    const entity = new RiskFreeRateMapping();
    (entity as any).rowKey = `new_${this.newRowCounter++}`;
    return entity;
  };

  onRowEditSave(event: RowEditSaveEvent<RiskFreeRateMapping>): void {
    const entity = event.row;
    if (!entity.currency || !entity.idSecuritycurrency) {
      this.messageToastService.showMessageI18n(InfoLevelType.WARNING, 'REQUIRED_FIELDS_MISSING');
      return;
    }
    this.riskFreeRateMappingService.update(entity).subscribe({
      next: () => {
        this.messageToastService.showMessageI18n(InfoLevelType.SUCCESS,
          event.isNew ? 'MSG_RECORD_CREATED' : 'MSG_RECORD_SAVED',
          {i18nRecord: RiskFreeRateMappingTableComponent.I18N_RECORD});
        this.readData();
      }
    });
  }

  /** Trash button on a row was clicked. Routes through the same confirm + service.delete path as the context menu. */
  onRowDelete(event: RowEditEvent<RiskFreeRateMapping>): void {
    this.handleDeleteEntity(event.row);
  }

  onComponentClick(): void {
    this.resetMenu();
  }

  /**
   * Registers the panel with the main menu bar so the help-context wiring works. The edit-menu is intentionally empty
   * — row creation goes through the toolbar "+" button (caption slot of the editable-table), row deletion through
   * the per-row trash button rendered by EditableTableComponent.
   */
  private resetMenu(): void {
    this.contextMenuItems = [];
    this.activePanelService.activatePanel(this, {showMenu: null, editMenu: this.contextMenuItems});
  }

  private handleDeleteEntity(entity: RiskFreeRateMapping): void {
    if (!entity?.idRiskFreeRateMapping) {
      return;
    }
    AppHelper.confirmationDialog(this.translateService, this.confirmationService,
      'MSG_CONFIRM_DELETE_RECORD|' + RiskFreeRateMappingTableComponent.I18N_RECORD, () => {
        this.riskFreeRateMappingService.deleteEntity(entity.idRiskFreeRateMapping).subscribe(() => {
          this.messageToastService.showMessageI18n(InfoLevelType.SUCCESS, 'MSG_DELETE_RECORD',
            {i18nRecord: RiskFreeRateMappingTableComponent.I18N_RECORD});
          this.selectedEntity = null;
          this.readData();
        });
      });
  }
}
