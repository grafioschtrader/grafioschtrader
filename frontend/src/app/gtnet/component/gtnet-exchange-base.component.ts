import {Directive, Injector, OnDestroy, OnInit, ViewChild} from '@angular/core';
import {TranslateService} from '@ngx-translate/core';
import {ConfirmationService, FilterService, MenuItem, SortEvent} from 'primeng/api';
import {DialogService} from 'primeng/dynamicdialog';

import {CrudMenuOptions, TableCrudSupportMenu} from '../../lib/datashowbase/table.crud.support.menu';
import {GlobalparameterService} from '../../lib/services/globalparameter.service';
import {UserSettingsService} from '../../lib/services/user.settings.service';
import {GTNetExchangeFields} from '../../lib/gnet/model/gtnet';
import {GTNetExchangeService} from '../service/gtnet-exchange.service';
import {DataType} from '../../lib/dynamic-form/models/data.type';
import {HelpIds} from '../../lib/help/help.ids';
import {ActivePanelService} from '../../lib/mainmenubar/service/active.panel.service';
import {MessageToastService} from '../../lib/message/message.toast.service';
import {InfoLevelType} from '../../lib/message/info.leve.type';
import {Securitycurrency} from '../../entities/securitycurrency';
import {ConfigurableTableComponent} from '../../lib/datashowbase/configurable-table.component';

/**
 * Abstract base component for GTNet exchange configuration tables.
 * Provides common functionality for both securities and currency pairs tables.
 * Now works directly with Security and Currencypair entities that have GTNet fields.
 *
 * Shared functionality includes:
 * - Table configuration with checkbox columns for boolean flags
 * - Batch save functionality
 * - Modified items tracking
 * - Custom multi-column sorting
 * - Context menu handling
 */
