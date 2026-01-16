import {Component, Injector, ViewChild} from '@angular/core';
import {CommonModule} from '@angular/common';
import {FormsModule} from '@angular/forms';
import {TranslateModule, TranslateService} from '@ngx-translate/core';
import {TableModule} from 'primeng/table';
import {ButtonModule} from 'primeng/button';
import {CheckboxModule} from 'primeng/checkbox';
import {TooltipModule} from 'primeng/tooltip';
import {ContextMenuModule} from 'primeng/contextmenu';
import {ConfirmationService, FilterService, MenuItem} from 'primeng/api';
import {DialogService} from 'primeng/dynamicdialog';
import {InputTextModule} from 'primeng/inputtext';

import {GTNetExchangeBaseComponent} from './gtnet-exchange-base.component';
import {GTNetExchangeService} from '../service/gtnet-exchange.service';
import {GlobalparameterService} from '../../lib/services/globalparameter.service';
import {UserSettingsService} from '../../lib/services/user.settings.service';
import {ActivePanelService} from '../../lib/mainmenubar/service/active.panel.service';
import {MessageToastService} from '../../lib/message/message.toast.service';
import {DataType} from '../../lib/dynamic-form/models/data.type';
import {TranslateValue} from '../../lib/datashowbase/column.config';
import {FilterType} from '../../lib/datashowbase/filter.type';
import {AppSettings} from '../../shared/app.settings';
import {SecurityEditComponent} from '../../shared/securitycurrency/security-edit.component';
import {ProcessedActionData} from '../../lib/types/processed.action.data';
import {ProcessedAction} from '../../lib/types/processed.action';
import {Security} from '../../entities/security';
import {TranslateHelper} from '../../lib/helper/translate.helper';
import {BaseSettings} from '../../lib/base.settings';
import {ConfigurableTableComponent} from '../../lib/datashowbase/configurable-table.component';
import {GTNetSupplierDetailTableComponent} from './gtnet-supplier-detail-table.component';
import {GTNetExchangeCheckboxesComponent, CheckboxToggleEvent} from './gtnet-exchange-checkboxes.component';
import {HelpIds} from '../../lib/help/help.ids';

/**
 * Component for configuring GTNet exchange settings for securities.
 * Displays a table with security information and 4 boolean checkboxes for exchange configuration.
 * Supports editing the underlying security via a dialog.
 */
