import { Component, inject, input } from '@angular/core';
import { IonItem, IonLabel, IonList, IonListHeader, IonNote } from '@ionic/angular';
import { TranslatePipe } from '@ngx-translate/core';
import { DashboardResult, DashboardRow } from '../../../app/lib/dashboard/dashboard.types';
import { DataType } from '../../../app/lib/dynamic-form/models/data.type';
import { MobileFormatService } from '../service/mobile.format.service';

/**
 * Counted lists of the library widgets: unread mail, open change proposals and limit requests. Read only; working on
 * an entry stays with the desktop application.
 */
@Component({
  selector: 'mobile-summary-widget',
  imports: [IonList, IonListHeader, IonItem, IonLabel, IonNote, TranslatePipe],
  template: `
    @for (list of result().payload?.lists ?? []; track list.titleKey) {
      <ion-list lines="inset">
        <ion-list-header>
          <ion-label>{{ list.titleKey | translate }}: {{ format.format(list, 'count', integer) }}</ion-label>
        </ion-list-header>
        @if (list.userCount !== null) {
          <ion-item>
            <ion-label>{{ 'DASHBOARD_AFFECTED_USERS' | translate }}</ion-label>
            <ion-note slot="end">{{ format.format(list, 'userCount', integer) }}</ion-note>
          </ion-item>
        }
        @for (row of list.rows; track row.id) {
          <ion-item>
            <ion-label class="ion-text-wrap">
              <h3>{{ rowTitle(row) }}</h3>
              @if (row.noteRequest) {
                <p>{{ row.noteRequest }}</p>
              }
              <p>{{ format.format(row, 'creationTime', dateTime) }}</p>
            </ion-label>
          </ion-item>
        } @empty {
          <ion-item>
            <ion-label>{{ 'DASHBOARD_NO_ITEMS' | translate }}</ion-label>
          </ion-item>
        }
      </ion-list>
    }
  `
})
export class SummaryWidgetComponent {
  readonly result = input.required<DashboardResult>();
  protected readonly format = inject(MobileFormatService);
  protected readonly integer = DataType.NumericInteger;
  protected readonly dateTime = DataType.DateTimeString;

  /** The subject of a mail, otherwise the user and the entity the row is about. */
  protected rowTitle(row: DashboardRow): string {
    return row.subject ?? [row.nickname, row.entity].filter((part) => !!part).join(' · ');
  }
}