@Directive()
export abstract class GTNetExchangeBaseComponent<T extends Securitycurrency & GTNetExchangeFields>
  extends TableCrudSupportMenu<T> implements OnInit, OnDestroy {

  /** Set of modified item IDs for batch save tracking */
  modifiedItems: Set<number> = new Set();

  /** Tracks if any saves occurred during component lifetime for triggering sync on destroy */
  private savesOccurred: boolean = false;

  /** Entity for dialog calls (required by base class) */
  callParam: T;

  @ViewChild('cmDiv') contextMenuDiv: any;

  protected constructor(
    entityName: string,
    protected gtNetExchangeService: GTNetExchangeService,
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
    super(entityName, null, confirmationService, messageToastService, activePanelService,
      dialogService, filterService, translateService, gps, usersettingsService, injector, [CrudMenuOptions.ParentControl]);
    this.rowsPerPage = 50;
  }

  override ngOnInit(): void {
    this.initializeColumns();
    this.loadData();
  }

  /**
   * Initialize table columns. Must be implemented by derived classes.
   */
  protected abstract initializeColumns(): void;

  /**
   * Load data from the backend. Must be implemented by derived classes.
   */
  protected abstract loadData(): void;

  /**
   * Get the title translation key for the table caption.
   */
  abstract getTitleKey(): string;

  /**
   * Get the discriminator type for the security currency.
   */
  abstract getDType(): string;

  /**
   * Add the 4 common checkbox columns for GTNet exchange boolean flags.
   * Uses the new field names directly on the entity.
   */
  protected addCheckboxColumns(): void {
    this.addColumnFeqH(DataType.Boolean, 'gtNetLastpriceRecv', true, false,
      {width: 80, templateName: 'checkbox'});
    this.addColumnFeqH(DataType.Boolean, 'gtNetHistoricalRecv', true, false,
      {width: 80, templateName: 'checkbox'});
    this.addColumnFeqH(DataType.Boolean, 'gtNetLastpriceSend', true, false,
      {width: 80, templateName: 'checkbox'});
    this.addColumnFeqH(DataType.Boolean, 'gtNetHistoricalSend', true, false,
      {width: 80, templateName: 'checkbox'});
  }

  /**
   * Handle checkbox value change - marks item as modified.
   * Tracks by idSecuritycurrency directly on the entity.
   */
  onCheckboxChange(item: T): void {
    this.modifiedItems.add(item.idSecuritycurrency);
  }

  /**
   * Check if a checkbox should be disabled.
   * Override in derived classes for specific logic.
   */
  isCheckboxDisabled(item: T, field: string): boolean {
    return false;
  }

  /**
   * Get the list of modified entities for batch save.
   */
  protected getModifiedList(): T[] {
    return this.entityList.filter(item => this.modifiedItems.has(item.idSecuritycurrency));
  }

  /**
   * Save all modified items via batch update.
   * Must be implemented by derived classes as securities and currency pairs use different endpoints.
   */
  abstract saveChanges(): void;

  /**
   * Handle successful save response.
   */
  protected handleSaveSuccess(updatedItems: T[]): void {
    // Update entityList with returned items
    for (const updated of updatedItems) {
      const index = this.entityList.findIndex(e => e.idSecuritycurrency === updated.idSecuritycurrency);
      if (index >= 0) {
        this.entityList[index] = updated;
      }
    }
    this.modifiedItems.clear();
    this.savesOccurred = true;
    this.messageToastService.showMessageI18n(InfoLevelType.SUCCESS, 'GT_NET_EXCHANGE_SAVE_SUCCESS');
  }

  /**
   * Handle save error.
   */
  protected handleSaveError(): void {
    this.messageToastService.showMessageI18n(InfoLevelType.ERROR, 'SAVE_ERROR');
  }

  /**
   * Check if there are unsaved changes.
   */
  hasUnsavedChanges(): boolean {
    return this.modifiedItems.size > 0;
  }

  /**
   * Custom sort handler for multi-column sorting.
   */
  doCustomSort(event: SortEvent): void {
    event.data.sort((data1, data2) => {
      for (const sortMeta of event.multiSortMeta) {
        const field = this.fields.find(f => f.field === sortMeta.field);
        const value1 = this.getValueByPath(data1, field);
        const value2 = this.getValueByPath(data2, field);

        let result = 0;
        if (value1 == null && value2 != null) {
          result = -1;
        } else if (value1 != null && value2 == null) {
          result = 1;
        } else if (value1 != null && value2 != null) {
          if (typeof value1 === 'string' && typeof value2 === 'string') {
            result = value1.localeCompare(value2);
          } else {
            result = (value1 < value2) ? -1 : (value1 > value2) ? 1 : 0;
          }
        }

        if (result !== 0) {
          return sortMeta.order * result;
        }
      }
      return 0;
    });
  }

  /**
   * Get CSS class for table cell based on data type.
   */
  getCellClass(field: any): string {
    return (field.dataType === DataType.Numeric || field.dataType === DataType.DateTimeNumeric) ? 'text-end' : '';
  }

  override prepareCallParam(entity: T): void {
    this.callParam = entity;
  }

  protected override readData(): void {
    this.loadData();
  }

  public override getHelpContextId(): string {
    return HelpIds.HELP_GT_NET;
  }

  /**
   * Override to build edit menu. Can be overridden by derived classes.
   */
  protected override prepareEditMenu(entity: T): MenuItem[] {
    return null;
  }

  /**
   * Reset menu based on selection. Can be overridden by derived classes.
   */
  protected override resetMenu(entity: T): void {
    this.selectedEntity = entity;
    this.contextMenuItems = this.prepareEditMenu(this.selectedEntity);
    this.activePanelService.activatePanel(this, {
      editMenu: this.contextMenuItems
    });
  }

  /**
   * Check if the current user has ROLE_ADMIN or ROLE_ALL_EDIT.
   */
  isUserAllowedToMultiSelect(): boolean {
    return this.gps.hasRole('ROLE_ADMIN') || this.gps.hasRole('ROLE_ALL_EDIT');
  }

  /**
   * Get the ConfigurableTableComponent reference. Must be provided by derived classes.
   */
  abstract getConfigurableTable(): ConfigurableTableComponent;

  /**
   * Toggles the specified boolean column for all currently filtered rows.
   * Used by the header checkboxes to enable/disable all items at once.
   *
   * @param field The field name to toggle (e.g., 'gtNetLastpriceRecv').
   * @param checkboxEvent The event from the checkbox (contains checked state).
   */
  toggleColumn(field: string, checkboxEvent: any): void {
    const state = checkboxEvent.checked;
    const table = this.getConfigurableTable()?.table;
    if (!table) {
      return;
    }
    const data = table.filteredValue || table.value;

    if (data) {
      data.forEach(row => {
        if (!this.isCheckboxDisabled(row, field)) {
          if (row[field] !== state) {
            row[field] = state;
            this.onCheckboxChange(row);
          }
        }
      });
    }
  }

  /**
   * Lifecycle hook called when component is destroyed.
   * Prompts user to save unsaved changes, then triggers sync if saves occurred.
   */
  ngOnDestroy(): void {
    if (this.hasUnsavedChanges()) {
      this.confirmationService.confirm({
        header: this.translateService.instant('GT_NET_EXCHANGE_UNSAVED_CHANGES_HEADER'),
        message: this.translateService.instant('GT_NET_EXCHANGE_UNSAVED_CHANGES_MESSAGE'),
        accept: () => {
          this.saveChanges();
          this.triggerSyncIfNeeded();
        },
        reject: () => {
          // User declined, do not save or sync
        }
      });
    } else if (this.savesOccurred) {
      this.triggerSyncIfNeeded();
    }
  }

  /**
   * Triggers the exchange sync background job if saves occurred.
   */
  private triggerSyncIfNeeded(): void {
    if (this.savesOccurred) {
      this.gtNetExchangeService.triggerSync().subscribe({
        error: (err) => {
          console.error('Failed to trigger exchange sync:', err);
        }
      });
    }
  }
}
