import {Component, ViewChild} from '@angular/core';
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
import {FilterType} from '../../lib/datashowbase/filter.type';
import {CurrencypairEditComponent} from '../../shared/securitycurrency/currencypair-edit.component';
import {ProcessedActionData} from '../../lib/types/processed.action.data';
import {ProcessedAction} from '../../lib/types/processed.action';
import {Currencypair} from '../../entities/currencypair';
import {TranslateHelper} from '../../lib/helper/translate.helper';
import {BaseSettings} from '../../lib/base.settings';
import {ConfigurableTableComponent} from '../../lib/datashowbase/configurable-table.component';
import {GTNetSupplierDetailTableComponent} from './gtnet-supplier-detail-table.component';
import {HelpIds} from '../../lib/help/help.ids';


/**
 * Component for configuring GTNet exchange settings for currency pairs.
 * Displays a table with currency pair information and 4 boolean checkboxes for exchange configuration.
 * Supports editing the underlying currency pair via a dialog.
 */
@Component({
  selector: 'gtnet-exchange-currencypairs',
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
    CurrencypairEditComponent,
    ConfigurableTableComponent,
    GTNetSupplierDetailTableComponent
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
      [containerClass]="{'data-container-full': true, 'active-border': isActivated(), 'passiv-border': !isActivated()}"
      [showContextMenu]="true"
      [expandable]="true"
      [canExpandFn]="canExpand.bind(this)"
      [expandedRowTemplate]="expandedRow">

      <div caption class="caption-toolbar">
        <h4 class="caption-title">{{ getTitleKey() | translate }}</h4>
        <div class="caption-actions">
              <span class="p-input-icon-left caption-search">
                <i class="pi pi-search"></i>
                <input pInputText type="text"
                       (input)="configurableTable.table.filterGlobal($any($event.target).value, 'contains')"
                       [placeholder]="'SEARCH' | translate" />
              </span>
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
        <gtnet-supplier-detail-table [idSecuritycurrency]="row.idSecuritycurrency" [dtype]="'C'">
        </gtnet-supplier-detail-table>
      </ng-template>

    </configurable-table>

    @if (visibleEditCurrencypairDialog) {
      <currencypair-edit (closeDialog)="handleCloseEditCurrencypairDialog($event)"
                         [securityCurrencypairCallParam]="currencypairCallParam"
                         [visibleEditCurrencypairDialog]="visibleEditCurrencypairDialog">
      </currencypair-edit>
    }
  `,
  styles: [`
    .caption-toolbar {
      display: flex;
      flex-direction: column;
      gap: 0.75rem;
      width: 100%;
    }

    .caption-title {
      margin: 0;
    }

    .caption-actions {
      display: flex;
      justify-content: flex-end;
      align-items: center;
      gap: 0.75rem;
      flex-wrap: nowrap;
      width: 100%;
    }

    .caption-search {
      display: flex;
      align-items: center;
      gap: 0.5rem;
      flex: 1;
      min-width: 0;
    }

    .caption-search i {
      flex: 0 0 auto;
    }

    .caption-search input {
      flex: 1;
      min-width: 0;
      width: 100%;
    }

    @media (max-width: 768px) {
      .caption-actions {
        flex-wrap: wrap;
        row-gap: 0.5rem;
        justify-content: flex-start;
      }

      .caption-search {
        width: 100%;
      }
    }
  `],
  providers: [DialogService]
})
export class GTNetExchangeCurrencypairsComponent extends GTNetExchangeBaseComponent<Currencypair> {

  @ViewChild(ConfigurableTableComponent) configurableTable: ConfigurableTableComponent;

  /** Visibility flag for currency pair edit dialog */
  visibleEditCurrencypairDialog = false;

  idSecuritycurreniesWithDetails: Set<number> = new Set();

  /** Currency pair to edit */
  currencypairCallParam: Currencypair;

  constructor(
    gtNetExchangeService: GTNetExchangeService,
    confirmationService: ConfirmationService,
    messageToastService: MessageToastService,
    activePanelService: ActivePanelService,
    dialogService: DialogService,
    filterService: FilterService,
    translateService: TranslateService,
    gps: GlobalparameterService,
    usersettingsService: UserSettingsService
  ) {
    super('Currencypair', gtNetExchangeService, confirmationService, messageToastService, activePanelService,
      dialogService, filterService, translateService, gps, usersettingsService);
  }

  getTitleKey(): string {
    return 'GT_NET_EXCHANGE_CURRENCYPAIRS';
  }

  override getDType(): string {
    return 'C';
  }

  protected initializeColumns(): void {
    this.addColumnFeqH(DataType.String, 'name', true, false,
      {width: 150, filterType: FilterType.likeDataType});
    this.addCheckboxColumns();

    this.multiSortMeta.push({field: 'name', order: 1});
    this.prepareTableAndTranslate();
  }

  protected loadData(): void {
    this.gtNetExchangeService.getCurrencypairs().subscribe(data => {
      this.entityList = data.securitiescurrenciesList;
      this.idSecuritycurreniesWithDetails = new Set(data.idSecuritycurrenies);
      this.modifiedItems.clear();
      this.createTranslatedValueStoreAndFilterField(this.entityList);
    });
  }

  /**
   * Save modified currency pairs via batch update.
   */
  saveChanges(): void {
    const modifiedList = this.getModifiedList();
    if (modifiedList.length > 0) {
      this.gtNetExchangeService.batchUpdateCurrencypairs(modifiedList).subscribe({
        next: (updatedItems) => this.handleSaveSuccess(updatedItems),
        error: () => this.handleSaveError()
      });
    }
  }

  /**
   * Override to build edit menu with currency pair edit option.
   */
  protected override prepareEditMenu(entity: Currencypair): MenuItem[] {
    const menuItems: MenuItem[] = [];

    if (entity) {
      menuItems.push({
        label: 'EDIT_RECORD|CURRENCYPAIR' + BaseSettings.DIALOG_MENU_SUFFIX,
        command: () => this.editCurrencypair(entity)
      });
    }

    TranslateHelper.translateMenuItems(menuItems, this.translateService);
    return menuItems.length > 0 ? menuItems : null;
  }

  /**
   * Opens the currency pair edit dialog.
   */
  editCurrencypair(entity: Currencypair): void {
    this.currencypairCallParam = entity;
    this.visibleEditCurrencypairDialog = true;
  }

  /**
   * Handle close of currency pair edit dialog.
   */
  handleCloseEditCurrencypairDialog(processedActionData: ProcessedActionData): void {
    this.visibleEditCurrencypairDialog = false;
    if (processedActionData.action !== ProcessedAction.NO_CHANGE) {
      // Reload data to reflect any changes
      this.loadData();
    }
  }

  /**
   * Wrapper for selection change to call resetMenu
   */
  onSelectionChange(entity: Currencypair): void {
    this.resetMenu(entity);
  }

  canExpand(row: Currencypair): boolean {
    return this.idSecuritycurreniesWithDetails.has(row.idSecuritycurrency);
  }

  public override getHelpContextId(): string {
    return HelpIds.HELP_GT_NET_EXCHANGE;
  }

}
