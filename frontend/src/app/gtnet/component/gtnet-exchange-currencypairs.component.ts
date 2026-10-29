import { Component, signal, viewChild } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';

import { FormsModule } from '@angular/forms';
import { TranslateModule } from '@ngx-translate/core';
import { TableModule } from '@openng/optimus-ui/table';
import { ButtonModule } from '@openng/optimus-ui/button';
import { CheckboxModule } from '@openng/optimus-ui/checkbox';
import { TooltipModule } from '@openng/optimus-ui/tooltip';
import { ContextMenuModule } from '@openng/optimus-ui/contextmenu';
import { MenuItem } from '@openng/optimus-ui/api';
import { DialogService } from '@openng/optimus-ui/dynamicdialog';
import { InputTextModule } from '@openng/optimus-ui/inputtext';

import { GTNetExchangeBaseComponent } from './gtnet-exchange-base.component';
import { DataType } from '../../lib/dynamic-form/models/data.type';
import { FilterType } from '../../lib/datashowbase/filter.type';
import { CurrencypairEditComponent } from '../../shared/securitycurrency/currencypair-edit.component';
import { ProcessedActionData } from '../../lib/types/processed.action.data';
import { ProcessedAction } from '../../lib/types/processed.action';
import { Currencypair } from '../../entities/currencypair';
import { BaseSettings } from '../../lib/base.settings';
import { ConfigurableTableComponent } from '../../lib/datashowbase/configurable-table.component';
import { GTNetSupplierDetailTableComponent } from './gtnet-supplier-detail-table.component';
import { GTNetExchangeCheckboxesComponent, CheckboxToggleEvent } from './gtnet-exchange-checkboxes.component';
import { HelpIds } from '../../lib/help/help.ids';
import { Helper } from '../../lib/helper/helper';

/**
 * Component for configuring GTNet exchange settings for currency pairs.
 * Displays a table with currency pair information and 4 boolean checkboxes for exchange configuration.
 * Supports editing the underlying currency pair via a dialog.
 */
@Component({
  selector: 'gtnet-exchange-currencypairs',
  imports: [
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
    GTNetSupplierDetailTableComponent,
    GTNetExchangeCheckboxesComponent
  ],
  template: `
    <configurable-table
      [data]="entityList()"
      [fields]="fields"
      [fieldsRevision]="fieldsRevision()"
      dataKey="idSecuritycurrency"
      [selectionMode]="'single'"
      [(selection)]="selectedEntity"
      (selectionChange)="onSelectionChange($event)"
      [sortMode]="'multiple'"
      [multiSortMeta]="multiSortMeta"
      [enableCustomSort]="true"
      [customSortFn]="exchangeSorter"
      [paginator]="true"
      [rows]="rowsPerPage"
      [rowsPerPageOptions]="[20, 50, 100, 150]"
      [hasFilter]="true"
      (componentClick)="onComponentClick($event)"
      [contextMenuEnabled]="true"
      [contextMenuItems]="contextMenuItems()"
      [containerClass]="{
        'data-container-full': true,
        'active-border': isActivated(),
        'passiv-border': !isActivated()
      }"
      [showContextMenu]="true"
      [expandable]="true"
      [canExpandFn]="canExpand"
      [expandedRowTemplate]="expandedRow">
      <div caption class="caption-toolbar">
        <h4 class="caption-title">{{ getTitleKey() | translate }}</h4>
        <div class="caption-row">
          <gtnet-exchange-checkboxes [disabled]="!isUserAllowedToMultiSelect()" (toggle)="onCheckboxToggle($event)" />
        </div>
        <div class="caption-actions">
          <span class="p-input-icon-left caption-search">
            <i class="pi pi-search"></i>
            <input
              pInputText
              type="text"
              (input)="configurableTable().table().filterGlobal($any($event.target).value, 'contains')"
              [placeholder]="'SEARCH' | translate" />
          </span>
          <p-button [label]="'SAVE' | translate" (onClick)="saveChanges()" [disabled]="!hasUnsavedChanges()">
            <i class="pi pi-save" pButtonIcon></i>
          </p-button>
        </div>
      </div>

      <ng-template #customCell let-row let-field="field">
        @if (field.templateName === 'checkbox') {
          <p-checkbox
            [(ngModel)]="row[field.field]"
            [binary]="true"
            (onChange)="onCheckboxChange(row)"
            [disabled]="isCheckboxDisabled(row, field.field)" />
        } @else {
          <span [pTooltip]="getValueByPath(row, field)" tooltipPosition="top">
            {{ getValueByPath(row, field) }}
          </span>
        }
      </ng-template>

      <ng-template #expandedRow let-row>
        <gtnet-supplier-detail-table [idSecuritycurrency]="row.idSecuritycurrency" [dtype]="'C'" />
      </ng-template>
    </configurable-table>

    @if (visibleEditCurrencypairDialog()) {
      <currencypair-edit
        (closeDialog)="handleCloseEditCurrencypairDialog($event)"
        [securityCurrencypairCallParam]="currencypairCallParam"
        [visibleEditCurrencypairDialog]="visibleEditCurrencypairDialog()" />
    }
  `,
  styles: [
    `
      .caption-toolbar {
        display: flex;
        flex-direction: column;
        gap: 0.75rem;
        width: 100%;
      }

      .caption-title {
        margin: 0;
      }

      .caption-row {
        display: flex;
        align-items: center;
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
    `
  ],
  providers: [DialogService]
})
export class GTNetExchangeCurrencypairsComponent extends GTNetExchangeBaseComponent<Currencypair> {
  readonly configurableTable = viewChild.required(ConfigurableTableComponent);

