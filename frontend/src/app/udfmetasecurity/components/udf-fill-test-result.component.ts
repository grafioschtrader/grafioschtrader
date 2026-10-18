import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { TranslateModule } from '@ngx-translate/core';
import { UDFFillTestResult } from '../model/udf.metadata.security';

/**
 * Shows the result of testing a fill rule below the edit form of a user-defined security field: the counters, the
 * existing values the rule does not reproduce and the values it computes for securities without a value. The server
 * already limits and sorts both lists; nothing here is saved.
 */
@Component({
  selector: 'udf-fill-test-result',
  template: `
    @if (testResult) {
      <div>
        <p>{{ 'UDF_FILL_TEST_COUNTS' | translate: testResult }}</p>
        @if (testResult.mismatches.length > 0) {
          <h6>{{ 'UDF_FILL_TEST_MISMATCHES' | translate }}</h6>
          <ul>
            @for (m of testResult.mismatches; track $index) {
              <li>
                <b>{{ m.name }}</b
                >: {{ m.existing }} &rarr; {{ m.computed ?? '—' }}
                @if (m.reason) {
                  <i>({{ m.reason }})</i>
                }
              </li>
            }
          </ul>
        }
        @if (testResult.samples.length > 0) {
          <h6>{{ 'UDF_FILL_TEST_SAMPLES' | translate }}</h6>
          <ul>
            @for (s of testResult.samples; track $index) {
              <li>
                <b>{{ s.name }}</b
                >: {{ s.computed }}
              </li>
            }
          </ul>
        }
      </div>
    }
  `,
  styles: [
    `
      /* Shrinks to the height the dialog has left below the form and scrolls there (parent content is a flex column) */
      :host {
        display: block;
        flex: 0 1 auto;
        min-height: 6rem;
        overflow-y: auto;
        word-break: break-all;
      }
      ul {
        padding-left: 1.2rem;
        margin-bottom: 0.5rem;
      }
    `
  ],
  standalone: true,
  changeDetection: ChangeDetectionStrategy.Eager,
  imports: [TranslateModule]
})
export class UDFFillTestResultComponent {
  /** Result of the last test, null before the first one */
  @Input() testResult: UDFFillTestResult;
}
