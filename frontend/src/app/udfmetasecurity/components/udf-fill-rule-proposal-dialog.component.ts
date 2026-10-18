import { ChangeDetectionStrategy, Component, EventEmitter, Input, Output, NgZone, OnDestroy } from '@angular/core';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { ProgressSpinnerModule } from '@openng/optimus-ui/progressspinner';
import { Subscription, finalize } from 'rxjs';
import { DialogModule } from '@openng/optimus-ui/dialog';
import { ButtonModule } from '@openng/optimus-ui/button';
import { UDFMetadataSecurityService } from '../service/udf.metadata.security.service';
import { RuleProposalPreview, RuleProposalResult } from '../model/udf.metadata.security';
import { UDFFillTestResultComponent } from './udf-fill-test-result.component';

/**
 * Lets a language model propose a template rule for a user-defined security field. Before the first request the
 * dialog shows the language models in order of priority and every value that would leave the instance; the user may deselect values.
 * Only after "Send" one request goes out. The proposed rule is shown with the result of its test against all values;
 * the user can take it over into the rule of the field, which is saved only with the field.
 */
@Component({
  selector: 'udf-fill-rule-proposal-dialog',
  template: `
    <p-dialog
      header="{{ 'UDF_FILL_PROPOSE_TITLE' | translate }}"
      [visible]="visibleDialog"
      [style]="{ width: '850px' }"
      [modal]="true"
      appendTo="body"
      (onShow)="onShow()"
      (onHide)="close()">
      @if (loading) {
        <div class="flex justify-content-center align-items-center" style="min-height: 200px">
          <p-progressSpinner></p-progressSpinner>
        </div>
        <p>{{ 'UDF_FILL_PROPOSE_WAITING' | translate }}</p>
      } @else {
        @if (preview) {
          @if (preview.examples.length === 0) {
            <p>{{ 'UDF_FILL_PROPOSE_NONE' | translate }}</p>
          } @else {
            <p>{{ 'UDF_FILL_PROPOSE_CONSENT' | translate: { providers: providerLabels() } }}</p>
            <div class="proposal-examples">
              @for (e of preview.examples; track e.idSecuritycurrency) {
                <label class="proposal-example">
                  <input
                    type="checkbox"
                    [checked]="selected.has(e.idSecuritycurrency)"
                    (change)="toggle(e.idSecuritycurrency)" />
                  <b>{{ e.name }}</b> {{ e.isin }} {{ e.ticker }} {{ e.mic }} {{ e.currency }}: {{ e.value }}
                </label>
              }
            </div>
            <p-button
              [label]="'UDF_FILL_PROPOSE_SEND' | translate"
              [disabled]="selected.size === 0 || loading"
              (onClick)="send()"></p-button>
          }
        }
        @if (result) {
          <hr />
          @if (result.rule) {
            <pre class="proposal-rule">{{ result.rule }}</pre>
          }
          @if (result.parseError) {
            <p class="text-danger">{{ result.parseError }}</p>
          }
          <udf-fill-test-result [testResult]="result.testResult"></udf-fill-test-result>
          @if (result.rule && !result.parseError) {
            <p-button [label]="'UDF_FILL_PROPOSE_ACCEPT' | translate" (onClick)="accept()"></p-button>
          }
        }
      }
    </p-dialog>
  `,
  styles: [
    `
      .proposal-examples {
        max-height: 260px;
        overflow-y: auto;
        margin-bottom: 0.5rem;
        word-break: break-all;
      }
      .proposal-example {
        display: block;
      }
      .proposal-rule {
        white-space: pre-wrap;
        word-break: break-all;
      }
    `
  ],
  standalone: true,
  changeDetection: ChangeDetectionStrategy.Eager,
  imports: [DialogModule, ButtonModule, TranslateModule, ProgressSpinnerModule, UDFFillTestResultComponent]
})
export class UDFFillRuleProposalDialogComponent implements OnDestroy {
  /** The saved field the rule is proposed for */
  @Input() idUDFMetadata: number;

  /** Controls the visibility of the dialog */
  @Input() visibleDialog: boolean;

  /** Emits the proposed rule when the user takes it over */
  @Output() ruleAccepted = new EventEmitter<string>();

  /** Emits when the dialog closes */
  @Output() closeDialog = new EventEmitter<void>();

  preview: RuleProposalPreview;
  result: RuleProposalResult;
  selected = new Set<number>();
  loading = false;
  private pendingRequest: Subscription;

  /**
   * Creates the dialog.
   *
   * @param uDFMetadataSecurityService - Service with the proposal endpoints
   */
  constructor(
    private uDFMetadataSecurityService: UDFMetadataSecurityService,
    private ngZone: NgZone,
    private translateService: TranslateService
  ) {}

  /**
   * Loads what would be sent; nothing leaves the instance yet.
   */
  onShow(): void {
    this.ngZone.run(() => {
      this.cancelPendingRequest();
      this.preview = null;
      this.result = null;
      this.loading = true;
      this.pendingRequest = this.uDFMetadataSecurityService
        .previewRuleProposal(this.idUDFMetadata)
        .pipe(finalize(() => (this.loading = false)))
        .subscribe({
          next: (preview) => {
            this.preview = preview;
            this.selected = new Set(preview.examples.map((e) => e.idSecuritycurrency));
          },
          error: () => {} // The authenticated service displays the translated error.
        });
    });
  }

  providerLabels(): string {
    return this.preview.providers
      .map(
        (p) =>
          p.label +
          ', ' +
          this.translateService.instant(p.keySource === 'TENANT' ? 'AI_KEY_SOURCE_TENANT' : 'AI_KEY_SOURCE_INSTANCE')
      )
      .join('; ');
  }

  toggle(idSecuritycurrency: number): void {
    this.selected.has(idSecuritycurrency)
      ? this.selected.delete(idSecuritycurrency)
      : this.selected.add(idSecuritycurrency);
  }

  /**
   * Sends the selected examples; one paid request.
   */
  send(): void {
    if (this.loading || this.selected.size === 0) {
      return;
    }
    this.loading = true;
    this.result = null;
    this.pendingRequest = this.uDFMetadataSecurityService
      .proposeRule(this.idUDFMetadata, [...this.selected])
      .pipe(finalize(() => (this.loading = false)))
      .subscribe({
        next: (result) => {
          this.result = result;
        },
        error: () => {}
      });
  }

  accept(): void {
    this.ruleAccepted.emit(this.result.rule);
    this.close();
  }

  close(): void {
    this.ngZone.run(() => {
      this.cancelPendingRequest();
      this.closeDialog.emit();
    });
  }

  private cancelPendingRequest(): void {
    this.pendingRequest?.unsubscribe();
    this.pendingRequest = null;
    this.loading = false;
  }

  ngOnDestroy(): void {
    this.cancelPendingRequest();
  }
}
