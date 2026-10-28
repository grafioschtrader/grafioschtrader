import { Component, signal, output, input, linkedSignal, inject } from '@angular/core';
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
  SecurityLookupCandidate,
  SecurityLookupResponse,
  SecurityLookupSelection
} from '../model/security-lookup';
import { SecurityLookupBaseDataTableComponent } from './security-lookup-base-data-table.component';
import { SecurityLookupConnectorTableComponent } from './security-lookup-connector-table.component';
import { SecurityLookupHintTableComponent } from './security-lookup-hint-table.component';
import { LookupFeed, preselectAlternative, preselectNameCandidate } from '../model/security-lookup-selection';

/**
 * Dialog of the security lookup. It searches the data providers by ISIN, currency and exchange as soon as it opens and
 * shows the hints of the query - failed providers, exchanges on which the instrument was found instead - followed by
 * the listings with their master data, the hints of the chosen listing, and the connectors for history, intraday,
 * dividends and splits of the chosen listing. Only a connector whose probe succeeded can be chosen, and a table with exactly one such
 * connector is preselected. "Apply" closes the dialog and hands the
 * chosen listing and connectors to the security form; nothing is saved here.
 */
@Component({
  selector: 'security-lookup-dialog',
  imports: [
    TranslateModule,
    DialogModule,
    ProgressSpinnerModule,
    MessageModule,
    ButtonModule,
    SecurityLookupBaseDataTableComponent,
    SecurityLookupConnectorTableComponent,
    SecurityLookupHintTableComponent
  ],
  template: `
    <p-dialog
      [header]="'SECURITY_LOOKUP_DIALOG' | translate"
      [visible]="visibleDialog()"
      [style]="{ width: '1150px', minHeight: '400px' }"
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
        @if (errorMessage()) {
          <p-message severity="error" [text]="errorMessage()" />
        }
        @if (response(); as response) {
          <security-lookup-hint-table titleKey="LOOKUP_HINTS" [hints]="response.hints" />
          @if (response.candidates.length === 0) {
            <p-message severity="info" [text]="'SECURITY_LOOKUP_NO_RESULTS' | translate" />
          } @else {
            <security-lookup-base-data-table
              [candidates]="response.candidates"
              [selectedCandidate]="selectedCandidate()"
              [(nameCandidate)]="nameCandidate"
              (candidateSelected)="onCandidateSelected($event)" />
            @if (selectedCandidate(); as selectedCandidate) {
              <security-lookup-hint-table titleKey="LOOKUP_HINTS_LISTING" [hints]="selectedCandidate.hints" />
              @for (feed of visibleFeeds(); track feed.key) {
                <security-lookup-connector-table
                  [titleKey]="feed.titleKey"
                  [feed]="feed.key"
                  [alternatives]="alternativesOf(feed.field)"
                  [selectedAlternative]="selection()[feed.key]"
                  [showDistributionFrequency]="feed.key === 'dividend'"
                  [disabled]="isFeedDisabled(feed.key)"
                  disabledMessageKey="DIVIDEND_CONNECTOR_NOT_NEEDED"
                  (alternativeSelected)="selectAlternative(feed.key, $event)" />
              }
            }
            <div class="flex justify-content-end mt-3">
              <p-button [label]="'APPLY' | translate" (onClick)="apply()" [disabled]="!selectedCandidate()">
                <i class="pi pi-check" pButtonIcon></i>
              </p-button>
            </div>
          }
        }
      }
    </p-dialog>
  `
})
export class SecurityLookupDialogComponent {
  private securityLookupService = inject(SecurityLookupService);

  readonly visibleDialogInput = input(false, { alias: 'visibleDialog' });
  readonly visibleDialog = linkedSignal(() => this.visibleDialogInput());
  readonly isin = input<string>(undefined);
  readonly currency = input<string>(undefined);
  /** MIC of the stock exchange chosen in the security form */
  readonly mic = input<string>(undefined);
  /** Asset class chosen in the form; selects the providers and connectors */
  readonly idAssetClass = input<number>(undefined);
  /** Ticker entered in the form; helps providers that know the ISIN only for another listing */
  readonly tickerSymbol = input<string>(undefined);

  readonly closeDialog = output<ProcessedActionData>();

  readonly feeds: { key: LookupFeed; field: keyof SecurityLookupCandidate; titleKey: string }[] = [
    { key: 'history', field: 'historyConnectors', titleKey: 'ID_CONNECTOR_HISTORY' },
    { key: 'intra', field: 'intraConnectors', titleKey: 'ID_CONNECTOR_INTRA' },
    { key: 'dividend', field: 'dividendConnectors', titleKey: 'ID_CONNECTOR_DIVIDEND' },
    { key: 'split', field: 'splitConnectors', titleKey: 'ID_CONNECTOR_SPLIT' }
  ];

