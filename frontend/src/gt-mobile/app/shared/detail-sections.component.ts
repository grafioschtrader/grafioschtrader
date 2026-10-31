import { Component, inject, input } from '@angular/core';
import { IonItem, IonLabel, IonList, IonListHeader } from '@ionic/angular';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { MobileFormatService } from '../service/mobile.format.service';
import { MobileDetailEntry, MobileDetailSection } from './mobile.detail';

/**
 * Sections of labelled values, each line with the label on the left and the value on the right. This is how the phone
 * shows a record the desktop shows as a wide table row or a record view. A line whose value formats to nothing is left
 * out, and so is a section without lines.
 */
@Component({
  selector: 'mobile-detail-sections',
  imports: [IonList, IonListHeader, IonItem, IonLabel, TranslatePipe],
  template: `
    @for (section of sections(); track $index) {
      @if (section.entries.length) {
        <ion-list lines="full">
          <ion-list-header>
            <ion-label>{{ section.title ?? (section.titleKey ?? '' | translate) }}</ion-label>
          </ion-list-header>
          @for (entry of section.entries; track entry.labelKey) {
            @let text = entryText(entry);
            @if (text) {
              <ion-item>
                <ion-label class="detail-line">
                  <span class="detail-label">{{ entry.labelKey | translate }}</span>
                  <span
                    class="detail-value"
                    [class.figure]="entry.format !== 'text' && entry.format !== 'translate'"
                    [class.gain]="entry.signed && +entry.value > 0"
                    [class.loss]="entry.signed && +entry.value < 0"
                    >{{ text }}</span
                  >
                </ion-label>
              </ion-item>
            }
          }
        </ion-list>
      }
    }
  `,
  styles: [
    `
      .detail-line {
        display: flex;
        justify-content: space-between;
        align-items: baseline;
        gap: 12px;
      }
      .detail-label {
        color: var(--ion-color-medium);
      }
      .detail-value {
        text-align: right;
        white-space: normal;
      }
    `
  ]
})
export class DetailSectionsComponent {
  readonly sections = input.required<MobileDetailSection[]>();

  private readonly format = inject(MobileFormatService);
  private readonly translateService = inject(TranslateService);

  /** Text of a line by its format; empty when the value formats to nothing. */
  protected entryText(entry: MobileDetailEntry): string {
    const value = entry.value;
    switch (entry.format) {
      case 'text':
        return String(value);
      case 'translate':
        return this.translateService.instant(String(value));
      case 'date':
        return this.format.date(String(value));
      case 'dateTime':
        return this.format.dateTime(value);
      case 'price':
        return this.format.price(Number(value), entry.fractionDigits);
      case 'percentage':
        return this.format.percentage(Number(value));
      case 'units':
        return this.format.units(Number(value));
      case 'integer':
        return this.format.integer(Number(value));
      case 'amount':
        return this.format.amount(Number(value), entry.currency ?? '');
    }
  }
}
