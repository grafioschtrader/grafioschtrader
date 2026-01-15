import {Component, EventEmitter, Input, Output} from '@angular/core';
import {CommonModule} from '@angular/common';
import {TranslateModule, TranslateService} from '@ngx-translate/core';
import {DialogModule} from 'primeng/dialog';
import {ProgressSpinnerModule} from 'primeng/progressspinner';
import {MessageModule} from 'primeng/message';

import {GtnetSecurityLookupService} from '../service/gtnet-security-lookup.service';
import {
  ConnectorCapability,
  ConnectorHint,
  SecurityGtnetLookupDTO,
  SecurityGtnetLookupRequest,
  SecurityGtnetLookupResponse,
  SecurityGtnetLookupWithMatch
} from '../model/gtnet-security-lookup';
import {GtnetSecurityLookupTableComponent} from './gtnet-security-lookup-table.component';
import {ProcessedActionData} from '../../lib/types/processed.action.data';
import {ProcessedAction} from '../../lib/types/processed.action';
import {IFeedConnector} from '../../shared/securitycurrency/ifeed.connector';

/**
 * Dialog component for searching and selecting security metadata from GTNet peers.
 * Auto-searches when dialog opens using the provided search criteria.
 * Emits the selected SecurityGtnetLookupDTO when user applies selection.
 */
@Component({
  selector: 'gtnet-security-lookup-dialog',
  standalone: true,
  imports: [
    CommonModule,
    TranslateModule,
    DialogModule,
    ProgressSpinnerModule,
    MessageModule,
    GtnetSecurityLookupTableComponent
  ],
  template: `
    <p-dialog
      [header]="'GTNET_SECURITY_LOOKUP' | translate"
      [(visible)]="visibleDialog"
      [style]="{width: '900px', minHeight: '400px'}"
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

        @if (response && response.securities.length === 0 && !errorMessage) {
          <p-message severity="info" [text]="'GTNET_LOOKUP_NO_RESULTS' | translate"></p-message>
        }

        @if (processedSecurities && processedSecurities.length > 0) {
          <div class="mb-2 text-secondary">
            {{ 'PEERS_QUERIED' | translate }}: {{ response.peersQueried }} |
            {{ 'PEERS_RESPONDED' | translate }}: {{ response.peersResponded }}
          </div>
          <gtnet-security-lookup-table
            [securities]="processedSecurities"
            (securitySelected)="onSecuritySelected($event)">
          </gtnet-security-lookup-table>
        }
      }
    </p-dialog>
  `
})
export class GtnetSecurityLookupDialogComponent {

  @Input() visibleDialog = false;
  @Input() isin: string;
  @Input() currency: string;
  @Input() tickerSymbol: string;
  /** Local feed connectors for matching against GTNet hints */
  @Input() feedConnectors: IFeedConnector[] = [];

  @Output() closeDialog = new EventEmitter<ProcessedActionData>();

  loading = false;
  response: SecurityGtnetLookupResponse;
  processedSecurities: SecurityGtnetLookupWithMatch[] = [];
  errorMessage: string;

  /** Flag to prevent double emission when selection closes the dialog */
  private selectionHandled = false;

  constructor(
    private lookupService: GtnetSecurityLookupService,
    private translateService: TranslateService
  ) {}

  onShow(): void {
    this.selectionHandled = false;
    this.errorMessage = null;
    this.response = null;
    this.processedSecurities = [];
    this.performSearch();
  }

  onHide(): void {
    if (!this.selectionHandled) {
      this.closeDialog.emit(new ProcessedActionData(ProcessedAction.NO_CHANGE));
    }
  }

  onSecuritySelected(security: SecurityGtnetLookupWithMatch): void {
    this.selectionHandled = true;
    this.closeDialog.emit(new ProcessedActionData(ProcessedAction.CREATED, security));
    this.visibleDialog = false;
  }

