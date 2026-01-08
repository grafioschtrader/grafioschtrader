import {Component, EventEmitter, Input, Output} from '@angular/core';
import {CommonModule} from '@angular/common';
import {TranslateModule} from '@ngx-translate/core';
import {CheckboxModule} from 'primeng/checkbox';
import {TooltipModule} from 'primeng/tooltip';

/**
 * Event payload for checkbox toggle actions.
 * Contains the field name and the checkbox event with the checked state.
 */
export interface CheckboxToggleEvent {
  field: string;
  event: { checked: boolean };
}

/**
 * Reusable component for displaying the four GTNet exchange checkboxes.
 * Used in both securities and currency pairs tables to enable/disable
 * all items' GTNet flags at once.
 *
 * The four checkboxes control:
 * - gtNetLastpriceRecv: Receive last price data from GTNet
 * - gtNetHistoricalRecv: Receive historical data from GTNet
 * - gtNetLastpriceSend: Send last price data to GTNet
 * - gtNetHistoricalSend: Send historical data to GTNet
 */
@Component({
  selector: 'gtnet-exchange-checkboxes',
  standalone: true,
  imports: [
    CommonModule,
    TranslateModule,
    CheckboxModule,
    TooltipModule
  ],
  template: `
    <div class="gtnet-checkboxes-container">
      <div class="gtnet-checkbox-item">
        <span class="me-2 cursor-help" [pTooltip]="'GT_NET_LASTPRICE_RECV_TOOLTIP' | translate"
              tooltipPosition="top">{{ 'GT_NET_LASTPRICE_RECV' | translate }}</span>
        <p-checkbox [binary]="true" (onChange)="onToggle('gtNetLastpriceRecv', $event)"
                    [disabled]="disabled"></p-checkbox>
      </div>

      <div class="gtnet-checkbox-item">
        <span class="me-2 cursor-help" [pTooltip]="'GT_NET_HISTORICAL_RECV_TOOLTIP' | translate"
              tooltipPosition="top">{{ 'GT_NET_HISTORICAL_RECV' | translate }}</span>
        <p-checkbox [binary]="true" (onChange)="onToggle('gtNetHistoricalRecv', $event)"
                    [disabled]="disabled"></p-checkbox>
      </div>

      <div class="gtnet-checkbox-item">
        <span class="me-2 cursor-help" [pTooltip]="'GT_NET_LASTPRICE_SEND_TOOLTIP' | translate"
              tooltipPosition="top">{{ 'GT_NET_LASTPRICE_SEND' | translate }}</span>
        <p-checkbox [binary]="true" (onChange)="onToggle('gtNetLastpriceSend', $event)"
                    [disabled]="disabled"></p-checkbox>
      </div>

      <div class="gtnet-checkbox-item">
        <span class="me-2 cursor-help" [pTooltip]="'GT_NET_HISTORICAL_SEND_TOOLTIP' | translate"
              tooltipPosition="top">{{ 'GT_NET_HISTORICAL_SEND' | translate }}</span>
        <p-checkbox [binary]="true" (onChange)="onToggle('gtNetHistoricalSend', $event)"
                    [disabled]="disabled"></p-checkbox>
      </div>
    </div>
  `,
  styles: [`
    .gtnet-checkboxes-container {
      display: flex;
      align-items: center;
      gap: 0.5rem;
      padding: 0.5rem;
      border-right: 1px solid var(--surface-border);
      padding-right: 0.75rem;
      margin-right: 0.75rem;
    }

    .gtnet-checkbox-item {
      display: flex;
      align-items: center;
    }

    .cursor-help {
      cursor: help;
    }
  `]
})
export class GTNetExchangeCheckboxesComponent {

  /** Whether all checkboxes should be disabled */
  @Input() disabled = false;

  /** Emits when any checkbox is toggled, with field name and event */
  @Output() toggle = new EventEmitter<CheckboxToggleEvent>();

  /**
   * Handles checkbox toggle and emits the event with field information.
   *
   * @param field The field name being toggled
   * @param event The checkbox change event
   */
  onToggle(field: string, event: { checked: boolean }): void {
    this.toggle.emit({field, event});
  }
}
