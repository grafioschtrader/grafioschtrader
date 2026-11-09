import { Component, signal, output, input, linkedSignal, inject, computed } from '@angular/core';
import { TranslateModule } from '@ngx-translate/core';
import { DialogModule } from '@openng/optimus-ui/dialog';
import { ProgressSpinnerModule } from '@openng/optimus-ui/progressspinner';
import { MessageModule } from '@openng/optimus-ui/message';
import { ButtonModule } from '@openng/optimus-ui/button';
import { Subscription } from 'rxjs';

import { ProcessedActionData } from '../../lib/types/processed.action.data';
import { ProcessedAction } from '../../lib/types/processed.action';
import { SecurityLookupService } from '../service/security-lookup.service';
import {
  ConnectorAlternative,
  CurrencypairLookupResponse,
  CurrencypairLookupSelection,
  LookupProviderStatus
} from '../model/security-lookup';
import { SecurityLookupConnectorTableComponent } from './security-lookup-connector-table.component';
import { isUsableAlternative, preselectAlternative } from '../model/security-lookup-selection';

/**
 * Dialog of the currency pair lookup. As soon as it opens, it tries every connector that serves currency pairs of this
 * kind and shows two tables, one for the history and one for the intraday feed. Each row is a connector with the URL
 * extension it derived and the outcome of its trial request; a history connector also with the oldest closing price
 * it delivers, so the user sees how far back it reaches. Only a connector whose probe succeeded can be chosen, and a
 * table with exactly one such connector is preselected. "Apply" hands the chosen connectors to the currency pair form;
 * nothing is saved here.
 */
@Component({
  selector: 'currencypair-lookup-dialog',
  imports: [
    TranslateModule,
    DialogModule,
    ProgressSpinnerModule,
    MessageModule,
    ButtonModule,
    SecurityLookupConnectorTableComponent
  ],
  template: `
    <p-dialog
      [header]="'CURRENCYPAIR_LOOKUP_DIALOG' | translate"
      [visible]="visibleDialog()"
      [style]="{ width: '1000px', minHeight: '400px' }"
      [contentStyle]="{ 'max-height': 'calc(100vh - 160px)', 'overflow-y': 'auto' }"
      [modal]="true"
      [resizable]="true"
      (onShow)="onShow()"
      (onHide)="onHide()">
      @if (loading()) {
        <div class="flex justify-content-center align-items-center" style="min-height: 200px">
          <p-progressSpinner />
        </div>
      } @else {
        @if (errorMessage(); as errorMessageValue) {
          <p-message severity="error" [text]="errorMessageValue" />
        }
        @if (response(); as response) {
          @for (status of failedProviders(); track status.providerId) {
            <p-message
              severity="warn"
              [text]="
                'LOOKUP_HINT_PROVIDER_FAILED' | translate: { provider: status.providerId, status: status.status }
              " />
          }
          @if (!hasUsableConnector()) {
            <p-message severity="info" [text]="'CURRENCYPAIR_LOOKUP_NO_CONNECTOR' | translate" />
          }
          <security-lookup-connector-table
            titleKey="ID_CONNECTOR_HISTORY"
            feed="history"
            [alternatives]="response.historyConnectors"
            [selectedAlternative]="selection().history"
            [showOldestDate]="true"
            (alternativeSelected)="selectAlternative('history', $event)" />
          <security-lookup-connector-table
            titleKey="ID_CONNECTOR_INTRA"
            feed="intra"
            [alternatives]="response.intraConnectors"
            [selectedAlternative]="selection().intra"
            (alternativeSelected)="selectAlternative('intra', $event)" />
          <div class="flex justify-content-end mt-3">
            <p-button
              [label]="'APPLY' | translate"
              (onClick)="apply()"
              [disabled]="!selection().history && !selection().intra">
              <i class="pi pi-check" pButtonIcon></i>
            </p-button>
          </div>
        }
      }
    </p-dialog>
  `
})
export class CurrencypairLookupDialogComponent {
  private securityLookupService = inject(SecurityLookupService);

  readonly visibleDialogInput = input(false, { alias: 'visibleDialog' });
  readonly visibleDialog = linkedSignal(() => this.visibleDialogInput());
  /** Base currency chosen in the currency pair form */
  readonly fromCurrency = input<string>();
  /** Quote currency chosen in the currency pair form */
  readonly toCurrency = input<string>();

  readonly closeDialog = output<ProcessedActionData>();

  readonly loading = signal(false);
  readonly response = signal<CurrencypairLookupResponse | null>(null);
  readonly errorMessage = signal<string | null>(null);
  /** Chosen connector per feed; every change publishes a new record */
  readonly selection = signal<CurrencypairLookupSelection>({ history: null, intra: null });

  /**
   * Search providers that could not be asked. A provider that does not know the pair is no failure: the connector that
   * needs its identifier is simply not offered.
   */
  readonly failedProviders = computed<LookupProviderStatus[]>(() =>
    (this.response()?.providerStatus ?? []).filter((s) => s.status !== 'OK' && s.status !== 'NO_MATCH')
  );

  /** False when no connector of either feed delivered data for the pair. */
  readonly hasUsableConnector = computed<boolean>(() => {
    const response = this.response();
    return (
      !!response &&
      (response.historyConnectors.some((a) => isUsableAlternative('history', a)) ||
        response.intraConnectors.some((a) => isUsableAlternative('intra', a)))
    );
  });

  /** Prevents a second emission when "Apply" closes the dialog */
  private selectionHandled = false;
  private lookupSubscription?: Subscription | null;

  /** Starts the lookup when the dialog has opened. */
  onShow(): void {
    this.selectionHandled = false;
    this.errorMessage.set(null);
    this.response.set(null);
    this.selection.set({ history: null, intra: null });
    this.performLookup();
  }

  /** Cancels a running lookup and reports a close without selection. */
  onHide(): void {
    this.cancelPendingRequest();
    if (!this.selectionHandled) {
      this.closeDialog.emit(new ProcessedActionData(ProcessedAction.NO_CHANGE));
    }
  }

  /**
   * Takes over the connector the user chose for one feed.
   *
   * @param feed - The feed of the connector table
   * @param alternative - The chosen connector, or null
   */
  selectAlternative(feed: keyof CurrencypairLookupSelection, alternative: ConnectorAlternative | null): void {
    this.selection.update((selection) => ({ ...selection, [feed]: alternative }));
  }

  /** Hands the chosen connectors to the currency pair form and closes the dialog. */
  apply(): void {
    this.selectionHandled = true;
    this.closeDialog.emit(new ProcessedActionData(ProcessedAction.CREATED, this.selection()));
    this.visibleDialog.set(false);
  }

  private performLookup(): void {
    const fromCurrency = this.fromCurrency();
    const toCurrency = this.toCurrency();
    if (!fromCurrency || !toCurrency) return;
    this.cancelPendingRequest();
    this.loading.set(true);
    this.lookupSubscription = this.securityLookupService.lookupCurrencypair(fromCurrency, toCurrency).subscribe({
      next: (response) => {
        this.selection.set({
          history: preselectAlternative('history', response.historyConnectors),
          intra: preselectAlternative('intra', response.intraConnectors)
        });
        this.response.set(response);
        this.loading.set(false);
      },
      error: (error) => {
        this.loading.set(false);
        this.errorMessage.set(error.message);
      }
    });
  }

  private cancelPendingRequest(): void {
    if (this.lookupSubscription) {
      this.lookupSubscription.unsubscribe();
      this.lookupSubscription = null;
    }
    this.loading.set(false);
  }
}