  readonly loading = signal(false);
  readonly response = signal<SecurityLookupResponse>(null);
  readonly errorMessage = signal<string>(null);
  readonly selectedCandidate = signal<SecurityLookupCandidate>(null);
  /** The listing whose name is taken over; by default the first one not written entirely in capitals */
  readonly nameCandidate = signal<SecurityLookupCandidate>(null);
  /** Chosen connector alternative per feed; every change publishes a new record */
  readonly selection = signal<Record<LookupFeed, ConnectorAlternative>>(this.emptySelection());

  /** Prevents a second emission when "Apply" closes the dialog */
  private selectionHandled = false;
  private lookupSubscription: Subscription;

  /**
   * Starts the lookup when the dialog has opened. Optimus UI emits onShow at the end of the enter motion; the state
   * the template shows is held in signals, so the answer refreshes the dialog wherever the request was started.
   */
  onShow(): void {
    this.selectionHandled = false;
    this.errorMessage.set(null);
    this.response.set(null);
    this.nameCandidate.set(null);
    this.onCandidateSelected(null);
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
   * Shows the connectors of the chosen listing and preselects every connector table with exactly one usable connector,
   * however many others failed their probe.
   *
   * @param candidate - The chosen listing, or null
   */
  onCandidateSelected(candidate: SecurityLookupCandidate): void {
    this.selectedCandidate.set(candidate);
    const selection = this.emptySelection();
    if (candidate) {
      this.feeds.forEach((feed) => {
        selection[feed.key] = this.isFeedDisabled(feed.key)
          ? null
          : preselectAlternative(feed.key, this.alternativesOf(feed.field));
      });
    }
    this.selection.set(selection);
  }

  /**
   * Takes over the alternative the user chose for one feed.
   *
   * @param feed - The feed of the connector table
   * @param alternative - The chosen alternative, or null
   */
  selectAlternative(feed: LookupFeed, alternative: ConnectorAlternative): void {
    this.selection.update((selection) => ({ ...selection, [feed]: alternative }));
  }

  /**
   * The connector tables to show: dividend and split only when they apply to the chosen asset class, by the same rule
   * as the security form.
   *
   * @returns The feeds with their table
   */
  visibleFeeds(): { key: LookupFeed; field: keyof SecurityLookupCandidate; titleKey: string }[] {
    return this.feeds.filter(
      (feed) =>
        (feed.key !== 'dividend' || this.response()?.dividendConnectorApplicable !== false) &&
        (feed.key !== 'split' || this.response()?.splitConnectorApplicable !== false)
    );
  }

  /**
   * Connectors of the chosen listing for one feed.
   *
   * @param field - Property of the candidate holding the connectors of the feed
   * @returns The connectors, empty when no listing is chosen
   */
  alternativesOf(field: keyof SecurityLookupCandidate): ConnectorAlternative[] {
    return (this.selectedCandidate()?.[field] as ConnectorAlternative[]) ?? [];
  }

  /**
   * Tells whether a connector table is disabled: the dividend table of an instrument that distributes nothing, for
   * which taking over a dividend connector makes no sense.
   *
   * @param feed - The feed of the table
   * @returns True when no connector of this feed may be chosen
   */
  isFeedDisabled(feed: LookupFeed): boolean {
    return feed === 'dividend' && !!this.selectedCandidate()?.distributesNothing;
  }

  /** Hands the chosen listing and connectors to the security form and closes the dialog. */
  apply(): void {
    const result: SecurityLookupSelection = {
      candidate: this.selectedCandidate(),
      nameCandidate: this.nameCandidate(),
      ...this.selection()
    };
    this.selectionHandled = true;
    this.closeDialog.emit(new ProcessedActionData(ProcessedAction.CREATED, result));
    this.visibleDialog.set(false);
  }

  private performLookup(): void {
    this.cancelPendingRequest();
    this.loading.set(true);
    this.lookupSubscription = this.securityLookupService
      .lookup(this.isin(), this.currency(), this.mic(), this.idAssetClass(), this.tickerSymbol())
      .subscribe({
        next: (response) => {
          this.nameCandidate.set(preselectNameCandidate(response.candidates));
          this.onCandidateSelected(response.candidates.length === 1 ? response.candidates[0] : null);
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

  private emptySelection(): Record<LookupFeed, ConnectorAlternative> {
    return { history: null, intra: null, dividend: null, split: null };
  }
}
