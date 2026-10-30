import { computed, Directive, OnDestroy, OnInit, signal, viewChild, inject } from '@angular/core';
import { MenuItem, SortEvent } from '@openng/optimus-ui/api';

import { TableCrudSupportMenu } from '../../lib/datashowbase/table.crud.support.menu';
import { GTNetExchangeFields } from '../../lib/gnet/model/gtnet';
import { GTNetExchangeService } from '../service/gtnet-exchange.service';
import { DataType } from '../../lib/dynamic-form/models/data.type';
import { HelpIds } from '../../lib/help/help.ids';
import { InfoLevelType } from '../../lib/message/info.leve.type';
import { Securitycurrency } from '../../entities/securitycurrency';
import { ConfigurableTableComponent } from '../../lib/datashowbase/configurable-table.component';
import { AuditHelper } from '../../lib/helper/audit.helper';
import { BaseSettings } from '../../lib/base.settings';

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
  extends TableCrudSupportMenu<T>
  implements OnInit, OnDestroy
{
  protected gtNetExchangeService: GTNetExchangeService = inject(GTNetExchangeService);

  /** IDs of the modified items for the batch save; every change publishes a new set */
  readonly modifiedItems = signal<ReadonlySet<number>>(new Set());

  /** Tracks if any saves occurred during component lifetime for triggering sync on destroy */
  private savesOccurred: boolean = false;

  /**
   * Whether the header checkboxes are offered at all. They walk the filtered rows and skip every disabled cell, so
   * they act only on the instruments the user may change; the strip is therefore useless only for a user who may
   * change nothing in the current list.
   */
  private readonly multiSelectAllowed = computed(
    () => AuditHelper.hasHigherPrivileges(this.gps) || this.entityList().some((item) => this.canEditRow(item))
  );

  /** Stable sort callback of the table */
  readonly exchangeSorter = (event: SortEvent) => this.doCustomSort(event);

  /** Entity for dialog calls (required by base class) */
  callParam: T;

  readonly contextMenuDiv = viewChild<any>('cmDiv');

  protected constructor(entityName: string) {
    super(entityName, null, []);
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
    this.addColumnFeqH(DataType.Boolean, 'gtNetLastpriceRecv', true, false, { width: 80, templateName: 'checkbox' });
    this.addColumnFeqH(DataType.Boolean, 'gtNetHistoricalRecv', true, false, { width: 80, templateName: 'checkbox' });
    this.addColumnFeqH(DataType.Boolean, 'gtNetLastpriceSend', true, false, { width: 80, templateName: 'checkbox' });
    this.addColumnFeqH(DataType.Boolean, 'gtNetHistoricalSend', true, false, { width: 80, templateName: 'checkbox' });
  }

  /**
   * Handle checkbox value change - marks item as modified.
   * Tracks by idSecuritycurrency directly on the entity.
   */
  onCheckboxChange(item: T): void {
    const id = item.idSecuritycurrency;
    if (id != null) this.markModified([id]);
  }

  /** Adds instruments to the modified set with a single new set. */
  private markModified(ids: number[]): void {
    if (ids.length > 0) {
      this.modifiedItems.update((modified) => new Set([...modified, ...ids]));
    }
  }

  /**
   * Whether the current user may change the exchange flags of this instrument. The flags are shared data, not tenant
   * data, so the ordinary editing rights of the instrument decide: an administrator and a user with the extended
   * editing right may change every instrument, everybody else only the instruments they created themselves. The
   * backend refuses anything else with a security breach, which is why a row the user may not change must not be
   * offered at all.
   *
   * @param item The instrument row to check
   * @returns True if the current user may change this row
   */
  protected canEditRow(item: T): boolean {
    return AuditHelper.hasRightsForEditingOrDeleteAuditable(this.gps, item);
  }

  /**
   * Check if a checkbox should be disabled. A row the user may not change is disabled as a whole; derived classes add
   * their instrument-specific rules on top of this one.
   */
  isCheckboxDisabled(item: T, field: string): boolean {
    return !this.canEditRow(item);
  }

  /**
   * Get the list of modified entities for batch save.
   */
  protected getModifiedList(): T[] {
    const modified = this.modifiedItems();
    return this.entityList().filter((item) => item.idSecuritycurrency != null && modified.has(item.idSecuritycurrency));
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
    // Replace the saved rows with the returned items
    const updatedById = new Map(updatedItems.map((updated) => [updated.idSecuritycurrency, updated]));
    this.entityList.update((list) => list.map((item) => updatedById.get(item.idSecuritycurrency) ?? item));
    this.modifiedItems.set(new Set());
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
    return this.modifiedItems().size > 0;
  }

  /**
   * Custom sort handler for multi-column sorting.
   */
  doCustomSort(event: SortEvent): void {
    const sortMetaList = event.multiSortMeta;
    if (!sortMetaList) return;
    event.data?.sort((data1, data2) => {
      for (const sortMeta of sortMetaList) {
        const field = this.fields.find((f) => f.field === sortMeta.field);
        if (!field) continue;
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
            result = value1 < value2 ? -1 : value1 > value2 ? 1 : 0;
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
    return field.dataType === DataType.Numeric || field.dataType === DataType.DateTimeNumeric ? 'text-end' : '';
  }

  override prepareCallParam(entity: T | null): void {
    if (!entity) throw new Error('An exchange dialog requires a selected instrument.');
    this.callParam = entity;
  }

  protected override readData(): void {
    this.loadData();
  }

  public override getHelpContextId(): string {
    return HelpIds.HELP_GT_NET;
  }

  /**
   * Builds the untranslated edit menu of the selected instrument; the base class translates and publishes it.
   * Derived classes add their entries.
   */
  protected override prepareEditMenu(entity: T | null): MenuItem[] | null {
    return null;
  }

  /** Whether the header checkboxes are offered at all, see {@link multiSelectAllowed}. */
  isUserAllowedToMultiSelect(): boolean {
    return this.multiSelectAllowed();
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
    const table = this.getConfigurableTable()?.table();
    if (!table) {
      return;
    }
    const data = table.filteredValue || table.value;

    if (data) {
      const changed: number[] = [];
      data.forEach((row) => {
        if (!this.isCheckboxDisabled(row, field)) {
          if (row[field] !== state) {
            row[field] = state;
            changed.push(row.idSecuritycurrency);
          }
        }
      });
      this.markModified(changed);
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
   * Triggers the exchange sync background job if saves occurred. Enqueuing that job concerns the whole instance, so
   * the endpoint requires ROLE_ADMIN. A user who may only change the instruments they created themselves would
   * otherwise fire a request that can only come back 403; their change reaches the peers with the daily
   * synchronisation instead.
   */
  private triggerSyncIfNeeded(): void {
    if (this.savesOccurred && this.gps.hasRole(BaseSettings.ROLE_ADMIN)) {
      this.gtNetExchangeService.triggerSync().subscribe({
        error: (err) => {
          console.error('Failed to trigger exchange sync:', err);
        }
      });
    }
  }
}
