import { Component, computed, OnInit, signal, input, inject } from '@angular/core';

import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { SimpleFormEditBase } from '../../lib/edit/simple.form.edit.base';
import { GenericConnectorEndpoint } from '../../entities/generic.connector.endpoint';
import { GenericConnectorDef } from '../../entities/generic.connector.def';
import { GenericConnectorTestRequest, GenericConnectorTestResult } from '../model/generic-connector-test.model';
import { GenericConnectorDefService } from '../service/generic.connector.def.service';
import { AppHelper } from '../../lib/helper/app.helper';
import { DynamicFieldHelper } from '../../lib/helper/dynamic.field.helper';
import { TranslateHelper } from '../../lib/helper/translate.helper';
import { AppHelpIds } from '../../shared/help/help.ids';
import { DataType } from '../../lib/dynamic-form/models/data.type';
import { DialogModule } from '@openng/optimus-ui/dialog';
import { DYNAMIC_FORM_IMPORTS } from '../../lib/dynamic-form/dynamic-form.imports';
import { FieldsetModule } from '@openng/optimus-ui/fieldset';
import { TableModule } from '@openng/optimus-ui/table';
import moment from 'moment';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Subscription } from 'rxjs';
import { MessageToastService } from '../../lib/message/message.toast.service';
import { InfoLevelType } from '../../lib/message/info.leve.type';

/**
 * Test dialog for verifying a generic connector endpoint configuration. Lets the user enter a ticker (or currency
 * pair) and optional date range, then sends a test request to the backend and displays the HTTP response, parsed
 * data rows, and any errors. The dialog stays open after submit so the user can adjust parameters and re-test.
 */
@Component({
  selector: 'generic-connector-test-dialog',
  template: `
    <p-dialog
      header="{{ 'TEST_ENDPOINT' | translate }}"
      [visible]="visibleDialog()"
      [style]="{ width: '900px' }"
      (onShow)="onShow($event)"
      (onHide)="onHide($event)"
      [modal]="true">
      <dynamic-form
        [config]="config"
        [formConfig]="formConfig"
        [translateService]="translateService"
        #form="dynamicForm"
        (submitBt)="submit($event)" />

      @if (testResult(); as result) {
        <p-fieldset legend="{{ 'TEST_RESULT' | translate }}" class="mt-3">
          @for (item of summaryItems(); track item.key) {
            <div class="mb-1">
              <strong>{{ item.label }}:</strong> {{ item.value }}
            </div>
          }
        </p-fieldset>

        @if (result.parsedRows && result.parsedRows.length > 0) {
          <p-fieldset legend="{{ 'PARSED_DATA' | translate }}" class="mt-3">
            <div style="max-height: 300px; overflow-y: auto;">
              <p-table [value]="result.parsedRows" [scrollable]="true" styleClass="p-datatable-sm">
                <ng-template #header>
                  <tr>
                    @for (col of parsedColumns(); track col) {
                      <th>{{ col }}</th>
                    }
                  </tr>
                </ng-template>
                <ng-template #body let-row>
                  <tr>
                    @for (col of parsedColumns(); track col) {
                      <td>{{ row[col] }}</td>
                    }
                  </tr>
                </ng-template>
              </p-table>
            </div>
          </p-fieldset>
        }

        @if (result.rawResponseSnippet) {
          <p-fieldset legend="{{ 'RAW_RESPONSE' | translate }}" [toggleable]="true" [collapsed]="true" class="mt-3">
            <pre style="max-height: 200px; overflow-y: auto; white-space: pre-wrap; word-break: break-all;">{{
              result.rawResponseSnippet
            }}</pre>
          </p-fieldset>
        }
      }
    </p-dialog>
  `,
  imports: [TranslateModule, DialogModule, ...DYNAMIC_FORM_IMPORTS, FieldsetModule, TableModule]
})
export class GenericConnectorTestDialogComponent extends SimpleFormEditBase implements OnInit {
  public translateService: TranslateService = inject(TranslateService);
  private genericConnectorDefService: GenericConnectorDefService = inject(GenericConnectorDefService);
  private readonly messageToastService = inject(MessageToastService);

  readonly endpoint = input.required<GenericConnectorEndpoint>();
  readonly connectorDef = input.required<GenericConnectorDef>();

