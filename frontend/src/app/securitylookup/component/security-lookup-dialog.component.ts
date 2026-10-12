import { ChangeDetectionStrategy, Component, EventEmitter, Input, NgZone, Output } from '@angular/core';
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
  LookupProviderStatus,
  SecurityLookupCandidate,
  SecurityLookupResponse,
  SecurityLookupSelection
} from '../model/security-lookup';
import { SecurityLookupBaseDataTableComponent } from './security-lookup-base-data-table.component';
import { SecurityLookupConnectorTableComponent } from './security-lookup-connector-table.component';
import { LookupFeed, preselectAlternative } from '../model/security-lookup-selection';

/**
 * Dialog of the security lookup. It searches the data providers by ISIN, currency and exchange as soon as it opens and
 * shows five tables: the listings with their master data, then the connectors for history, intraday, dividends and
 * splits of the chosen listing. Only a connector whose probe succeeded can be chosen, and a table with exactly one such
 * connector is preselected. "Apply" closes the dialog and hands the
 * chosen listing and connectors to the security form; nothing is saved here.
 */
@Component({
  selector: 'security-lookup-dialog',
  standalone: true,
  imports: [
    TranslateModule,
    DialogModule,
    ProgressSpinnerModule,
    MessageModule,
    ButtonModule,
    SecurityLookupBaseDataTableComponent,
    SecurityLookupConnectorTableComponent
  ],
  changeDetection: ChangeDetectionStrategy.Eager,
  template: `
    <p-dialog
      [header]="'SECURITY_LOOKUP_DIALOG' | translate"
      [visible]="visibleDialog"
      [style]="{ width: '1150px', minHeight: '400px' }"
      [modal]="true"
      [resizable]="true"
      (onShow)="onShow()"
      (onHide)="onHide()">
      @if (loading) {
        <div class="flex justify-content-center align-items-center" style="min-height: 200px">
          <p-progressSpinner></p-progressSpinner>
        </div>
      } @else {
        @if (errorMessage) {
          <p-message severity="error" [text]="errorMessage"></p-message>
        }
        @for (ps of failedProviders; track ps.providerId) {
          <p-message severity="warn" [text]="ps.providerId + ': ' + (ps.status | translate)"></p-message>
        }
        @if (response) {
          @if (response.candidates.length === 0) {
            <p-message severity="info" [text]="'SECURITY_LOOKUP_NO_RESULTS' | translate"></p-message>
          } @else {
            <security-lookup-base-data-table
              [candidates]="response.candidates"
              [selectedCandidate]="selectedCandidate"
              (candidateSelected)="onCandidateSelected($event)">
            </security-lookup-base-data-table>
            @if (selectedCandidate) {
              @for (feed of visibleFeeds(); track feed.key) {
                <security-lookup-connector-table
                  [titleKey]="feed.titleKey"
                  [feed]="feed.key"
                  [alternatives]="alternativesOf(feed.field)"
                  [selectedAlternative]="selection[feed.key]"
                  [showDistributionFrequency]="feed.key === 'dividend'"
                  [disabled]="isFeedDisabled(feed.key)"
                  disabledMessageKey="DIVIDEND_CONNECTOR_NOT_NEEDED"
                  (alternativeSelected)="selection[feed.key] = $event">
                </security-lookup-connector-table>
              }
            }
            <div class="flex justify-content-end mt-3">
              <p-button [label]="'APPLY' | translate" (onClick)="apply()" [disabled]="!selectedCandidate">
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
  @Input() visibleDialog = false;
  @Input() isin: string;
  @Input() currency: string;
  /** MIC of the stock exchange chosen in the security form */
  @Input() mic: string;
  /** Asset class chosen in the form; selects the providers and connectors */
  @Input() idAssetClass: number;
  /** Ticker entered in the form; helps providers that know the ISIN only for another listing */
  @Input() tickerSymbol: string;

  @Output() closeDialog = new EventEmitter<ProcessedActionData>();

  readonly feeds: { key: LookupFeed; field: keyof SecurityLookupCandidate; titleKey: string }[] = [
    { key: 'history', field: 'historyConnectors', titleKey: 'ID_CONNECTOR_HISTORY' },
    { key: 'intra', field: 'intraConnectors', titleKey: 'ID_CONNECTOR_INTRA' },
    { key: 'dividend', field: 'dividendConnectors', titleKey: 'ID_CONNECTOR_DIVIDEND' },
    { key: 'split', field: 'splitConnectors', titleKey: 'ID_CONNECTOR_SPLIT' }
  ];

  loading = false;
  response: SecurityLookupResponse;
  errorMessage: string;
  /** Providers whose search did not succeed; their status is shown above the tables */
  failedProviders: LookupProviderStatus[] = [];
  selectedCandidate: SecurityLookupCandidate;
  selection: Record<LookupFeed, ConnectorAlternative> = this.emptySelection();

  /** Prevents a second emission when "Apply" closes the dialog */
  private selectionHandled = false;
  private lookupSubscription: Subscription;

  constructor(
    private securityLookupService: SecurityLookupService,
    private ngZone: NgZone
  ) {}

  /**
   * Starts the lookup when the dialog has opened. Optimus UI emits onShow outside the NgZone, so the work is moved
   * back into the zone; otherwise the answer would not trigger change detection and the spinner would remain.
   */
  onShow(): void {
    this.ngZone.run(() => {
      this.selectionHandled = false;
      this.errorMessage = null;
      this.response = null;
      this.failedProviders = [];
      this.onCandidateSelected(null);
      this.performLookup();
    });
  }

  /** Cancels a running lookup and reports a close without selection. */
  onHide(): void {
    this.ngZone.run(() => {
      this.cancelPendingRequest();
      if (!this.selectionHandled) {
        this.closeDialog.emit(new ProcessedActionData(ProcessedAction.NO_CHANGE));
      }
    });
  }

  /**
   * Shows the connectors of the chosen listing and preselects every connector table with exactly one usable connector,
   * however many others failed their probe.
   *
   * @param candidate - The chosen listing, or null
   */
  onCandidateSelected(candidate: SecurityLookupCandidate): void {
    this.selectedCandidate = candidate;
    this.selection = this.emptySelection();
    if (candidate) {
      this.feeds.forEach((feed) => {
        this.selection[feed.key] = this.isFeedDisabled(feed.key)
          ? null
          : preselectAlternative(feed.key, this.alternativesOf(feed.field));
      });
    }
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
        (feed.key !== 'dividend' || this.response?.dividendConnectorApplicable !== false) &&
        (feed.key !== 'split' || this.response?.splitConnectorApplicable !== false)
    );
  }

  /**
   * Connectors of the chosen listing for one feed.
   *
   * @param field - Property of the candidate holding the connectors of the feed
   * @returns The connectors, empty when no listing is chosen
   */
  alternativesOf(field: keyof SecurityLookupCandidate): ConnectorAlternative[] {
    return (this.selectedCandidate?.[field] as ConnectorAlternative[]) ?? [];
  }

  /**
   * Tells whether a connector table is disabled: the dividend table of an instrument that distributes nothing, for
   * which taking over a dividend connector makes no sense.
   *
   * @param feed - The feed of the table
   * @returns True when no connector of this feed may be chosen
   */
  isFeedDisabled(feed: LookupFeed): boolean {
    return feed === 'dividend' && !!this.selectedCandidate?.distributesNothing;
  }

  /** Hands the chosen listing and connectors to the security form and closes the dialog. */
  apply(): void {
    const result: SecurityLookupSelection = { candidate: this.selectedCandidate, ...this.selection };
    this.selectionHandled = true;
    this.closeDialog.emit(new ProcessedActionData(ProcessedAction.CREATED, result));
    this.visibleDialog = false;
  }

  private performLookup(): void {
    this.cancelPendingRequest();
    this.loading = true;
    this.lookupSubscription = this.securityLookupService
      .lookup(this.isin, this.currency, this.mic, this.idAssetClass, this.tickerSymbol)
      .subscribe({
        next: (response) => {
          this.response = response;
          // A provider that does not cover the exchange (SIX outside XSWX) or found nothing is no failure
          this.failedProviders = response.providerStatus.filter(
            (ps) => !['OK', 'NO_MATCH', 'UNSUPPORTED_QUERY'].includes(ps.status)
          );
          this.onCandidateSelected(response.candidates.length === 1 ? response.candidates[0] : null);
          this.loading = false;
        },
        error: (error) => {
          this.loading = false;
          this.errorMessage = error.message;
        }
      });
  }

  private cancelPendingRequest(): void {
    if (this.lookupSubscription) {
      this.lookupSubscription.unsubscribe();
      this.lookupSubscription = null;
    }
    this.loading = false;
  }

  private emptySelection(): Record<LookupFeed, ConnectorAlternative> {
    return { history: null, intra: null, dividend: null, split: null };
  }
}
