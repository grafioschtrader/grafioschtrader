import { ChangeDetectionStrategy, Component, Input, OnChanges, OnDestroy, SimpleChanges } from '@angular/core';
import { TranslateModule } from '@ngx-translate/core';
import { ProgressSpinnerModule } from '@openng/optimus-ui/progressspinner';
import { Subscription, concatMap, finalize, from, switchMap, tap } from 'rxjs';
import { UDFMetadataSecurityService } from '../service/udf.metadata.security.service';
import { UDFFillTestRequest, UDFSuggestTestCandidate, UDFSuggestTestResult } from '../model/udf.metadata.security';

/**
 * Tests the description for the language model of a draft field on up to 5 securities and shows, below the edit form,
 * what the model answered and how the check against the source judged it. The server picks the securities, those
 * with a value first; they are asked one after the other, so every answer appears as soon as it is there and no single
 * request has to wait for all of them. Nothing is saved. Removing the component cancels the pending request; a call
 * already started at the provider may still be billed.
 */
@Component({
  selector: 'udf-fill-suggest-test-result',
  template: `
    <div>
      <p>{{ 'UDF_FILL_TEST_LLM_INFO' | translate }}</p>
      @if (candidates?.length === 0) {
        <p>{{ 'UDF_FILL_TEST_LLM_NONE' | translate }}</p>
      }
      @if (running && candidates?.length > 0) {
        <div class="test-progress">
          <p-progressSpinner [style]="{ width: '2rem', height: '2rem' }"></p-progressSpinner>
          <span>{{
            'UDF_FILL_TEST_LLM_PROGRESS' | translate: { current: results.size + 1, total: candidates.length }
          }}</span>
        </div>
      }
      <ul>
        @for (c of candidates; track c.idSecuritycurrency) {
          @let r = results.get(c.idSecuritycurrency);
          @if (r) {
            <li>
              <b>{{ c.name }}</b
              >: {{ 'UDF_FILL_TEST_LLM_' + r.outcome | translate }}
              @if (r.value) {
                &mdash; <i>{{ r.value }}</i>
              }
              @if (r.asOfDate) {
                ({{ r.asOfDate }})
              }
              @if (r.equalsExisting === true) {
                , {{ 'UDF_FILL_TEST_LLM_EQUAL' | translate }}
              } @else if (r.equalsExisting === false) {
                , {{ 'UDF_FILL_TEST_LLM_DIFFERING' | translate: { existing: c.existingValue } }}
              } @else if (c.existingValue) {
                <br />{{ 'UDF_FILL_TEST_LLM_EXISTING' | translate: { existing: c.existingValue } }}
              }
              @if (r.note) {
                <br /><i>{{ r.note }}</i>
              }
              @if (r.evidence) {
                <br /><span>«{{ r.evidence }}»</span>
              }
              @if (r.sourceUrl) {
                <br /><a [href]="r.sourceUrl" target="_blank" rel="noopener noreferrer">{{ r.sourceUrl }}</a>
              }
              @if (r.fetches?.length > 0) {
                <br /><span class="test-fetches">{{ 'UDF_FILL_TEST_LLM_FETCHES' | translate }}</span>
                <ul class="test-fetches">
                  @for (f of r.fetches; track $index) {
                    <li>{{ f }}</li>
                  }
                </ul>
              }
            </li>
          }
        }
      </ul>
    </div>
  `,
  styles: [
    `
      /* Shrinks to the height the dialog has left below the form and scrolls there (parent content is a flex column) */
      :host {
        display: block;
        flex: 0 1 auto;
        min-height: 6rem;
        overflow-y: auto;
        word-break: break-word;
      }
      ul {
        padding-left: 1.2rem;
        margin-bottom: 0.5rem;
      }
      li {
        margin-bottom: 0.5rem;
      }
      .test-fetches {
        font-size: 0.85em;
      }
      ul.test-fetches li {
        margin-bottom: 0;
      }
      .test-progress {
        display: flex;
        align-items: center;
        gap: 0.5rem;
        margin-bottom: 0.5rem;
      }
    `
  ],
  standalone: true,
  changeDetection: ChangeDetectionStrategy.Eager,
  imports: [TranslateModule, ProgressSpinnerModule]
})
export class UDFFillSuggestTestResultComponent implements OnChanges, OnDestroy {
  /** The draft of the edit form; every new object starts a new test */
  @Input() request: UDFFillTestRequest;

  /** The securities picked by the server, null while they are loading */
  candidates: UDFSuggestTestCandidate[];

  /** The answers received so far, by security */
  results = new Map<number, UDFSuggestTestResult>();

  /** True while securities are still being asked */
  running = false;

  private subscription: Subscription;

  /**
   * Creates the component.
   *
   * @param uDFMetadataSecurityService - Service with the test endpoints
   */
  constructor(private uDFMetadataSecurityService: UDFMetadataSecurityService) {}

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['request'] && this.request) {
      this.start(this.request);
    }
  }

  /**
   * Loads the securities and asks them one after the other; a failing request, such as an exhausted budget, ends the
   * test and its message is shown by the service.
   */
  private start(request: UDFFillTestRequest): void {
    this.subscription?.unsubscribe();
    this.candidates = null;
    this.results = new Map();
    this.running = true;
    this.subscription = this.uDFMetadataSecurityService
      .getSuggestTestCandidates(request)
      .pipe(
        tap((candidates) => (this.candidates = candidates)),
        switchMap((candidates) => from(candidates)),
        concatMap((c) => this.uDFMetadataSecurityService.testSuggestion(request, c.idSecuritycurrency)),
        finalize(() => (this.running = false))
      )
      .subscribe((result) => this.results.set(result.idSecuritycurrency, result));
  }

  ngOnDestroy(): void {
    this.subscription?.unsubscribe();
  }
}