@Component({
  selector: 'gtnet-exchange-securities',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    TranslateModule,
    TableModule,
    ButtonModule,
    CheckboxModule,
    TooltipModule,
    ContextMenuModule,
    InputTextModule,
    SecurityEditComponent,
    ConfigurableTableComponent,
    GTNetSupplierDetailTableComponent,
    GTNetExchangeCheckboxesComponent
  ],
  template: `
    <configurable-table
      [data]="entityList"
      [fields]="fields"
      dataKey="idSecuritycurrency"
      [selectionMode]="'single'"
      [(selection)]="selectedEntity"
      (selectionChange)="onSelectionChange($event)"
      [sortMode]="'multiple'"
      [multiSortMeta]="multiSortMeta"
      [enableCustomSort]="true"
      [customSortFn]="doCustomSort.bind(this)"
      [paginator]="true"
      [rows]="rowsPerPage"
      [rowsPerPageOptions]="[20, 50, 100, 150]"
      [hasFilter]="true"
      (componentClick)="onComponentClick($event)"
      [contextMenuEnabled]="true"
      [contextMenuItems]="contextMenuItems"
      [showContextMenu]="true"
      [containerClass]="{'data-container-full': true, 'active-border': isActivated(), 'passiv-border': !isActivated()}"
      [expandable]="true"
      [canExpandFn]="canExpand.bind(this)"
      [expandedRowTemplate]="expandedRow">

      <div caption class="flex justify-content-between align-items-center w-full">
        <h4>{{ getTitleKey() | translate }}</h4>
        <div class="flex align-items-center gap-2">
          <gtnet-exchange-checkboxes
            [disabled]="!isUserAllowedToMultiSelect()"
            (toggle)="onCheckboxToggle($event)">
          </gtnet-exchange-checkboxes>
        </div>
        <div class="flex align-items-center gap-2 w-full"
             style="display: flex; width: 100%; margin-bottom: 0.75rem">
          <div class="flex align-items-center gap-2 mr-3 surface-border w-full"
               style="display: flex; width: 100%">
            <i class="pi pi-search"></i>
            <input pInputText type="text" class="w-full"
                   style="flex: 1 1 auto; width: 100%"
                   (input)="configurableTable.table.filterGlobal($any($event.target).value, 'contains')"
                   [placeholder]="'SEARCH' | translate"/>
          </div>
        </div>
        <div class="flex justify-content-between align-items-center w-full"
             style="display: flex; justify-content: space-between; align-items: center; width: 100%">
          <div class="flex align-items-center gap-2" style="display: flex; align-items: center; gap: 0.5rem">
            <p-checkbox [(ngModel)]="activeOnly" [binary]="true"
                        (onChange)="loadData()" inputId="activeOnly"></p-checkbox>
            <label for="activeOnly" style="margin-bottom: 0">{{ 'ACTIVE_NOW_SECURITIES' | translate }}</label>
          </div>
          <p-button icon="pi pi-save"
                    [label]="'SAVE' | translate"
                    (onClick)="saveChanges()"
                    [disabled]="!hasUnsavedChanges()"></p-button>
        </div>
      </div>

      <ng-template #customCell let-row let-field="field">
        @if (field.templateName === 'checkbox') {
          <p-checkbox [(ngModel)]="row[field.field]" [binary]="true"
                      (onChange)="onCheckboxChange(row)"
                      [disabled]="isCheckboxDisabled(row, field.field)"></p-checkbox>
        } @else {
          <span [pTooltip]="getValueByPath(row, field)" tooltipPosition="top">
              {{ getValueByPath(row, field) }}
           </span>
        }
      </ng-template>

      <ng-template #expandedRow let-row>
        <gtnet-supplier-detail-table [idSecuritycurrency]="row.idSecuritycurrency" [dtype]="'S'">
        </gtnet-supplier-detail-table>
      </ng-template>

    </configurable-table>

    @if (visibleEditSecurityDialog) {
      <security-edit (closeDialog)="handleCloseEditSecurityDialog($event)"
                     [securityCurrencypairCallParam]="securityCallParam"
                     [visibleEditSecurityDialog]="visibleEditSecurityDialog">
      </security-edit>
    }
  `,
  styles: [],
  providers: [DialogService]
})
export class GTNetExchangeSecuritiesComponent extends GTNetExchangeBaseComponent<Security> {

  @ViewChild(ConfigurableTableComponent) configurableTable: ConfigurableTableComponent;

  /** Filter to show only active securities */
  activeOnly = true;

  idSecuritycurreniesWithDetails: Set<number> = new Set();

  /** Visibility flag for security edit dialog */
  visibleEditSecurityDialog = false;

  /** Security to edit */
  securityCallParam: Security;

  constructor(
    gtNetExchangeService: GTNetExchangeService,
    confirmationService: ConfirmationService,
    messageToastService: MessageToastService,
    activePanelService: ActivePanelService,
    dialogService: DialogService,
    filterService: FilterService,
    translateService: TranslateService,
    gps: GlobalparameterService,
    usersettingsService: UserSettingsService,
    injector: Injector
  ) {
    super('Security', gtNetExchangeService, confirmationService, messageToastService, activePanelService,
      dialogService, filterService, translateService, gps, usersettingsService, injector);
  }

  getTitleKey(): string {
    return 'GT_NET_EXCHANGE_SECURITIES';
  }

  override getDType(): string {
    return 'S';
  }

