import { Component, output, input, effect, untracked } from '@angular/core';
import { MenuItem } from '@openng/optimus-ui/api';
import { TableConfigBase } from '../../lib/datashowbase/table.config.base';
import { ConfigurableTableComponent } from '../../lib/datashowbase/configurable-table.component';
import { DataType } from '../../lib/dynamic-form/models/data.type';
import { FilterType } from '../../lib/datashowbase/filter.type';
import { TranslateValue } from '../../lib/datashowbase/column.config';
import { AlertNotification } from '../service/algo-alert.service';
import { Helper } from '../../lib/helper/helper';

/**
 * The recorded notification deliveries of the alert diagnostics. The parent loads every notification of the tenant;
 * this table pages, sorts and filters them like any other table. Rows are selected with checkboxes. Through the context
 * menu a single notification whose delivery failed or awaits review can be queued again, and selected notifications
 * can be deleted when the backend marks every one of them as deletable. The parent performs both actions and reloads
 * the list.
 */
@Component({
  selector: 'algo-alert-notification-table',
  imports: [ConfigurableTableComponent],
  template: `<configurable-table
    [data]="rows()"
    [fields]="fields"
    [fieldsRevision]="fieldsRevision()"
    dataKey="id"
    [selectionMode]="'multiple'"
    [selection]="selectedNotifications"
    (selectionChange)="onSelectionChange($event)"
    [valueGetterFn]="valueGetter"
    [baseLocale]="baseLocale"
    [customSortFn]="customSorter"
    [multiSortMeta]="multiSortMeta"
    [paginator]="true"
    [rows]="rowsPerPage"
    (pageChange)="onPage($event)"
    [hasFilter]="hasFilter"
    [showContextMenu]="!readOnly"
    [contextMenuItems]="contextMenuItems"
    [contextMenuAppendTo]="'body'" />`
})
export class AlgoAlertNotificationTableComponent extends TableConfigBase {
  /** Delivery states from which the backend accepts a retry. */
  private static readonly RETRYABLE_STATES = ['FAILED', 'REVIEW_REQUIRED'];

  /** All recorded notifications of the tenant. */
  readonly rows = input<AlertNotification[]>([]);

  /** Emits the id of the notification the user wants to be delivered again. */
  readonly retry = output<number>();

  /** Emits the ids of the notifications the user wants to delete; all of them are deletable. */
  readonly delete = output<number[]>();

  selectedNotifications: AlertNotification[] = [];
  contextMenuItems: MenuItem[] = [];

  /** A read-only user may inspect deliveries but not trigger one, so the context menu is not offered at all. */
  readonly readOnly: boolean;

  constructor() {
    super();
    this.readOnly = this.gps.isReadOnlyUser();
    this.rowsPerPage = 20;
    this.addColumnFeqH(DataType.String, 'contextName', true, false, { filterType: FilterType.withOptions });
    this.addColumnFeqH(DataType.String, 'securityName');
    this.addColumnFeqH(DataType.DateTimeString, 'alertTime');
    this.addColumnFeqH(DataType.String, 'deliveryStatus', true, false, {
      translateValues: TranslateValue.NORMAL,
      filterType: FilterType.withOptions
    });
    this.addColumnFeqH(DataType.String, 'deliveryChannels', true, false, {
      translateValues: TranslateValue.NORMAL,
      filterType: FilterType.withOptions
    });
    this.addColumnFeqH(DataType.NumericInteger, 'deliveryAttempts');
    for (const field of ['nextAttemptAt', 'internalCompletedAt', 'externalCompletedAt'])
      this.addColumnFeqH(DataType.DateTimeString, field);
    this.addColumnFeqH(DataType.String, 'deliveryError');
    this.addColumnFeqH(DataType.String, 'alarmDetails');
    this.multiSortMeta.push({ field: 'alertTime', order: -1 });
    this.prepareTableAndTranslate();
    this.resetMenu();

    effect(() => {
      this.rows();
      untracked(() => {
        const rows = this.rows();
        if (rows) {
          this.selectedNotifications = [];
          this.createTranslatedValueStoreAndFilterField(rows);
          this.prepareFilter(rows);
          this.resetMenu();
        }
      });
    });
  }

  onSelectionChange(selection: AlertNotification | AlertNotification[] | null): void {
    this.selectedNotifications = Helper.multipleSelection(selection);
    this.resetMenu();
  }

  /**
   * Rebuilds the context menu. Retry is enabled for exactly one selected notification in a retryable state, delete for
   * a selection in which every notification is deletable.
   */
  resetMenu(): void {
    const selected = this.selectedNotifications;
    const single = selected.length === 1 ? selected[0] : null;
    this.contextMenuItems = [
      {
        label: this.translateService.instant('ALERT_RETRY_SELECTED'),
        disabled: !single || !AlgoAlertNotificationTableComponent.RETRYABLE_STATES.includes(single.deliveryStatus),
        command: () => {
          if (single) this.retry.emit(single.id);
        }
      },
      {
        label: this.translateService.instant('DELETE_SELECTED'),
        disabled: selected.length === 0 || selected.some((notification) => !notification.deletable),
        command: () => this.delete.emit(selected.map((notification) => notification.id))
      }
    ];
  }
}
