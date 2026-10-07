import { Component, Input, InjectionToken, Type } from '@angular/core';
import { RouterLink } from '@angular/router';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { ShowRecordConfigBase } from '../datashowbase/show.record.config.base';
import { ColumnConfig } from '../datashowbase/column.config';
import { GlobalparameterService } from '../services/globalparameter.service';
import { DataType } from '../dynamic-form/models/data.type';
import { DashboardResult } from './dashboard.types';
import { DashboardTableComponent } from './dashboard-table.component';

@Component({
  selector: 'dashboard-summary',
  standalone: true,
  imports: [RouterLink, TranslateModule, DashboardTableComponent],
  template: `@for (list of result.payload?.lists; track list.titleKey) {
    <section class="mb-3">
      <h3 class="h6">{{ list.titleKey | translate }}: {{ getValueByPath(list, countField) }}</h3>
      @if (list.userCount !== null) {
        <p>{{ 'DASHBOARD_AFFECTED_USERS' | translate }}: {{ getValueByPath(list, userCountField) }}</p>
      }
      @if (list.rows.length) {
        <dashboard-table [rows]="list.rows" [type]="result.type" />
      } @else {
        <p>{{ 'DASHBOARD_NO_ITEMS' | translate }}</p>
      }
      <a [routerLink]="list.destination">{{ 'DASHBOARD_OPEN' | translate }}</a>
    </section>
  }`
})
export class DashboardSummaryComponent extends ShowRecordConfigBase {
  @Input() result: DashboardResult;
  countField = ShowRecordConfigBase.createColumnConfig(DataType.NumericInteger, 'count', '');
  userCountField = ShowRecordConfigBase.createColumnConfig(DataType.NumericInteger, 'userCount', '');
  constructor(translate: TranslateService, gps: GlobalparameterService) {
    super(translate, gps);
  }
}

/** Applications may provide a renderer map extending these built-in library renderers by stable backend type code. */
export const DASHBOARD_RENDERERS = new InjectionToken<Record<string, Type<unknown>>>('DASHBOARD_RENDERERS', {
  providedIn: 'root',
  factory: () => ({
    UNREAD_MAIL: DashboardSummaryComponent,
    PROPOSE_CHANGE_OPEN: DashboardSummaryComponent,
    USER_LIMIT_REQUESTS: DashboardSummaryComponent
  })
});

/** Applications can replace the default maxRows summary with the setting appropriate to each widget type. */
export const DASHBOARD_CONFIG_SUMMARIES = new InjectionToken<Record<string, ColumnConfig>>(
  'DASHBOARD_CONFIG_SUMMARIES',
  {
    providedIn: 'root',
    factory: () => ({})
  }
);

/**
 * Widget types whose card offers a maximize button, letting it take over the whole dashboard area for a while. Only a
 * card that gains from the space - a chart, for instance - is worth listing; a renderer of a listed type receives a
 * `maximized` input next to `result`. None of the library widgets is maximizable.
 */
export const DASHBOARD_MAXIMIZABLE = new InjectionToken<ReadonlySet<string>>('DASHBOARD_MAXIMIZABLE', {
  providedIn: 'root',
  factory: () => new Set<string>()
});
