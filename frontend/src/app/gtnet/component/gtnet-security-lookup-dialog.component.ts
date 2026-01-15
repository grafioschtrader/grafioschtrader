import {Component, EventEmitter, Input, Output} from '@angular/core';
import {CommonModule} from '@angular/common';
import {TranslateModule, TranslateService} from '@ngx-translate/core';
import {DialogModule} from 'primeng/dialog';
import {ProgressSpinnerModule} from 'primeng/progressspinner';
import {MessageModule} from 'primeng/message';

import {GtnetSecurityLookupService} from '../service/gtnet-security-lookup.service';
import {
  SecurityGtnetLookupDTO,
  SecurityGtnetLookupRequest,
  SecurityGtnetLookupResponse
} from '../model/gtnet-security-lookup';
import {GtnetSecurityLookupTableComponent} from './gtnet-security-lookup-table.component';
import {ProcessedActionData} from '../../lib/types/processed.action.data';
import {ProcessedAction} from '../../lib/types/processed.action';

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

        @if (response && response.securities.length > 0) {
          <div class="mb-2 text-secondary">
            {{ 'PEERS_QUERIED' | translate }}: {{ response.peersQueried }} |
            {{ 'PEERS_RESPONDED' | translate }}: {{ response.peersResponded }}
          </div>
          <gtnet-security-lookup-table
            [securities]="response.securities"
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

  @Output() closeDialog = new EventEmitter<ProcessedActionData>();

  loading = false;
  response: SecurityGtnetLookupResponse;
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
    this.performSearch();
  }

  onHide(): void {
    if (!this.selectionHandled) {
      this.closeDialog.emit(new ProcessedActionData(ProcessedAction.NO_CHANGE));
    }
  }

  onSecuritySelected(security: SecurityGtnetLookupDTO): void {
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
        this.loading = false;
      },
      error: (error) => {
        this.loading = false;
        this.errorMessage = error.message || 'GTNET_LOOKUP_ERROR';
      }
    });
  }
}