  protected initializeColumns(): void {
    this.addColumnFeqH(DataType.String, 'name', true, false,
      {width: 200, filterType: FilterType.likeDataType});
    this.addColumn(DataType.String, 'isin', 'ISIN', true, false,
      {width: 120, filterType: FilterType.likeDataType});
    this.addColumn(DataType.String, 'tickerSymbol', 'SYMBOL', true, false,
      {width: 80, filterType: FilterType.likeDataType});
    this.addColumn(DataType.String, 'currency', 'CURRENCY', true, false, {width: 60});
    this.addColumn(DataType.DateString, 'activeToDate', 'ACTIVE_TO_DATE', true, false,
      {width: 100, filterType: FilterType.likeDataType});
    this.addColumn(DataType.String, 'assetClass.categoryType', AppSettings.ASSETCLASS.toUpperCase(), true, true,
      {translateValues: TranslateValue.NORMAL, width: 100, filterType: FilterType.likeDataType});
    this.addColumn(DataType.String, 'assetClass.specialInvestmentInstrument', 'FINANCIAL_INSTRUMENT',
      true, false, {width: 150, translateValues: TranslateValue.NORMAL, filterType: FilterType.likeDataType});
    this.addCheckboxColumns();

    this.multiSortMeta.push({field: 'name', order: 1});
    this.prepareTableAndTranslate();
  }

  protected loadData(): void {
    this.gtNetExchangeService.getSecurities(this.activeOnly).subscribe(data => {
      this.entityList = data.securitiescurrenciesList;
      this.idSecuritycurreniesWithDetails = new Set(data.idSecuritycurrenies);
      this.modifiedItems.clear();
      this.createTranslatedValueStoreAndFilterField(this.entityList);
    });
  }

  /**
   * Save modified securities via batch update.
   */
  saveChanges(): void {
    const modifiedList = this.getModifiedList();
    if (modifiedList.length > 0) {
      this.gtNetExchangeService.batchUpdateSecurities(modifiedList).subscribe({
        next: (updatedItems) => this.handleSaveSuccess(updatedItems),
        error: () => this.handleSaveError()
      });
    }
  }

  /**
   * For securities: disable intraday fields if security is inactive.
   */
  override isCheckboxDisabled(item: Security, field: string): boolean {
    if (field === 'gtNetLastpriceRecv' || field === 'gtNetLastpriceSend') {
      const activeToDate = item.activeToDate;
      if (activeToDate) {
        const today = new Date();
        const endDate = new Date(activeToDate);
        return endDate < today;
      }
    }
    return false;
  }

  /**
   * Override to build edit menu with security edit option.
   */
  protected override prepareEditMenu(entity: Security): MenuItem[] {
    const menuItems: MenuItem[] = [];

    if (entity) {
      menuItems.push({
        label: 'EDIT_RECORD|SECURITY' + BaseSettings.DIALOG_MENU_SUFFIX,
        command: () => this.editSecurity(entity)
      });
    }

    TranslateHelper.translateMenuItems(menuItems, this.translateService);
    return menuItems.length > 0 ? menuItems : null;
  }

  /**
   * Opens the security edit dialog.
   */
  editSecurity(entity: Security): void {
    this.securityCallParam = entity;
    this.visibleEditSecurityDialog = true;
  }

  /**
   * Handle close of security edit dialog.
   */
  handleCloseEditSecurityDialog(processedActionData: ProcessedActionData): void {
    this.visibleEditSecurityDialog = false;
    if (processedActionData.action !== ProcessedAction.NO_CHANGE) {
      // Reload data to reflect any changes
      this.loadData();
    }
  }

  /**
   * Handles toggle events from the shared checkbox component.
   */
  onCheckboxToggle(event: CheckboxToggleEvent): void {
    this.toggleColumn(event.field, event.event);
  }

  /**
   * Returns the ConfigurableTableComponent reference for the base class.
   */
  override getConfigurableTable(): ConfigurableTableComponent {
    return this.configurableTable;
  }

  /**
   * Wrapper for selection change to call resetMenu
   */
  onSelectionChange(entity: Security): void {
    this.resetMenu(entity);
  }

  canExpand(row: Security): boolean {
    return this.idSecuritycurreniesWithDetails.has(row.idSecuritycurrency);
  }

  public override getHelpContextId(): string {
    return HelpIds.HELP_GT_NET_EXCHANGE;
  }

}