  private performSearch(): void {
    const request: SecurityGtnetLookupRequest = {
      isin: this.isin || undefined,
      currency: this.currency || undefined,
      tickerSymbol: this.tickerSymbol || undefined
    };

    this.loading = true;
    this.lookupService.lookupSecurity(request).subscribe({
      next: (response) => {
        this.response = response;
        this.processedSecurities = this.processAndDeduplicateSecurities(response.securities);
        this.loading = false;
      },
      error: (error) => {
        this.loading = false;
        this.errorMessage = error.message || 'GTNET_LOOKUP_ERROR';
      }
    });
  }

  /**
   * Processes securities by calculating connector match scores and deduplicating.
   * For securities with the same ISIN+exchange, keeps only the one with the best connector match.
   */
  private processAndDeduplicateSecurities(securities: SecurityGtnetLookupDTO[]): SecurityGtnetLookupWithMatch[] {
    // Calculate match scores and enrich with matched connector info
    const enrichedSecurities = securities.map(s => this.calculateConnectorMatch(s));

    // Group by ISIN + stockexchangeMic
    const groups = new Map<string, SecurityGtnetLookupWithMatch[]>();
    enrichedSecurities.forEach(security => {
      const key = `${security.isin}_${security.stockexchangeMic}`;
      const existing = groups.get(key) || [];
      existing.push(security);
      groups.set(key, existing);
    });

    // Keep only the best match from each group
    const deduplicated: SecurityGtnetLookupWithMatch[] = [];
    groups.forEach(group => {
      // Sort by score descending, then by source domain for consistency
      group.sort((a, b) => {
        if (b.connectorMatchScore !== a.connectorMatchScore) {
          return b.connectorMatchScore - a.connectorMatchScore;
        }
        return (a.sourceDomain || '').localeCompare(b.sourceDomain || '');
      });
      deduplicated.push(group[0]);
    });

    // Sort final list by score descending, then by name
    return deduplicated.sort((a, b) => {
      if (b.connectorMatchScore !== a.connectorMatchScore) {
        return b.connectorMatchScore - a.connectorMatchScore;
      }
      return (a.name || '').localeCompare(b.name || '');
    });
  }

  /**
   * Calculates connector match score for a security based on its hints vs local connectors.
   * Score is based on: number of matched connectors, with bonus for non-API-key connectors.
   */
  private calculateConnectorMatch(security: SecurityGtnetLookupDTO): SecurityGtnetLookupWithMatch {
    const result: SecurityGtnetLookupWithMatch = {
      ...security,
      connectorMatchScore: 0
    };

    if (!security.connectorHints || security.connectorHints.length === 0) {
      return result;
    }

    const localConnectorIds = new Set(this.feedConnectors.map(fc => fc.id));

    for (const hint of security.connectorHints) {
      // Check if this connector family matches any local connector
      const matchedConnector = this.feedConnectors.find(fc =>
        fc.id === hint.connectorFamily || fc.domain === hint.connectorFamily
      );

      if (matchedConnector) {
        // Score: 2 points for match without API key requirement, 1 point with API key
        const score = hint.requiresApiKey ? 1 : 2;

        for (const capability of hint.capabilities) {
          switch (capability) {
            case ConnectorCapability.HISTORY:
              if (!result.matchedHistoryConnector) {
                result.matchedHistoryConnector = matchedConnector.id;
                result.matchedHistoryUrlExtension = hint.urlExtensionPattern;
                result.connectorMatchScore += score;
              }
              break;
            case ConnectorCapability.INTRADAY:
              if (!result.matchedIntraConnector) {
                result.matchedIntraConnector = matchedConnector.id;
                result.matchedIntraUrlExtension = hint.urlExtensionPattern;
                result.connectorMatchScore += score;
              }
              break;
            case ConnectorCapability.DIVIDEND:
              if (!result.matchedDividendConnector) {
                result.matchedDividendConnector = matchedConnector.id;
                result.matchedDividendUrlExtension = hint.urlExtensionPattern;
                result.connectorMatchScore += score;
              }
              break;
            case ConnectorCapability.SPLIT:
              if (!result.matchedSplitConnector) {
                result.matchedSplitConnector = matchedConnector.id;
                result.matchedSplitUrlExtension = hint.urlExtensionPattern;
                result.connectorMatchScore += score;
              }
              break;
          }
        }
      }
    }

    return result;
  }
}
