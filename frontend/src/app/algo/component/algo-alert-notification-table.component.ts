import {
  ChangeDetectionStrategy,
  Component,
  EventEmitter,
  Input,
  OnChanges,
  Output,
  SimpleChanges
} from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import { FilterService, MenuItem } from '@openng/optimus-ui/api';
import { TableConfigBase } from '../../lib/datashowbase/table.config.base';
import { ConfigurableTableComponent } from '../../lib/datashowbase/configurable-table.component';
import { GlobalparameterService } from '../../lib/services/globalparameter.service';
import { UserSettingsService } from '../../lib/services/user.settings.service';
import { DataType } from '../../lib/dynamic-form/models/data.type';
import { FilterType } from '../../lib/datashowbase/filter.type';
import { TranslateValue } from '../../lib/datashowbase/column.config';
import { AlertNotification } from '../service/algo-alert.service';

/**
 * The recorded notification deliveries of the alert diagnostics. The parent loads every notification of the tenant;
 * this table pages, sorts and filters them like any other table. Rows are selected with checkboxes. Through the context
 * menu a single notification whose delivery failed or awaits review can be queued again, and selected notifications
 * can be deleted when the backend marks every one of them as deletable. The parent performs both actions and reloads
 * the list.
 */
@Component({
  selector: 'algo-alert-notification-table',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.Eager,
  imports: [ConfigurableTableComponent],
  template: `<configurable-table
    [data]="rows"
    [fields]="fields"
    dataKey="id"
    [selectionMode]="'multiple'"
    [selection]="selectedNotifications"
    (selectionChange)="onSelectionChange($event)"
    [valueGetterFn]="getValueByPath.bind(this)"
    [baseLocale]="baseLocale"
    [customSortFn]="customSort.bind(this)"
    [multiSortMeta]="multiSortMeta"
    [paginator]="true"
    [rows]="rowsPerPage"
    (pageChange)="onPage($event)"
    [hasFilter]="hasFilter"
    [showContextMenu]="!readOnly"
    [contextMenuItems]="contextMenuItems"
    [contextMenuAppendTo]="'body'" />`
})
export class AlgoAlertNotificationTableComponent extends TableConfigBase implements OnChanges {
  /** Delivery states from which the backend accepts a retry. */
  private static readonly RETRYABLE_STATES = ['FAILED', 'REVIEW_REQUIRED'];

  /** All recorded notifications of the tenant. */
  @Input() rows: AlertNotification[] = [];

  /** Emits the id of the notification the user wants to be delivered again. */
  @Output() retry = new EventEmitter<number>();

  /** Emits the ids of the notifications the user wants to delete; all of them are deletable. */
  @Output() delete = new EventEmitter<number[]>();

  selectedNotifications: AlertNotification[] = [];
  contextMenuItems: MenuItem[] = [];

  /** A read-only user may inspect deliveries but not trigger one, so the context menu is not offered at all. */
  readonly readOnly: boolean;

  constructor(
    filter: FilterService,
    settings: UserSettingsService,
    translate: TranslateService,
    gps: GlobalparameterService
  ) {
    super(filter, settings, translate, gps);
    this.readOnly = gps.isReadOnlyUser();
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
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['rows'] && this.rows) {
      // A reloaded list holds new row objects, so a previous selection no longer refers to a displayed row.
      this.selectedNotifications = [];
      this.createTranslatedValueStoreAndFilterField(this.rows);
      this.prepareFilter(this.rows);
      this.resetMenu();
    }
  }

  onSelectionChange(notifications: AlertNotification[]): void {
    this.selectedNotifications = notifications ?? [];
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
        command: () => this.retry.emit(single.id)
      },
      {
        label: this.translateService.instant('DELETE_SELECTED'),
        disabled: selected.length === 0 || selected.some((notification) => !notification.deletable),
        command: () => this.delete.emit(selected.map((notification) => notification.id))
      }
    ];
  }
}