  readonly testResult = signal<GenericConnectorTestResult | null>(null);
  readonly parsedColumns = computed(() => {
    const rows = this.testResult()?.parsedRows;
    return rows?.length ? Object.keys(rows[0]) : [];
  });
  private readonly summaryTranslations = signal<Record<string, string>>({});
  readonly summaryItems = computed(() => {
    const result = this.testResult();
    if (!result) return [];
    const translations = this.summaryTranslations();
    const items = [
      { key: 'success', label: translations['SUCCESS'], value: result.success },
      { key: 'httpStatus', label: translations['HTTP_STATUS'], value: result.httpStatus },
      { key: 'requestUrl', label: translations['REQUEST_URL'], value: result.requestUrl },
      { key: 'executionTimeMs', label: translations['EXECUTION_TIME_MS'], value: result.executionTimeMs }
    ];
    return result.errorMessage
      ? [...items, { key: 'errorMessage', label: translations['ERROR_MESSAGE'], value: result.errorMessage }]
      : items;
  });
  private testSubscription?: Subscription;

  constructor() {
    super(AppHelpIds.HELP_BASEDATA_GENERIC_CONNECTOR);
  }

  ngOnInit(): void {
    this.formConfig = AppHelper.getDefaultFormConfig(this.gps, 5, this.helpLink.bind(this));
    this.config = [
      DynamicFieldHelper.createFieldInputStringHeqF('ticker', 64, false),
      DynamicFieldHelper.createFieldInputStringHeqF('fromCurrency', 3, false),
      DynamicFieldHelper.createFieldInputStringHeqF('toCurrency', 3, false),
      DynamicFieldHelper.createFieldPcalendarHeqF(DataType.DateString, 'fromDate', false),
      DynamicFieldHelper.createFieldPcalendarHeqF(DataType.DateString, 'toDate', false),
      DynamicFieldHelper.createSubmitButton('TEST')
    ];
    this.configObject = TranslateHelper.prepareFieldsAndErrors(this.translateService, this.config);
    this.applyFieldVisibility();

    const keys = ['SUCCESS', 'HTTP_STATUS', 'REQUEST_URL', 'EXECUTION_TIME_MS', 'ERROR_MESSAGE'];
    this.translateService
      .get(keys)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((translations) => this.summaryTranslations.set(translations));
  }

  protected override initialize(): void {
    this.testResult.set(null);
    this.applyFieldVisibility();
  }

  private applyFieldVisibility(): void {
    const endpoint = this.endpoint();
    const isCurrencyPairStrategy =
      endpoint.tickerBuildStrategy === 'CURRENCY_PAIR' && endpoint.instrumentType === 'CURRENCY';
    const isHistory = this.endpoint().feedSupport === 'FS_HISTORY';

    this.configObject.ticker.invisible = isCurrencyPairStrategy;
    this.configObject.fromCurrency.invisible = !isCurrencyPairStrategy;
    this.configObject.toCurrency.invisible = !isCurrencyPairStrategy;
    this.configObject.fromDate.invisible = !isHistory;
    this.configObject.toDate.invisible = !isHistory;
  }

  submit(value: { [key: string]: any }): void {
    const idGenericConnector = this.connectorDef().idGenericConnector;
    const endpoint = this.endpoint();
    const { feedSupport, instrumentType } = endpoint;
    if (idGenericConnector == null || feedSupport == null || instrumentType == null) {
      this.messageToastService.showMessageI18n(InfoLevelType.WARNING, 'REQUIRED_FIELDS_MISSING');
      this.configObject.submit.disabled = false;
      return;
    }
    const request: GenericConnectorTestRequest = {
      idGenericConnector,
      feedSupport,
      instrumentType
    };

    if (endpoint.tickerBuildStrategy === 'CURRENCY_PAIR' && endpoint.instrumentType === 'CURRENCY') {
      request.fromCurrency = value.fromCurrency;
      request.toCurrency = value.toCurrency;
    } else {
      request.ticker = value.ticker;
    }

    if (endpoint.feedSupport === 'FS_HISTORY') {
      if (value.fromDate) {
        request.fromDate = moment(value.fromDate).format('YYYY-MM-DD');
      }
      if (value.toDate) {
        request.toDate = moment(value.toDate).format('YYYY-MM-DD');
      }
    }

    this.testSubscription?.unsubscribe();
    this.testResult.set(null);
    this.testSubscription = this.genericConnectorDefService
      .testEndpoint(request)
      .pipe(this.untilDialogClosed())
      .subscribe({
        next: (result) => {
          this.testResult.set(result);
          this.configObject.submit.disabled = false;
        },
        error: () => {
          this.configObject.submit.disabled = false;
        }
      });
  }
}
