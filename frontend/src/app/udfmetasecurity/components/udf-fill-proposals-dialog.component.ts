import { ChangeDetectionStrategy, Component, EventEmitter, Input, Output } from '@angular/core';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { DialogModule } from '@openng/optimus-ui/dialog';
import { ButtonModule } from '@openng/optimus-ui/button';
import { UDFMetadataSecurityService } from '../service/udf.metadata.security.service';
import { UDFFillProposal, UDFFillProposalDecisionResult, UDFMetadataSecurity } from '../model/udf.metadata.security';
import { MessageToastService } from '../../lib/message/message.toast.service';
import { InfoLevelType } from '../../lib/message/info.leve.type';

/**
 * Lists the values a language model proposed for a field with the fill source LLM, each with its source and excerpt,
 * and lets the user accept or reject them. A proposal is checked against its source but that establishes plausibility,
 * not truth; it is written into the field only when accepted. From here the user also starts a new paid run and asks
 * again for rejected, discarded and not found cells. It is the place that reaches securities which are in no
 * watchlist.
 */
@Component({
  selector: 'udf-fill-proposals-dialog',
  template: `
    <p-dialog
      header="{{ 'UDF_FILL_PROPOSALS' | translate }}: {{ field?.description }}"
      [visible]="visibleDialog"
      [style]="{ width: '900px' }"
      [modal]="true"
      appendTo="body"
      (onShow)="load()"
      (onHide)="closeDialog.emit()">
      <div class="proposal-actions">
        <p-button [label]="'UDF_FILL_SUGGEST' | translate" (onClick)="suggest()"></p-button>
        <p-button [label]="'UDF_FILL_RETRY' | translate" severity="secondary" (onClick)="retry()"></p-button>
      </div>
      @if (decisionText) {
        <p class="bg-info">{{ decisionText }}</p>
      }
      @if (proposals.length === 0) {
        <p>{{ 'UDF_FILL_PROPOSALS_NONE' | translate }}</p>
      } @else {
        <div class="proposal-list">
          @for (p of proposals; track p.idSecuritycurrency) {
            <label class="proposal-row">
              <input
                type="checkbox"
                [checked]="selected.has(p.idSecuritycurrency)"
                (change)="toggle(p.idSecuritycurrency)" />
              <b>{{ p.name }}</b
              >: <i>{{ p.value }}</i>
              @if (p.asOfDate) {
                ({{ p.asOfDate }})
              }
              <br />
              <span class="proposal-evidence">«{{ p.evidence }}»</span>
              <a [href]="p.sourceUrl" target="_blank" rel="noopener noreferrer">{{ p.sourceUrl }}</a>
            </label>
          }
        </div>
        <div class="proposal-actions">
          <p-button
            [label]="'UDF_FILL_ACCEPT_SELECTED' | translate"
            [disabled]="selected.size === 0"
            (onClick)="decide(true, false)"></p-button>
          <p-button [label]="'UDF_FILL_ACCEPT_ALL' | translate" (onClick)="decide(true, true)"></p-button>
          <p-button
            [label]="'UDF_FILL_REJECT_SELECTED' | translate"
            severity="secondary"
            [disabled]="selected.size === 0"
            (onClick)="decide(false, false)"></p-button>
        </div>
      }
    </p-dialog>
  `,
  styles: [
    `
      .proposal-actions {
        display: flex;
        gap: 0.5rem;
        margin: 0.5rem 0;
      }
      .proposal-list {
        max-height: 420px;
        overflow-y: auto;
        word-break: break-word;
      }
      .proposal-row {
        display: block;
        margin-bottom: 0.5rem;
      }
      .proposal-evidence {
        margin-right: 0.5rem;
      }
    `
  ],
  standalone: true,
  changeDetection: ChangeDetectionStrategy.Eager,
  imports: [DialogModule, ButtonModule, TranslateModule]
})
export class UDFFillProposalsDialogComponent {
  /** The field with the fill source LLM */
  @Input() field: UDFMetadataSecurity;

  /** Controls the visibility of the dialog */
  @Input() visibleDialog: boolean;

  /** Emits when the dialog closes */
  @Output() closeDialog = new EventEmitter<void>();

  proposals: UDFFillProposal[] = [];
  selected = new Set<number>();
  decisionText: string;

  /**
   * Creates the dialog.
   *
   * @param uDFMetadataSecurityService - Service with the proposal endpoints
   * @param translateService - Translation of the summary of a decision
   * @param messageToastService - Shows that a run was queued
   */
  constructor(
    private uDFMetadataSecurityService: UDFMetadataSecurityService,
    private translateService: TranslateService,
    private messageToastService: MessageToastService
  ) {}

  load(): void {
    this.uDFMetadataSecurityService.getProposals(this.field.idUDFMetadata).subscribe((proposals) => {
      this.proposals = proposals;
      this.selected = new Set([...this.selected].filter((id) => proposals.some((p) => p.idSecuritycurrency === id)));
    });
  }

  toggle(idSecuritycurrency: number): void {
    this.selected.has(idSecuritycurrency)
      ? this.selected.delete(idSecuritycurrency)
      : this.selected.add(idSecuritycurrency);
  }

  /** Starts a paid run of the language model. */
  suggest(): void {
    this.uDFMetadataSecurityService
      .suggestValues(this.field.idUDFMetadata)
      .subscribe(() => this.messageToastService.showMessageI18n(InfoLevelType.INFO, 'UDF_FILL_SUGGEST_QUEUED'));
  }

  /** Lets the next run ask again for rejected, discarded and not found cells. */
  retry(): void {
    this.uDFMetadataSecurityService.retryProposals(this.field.idUDFMetadata).subscribe(() => this.load());
  }

  /**
   * Accepts or rejects the selected or all proposals, naming the version and value the user saw.
   *
   * @param accept - True to accept, false to reject
   * @param all - True to decide about every listed proposal
   */
  decide(accept: boolean, all: boolean): void {
    const decisions = this.proposals
      .filter((p) => all || this.selected.has(p.idSecuritycurrency))
      .map((p) => ({ idSecuritycurrency: p.idSecuritycurrency, ruleVersion: p.ruleVersion, value: p.value }));
    const call = accept
      ? this.uDFMetadataSecurityService.acceptProposals(this.field.idUDFMetadata, decisions)
      : this.uDFMetadataSecurityService.rejectProposals(this.field.idUDFMetadata, decisions);
    call.subscribe((results) => {
      this.decisionText = this.summarize(results);
      this.selected.clear();
      this.load();
    });
  }

  private summarize(results: UDFFillProposalDecisionResult[]): string {
    const count = (outcome: string) => results.filter((r) => r.outcome === outcome).length;
    return this.translateService.instant('UDF_FILL_DECISION_RESULT', {
      applied: count('APPLIED'),
      rejected: count('REJECTED'),
      conflict: count('CONFLICT'),
      stale: count('STALE')
    });
  }
}