  /** Visibility flag for currency pair edit dialog */
  readonly visibleEditCurrencypairDialog = signal(false);

  idSecuritycurreniesWithDetails: Set<number> = new Set();

  /** Currency pair to edit */
  currencypairCallParam: Currencypair;

  constructor() {
    super('Currencypair');
  }

  getTitleKey(): string {
    return 'GT_NET_EXCHANGE_CURRENCYPAIRS';
  }

  override getDType(): string {
    return 'C';
  }

  protected initializeColumns(): void {
    this.addColumnFeqH(DataType.String, 'name', true, false, {
      width: 150,
      filterType: FilterType.likeDataType
    });
    this.addCheckboxColumns();

    this.multiSortMeta.push({ field: 'name', order: 1 });
    this.prepareTableAndTranslate();
  }

  protected loadData(): void {
    this.gtNetExchangeService
      .getCurrencypairs()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((data) => {
        this.idSecuritycurreniesWithDetails = new Set(data.idSecuritycurrenies);
        this.modifiedItems.set(new Set());
        this.createTranslatedValueStoreAndFilterField(data.securitiescurrenciesList);
        this.entityList.set(data.securitiescurrenciesList);
        this.refreshSelectedEntity();
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
  protected override prepareEditMenu(entity: Currencypair): MenuItem[] | null {
    const menuItems: MenuItem[] = [];

    if (entity) {
      menuItems.push({
        label: 'EDIT_RECORD|CURRENCYPAIR' + BaseSettings.DIALOG_MENU_SUFFIX,
        command: () => this.editCurrencypair(entity)
      });
    }

    return menuItems.length > 0 ? menuItems : null;
  }

  /**
   * Opens the currency pair edit dialog.
   */
  editCurrencypair(entity: Currencypair): void {
    this.currencypairCallParam = entity;
    this.visibleEditCurrencypairDialog.set(true);
  }

  /**
   * Handle close of currency pair edit dialog.
   */
  handleCloseEditCurrencypairDialog(processedActionData: ProcessedActionData): void {
    this.visibleEditCurrencypairDialog.set(false);
    if (processedActionData.action !== ProcessedAction.NO_CHANGE) {
      // Reload data to reflect any changes
      this.loadData();
    }
  }

  /**
   * Wrapper for selection change to call resetMenu
   */
  onSelectionChange(selection: Currencypair | Currencypair[]): void {
    this.resetMenu(Helper.singleSelection(selection));
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
    return this.configurableTable();
  }

  /** Only instruments with supplier details have something to expand */
  readonly canExpand = (row: Currencypair): boolean => this.idSecuritycurreniesWithDetails.has(row.idSecuritycurrency);

  public override getHelpContextId(): string {
    return HelpIds.HELP_GT_NET_EXCHANGE;
  }
}
