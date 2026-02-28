import {Component, Input, OnInit} from '@angular/core';
import {CommonModule} from '@angular/common';
import {TranslateModule, TranslateService} from '@ngx-translate/core';
import {SimpleEditBase} from '../../lib/edit/simple.edit.base';
import {GenericConnectorEndpoint} from '../../entities/generic.connector.endpoint';
import {GenericConnectorDef} from '../../entities/generic.connector.def';
import {GenericConnectorTestRequest, GenericConnectorTestResult} from '../model/generic-connector-test.model';
import {GenericConnectorDefService} from '../service/generic.connector.def.service';
import {GlobalparameterService} from '../../lib/services/globalparameter.service';
import {AppHelper} from '../../lib/helper/app.helper';
import {DynamicFieldHelper} from '../../lib/helper/dynamic.field.helper';
import {TranslateHelper} from '../../lib/helper/translate.helper';
import {AppHelpIds} from '../../shared/help/help.ids';
import {DataType} from '../../lib/dynamic-form/models/data.type';
import {DialogModule} from 'primeng/dialog';
import {DynamicFormModule} from '../../lib/dynamic-form/dynamic-form.module';
import {FieldsetModule} from 'primeng/fieldset';
import {TableModule} from 'primeng/table';
import moment from 'moment';

/**
 * Test dialog for verifying a generic connector endpoint configuration. Lets the user enter a ticker (or currency
 * pair) and optional date range, then sends a test request to the backend and displays the HTTP response, parsed
 * data rows, and any errors. The dialog stays open after submit so the user can adjust parameters and re-test.
 */
@Component({
  selector: 'generic-connector-test-dialog',
  template: `
    <p-dialog header="{{'TEST_ENDPOINT' | translate}}" [visible]="visibleDialog"
              [style]="{width: '900px'}"
              (onShow)="onShow($event)" (onHide)="onHide($event)" [modal]="true">
      <dynamic-form [config]="config" [formConfig]="formConfig" [translateService]="translateService"
                    #form="dynamicForm" (submitBt)="submit($event)">
      </dynamic-form>

      @if (testResult) {
        <p-fieldset legend="{{'TEST_RESULT' | translate}}" class="mt-3">
          @for (item of summaryItems; track item.key) {
            <div class="mb-1">
              <strong>{{ item.label }}:</strong> {{ item.value }}
            </div>
          }
        </p-fieldset>

        @if (testResult.parsedRows?.length > 0) {
          <p-fieldset legend="{{'PARSED_DATA' | translate}}" class="mt-3">
            <div style="max-height: 300px; overflow-y: auto;">
              <p-table [value]="testResult.parsedRows" [scrollable]="true" styleClass="p-datatable-sm">
                <ng-template #header>
                  <tr>
                    @for (col of parsedColumns; track col) {
                      <th>{{ col }}</th>
                    }
                  </tr>
                </ng-template>
                <ng-template #body let-row>
                  <tr>
                    @for (col of parsedColumns; track col) {
                      <td>{{ row[col] }}</td>
                    }
                  </tr>
                </ng-template>
              </p-table>
            </div>
          </p-fieldset>
        }

        @if (testResult.rawResponseSnippet) {
          <p-fieldset legend="{{'RAW_RESPONSE' | translate}}" [toggleable]="true" [collapsed]="true" class="mt-3">
            <pre style="max-height: 200px; overflow-y: auto; white-space: pre-wrap; word-break: break-all;">{{ testResult.rawResponseSnippet }}</pre>
          </p-fieldset>
        }
      }
    </p-dialog>
  `,
  standalone: true,
  imports: [CommonModule, TranslateModule, DialogModule, DynamicFormModule, FieldsetModule, TableModule]
})
export class GenericConnectorTestDialogComponent extends SimpleEditBase implements OnInit {
  @Input() endpoint: GenericConnectorEndpoint;
  @Input() connectorDef: GenericConnectorDef;

  testResult: GenericConnectorTestResult;
  parsedColumns: string[] = [];
  summaryItems: { key: string; label: string; value: any }[] = [];

  private summaryTranslations: { [key: string]: string } = {};

  constructor(public translateService: TranslateService,
              gps: GlobalparameterService,
              private genericConnectorDefService: GenericConnectorDefService) {
    super(AppHelpIds.HELP_BASEDATA_GENERIC_CONNECTOR, gps);
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
    this.translateService.get(keys).subscribe(t => this.summaryTranslations = t);
  }

  protected override initialize(): void {
    this.testResult = null;
    this.parsedColumns = [];
    this.summaryItems = [];
    this.applyFieldVisibility();
  }

  private applyFieldVisibility(): void {
    const isCurrencyPairStrategy = this.endpoint.tickerBuildStrategy === 'CURRENCY_PAIR'
      && this.endpoint.instrumentType === 'CURRENCY';
    const isHistory = this.endpoint.feedSupport === 'FS_HISTORY';

    this.configObject.ticker.invisible = isCurrencyPairStrategy;
    this.configObject.fromCurrency.invisible = !isCurrencyPairStrategy;
    this.configObject.toCurrency.invisible = !isCurrencyPairStrategy;
    this.configObject.fromDate.invisible = !isHistory;
    this.configObject.toDate.invisible = !isHistory;
  }

  submit(value: { [key: string]: any }): void {
    const request: GenericConnectorTestRequest = {
      idGenericConnector: this.connectorDef.idGenericConnector,
      feedSupport: this.endpoint.feedSupport,
      instrumentType: this.endpoint.instrumentType
    };

    if (this.endpoint.tickerBuildStrategy === 'CURRENCY_PAIR' && this.endpoint.instrumentType === 'CURRENCY') {
      request.fromCurrency = value.fromCurrency;
      request.toCurrency = value.toCurrency;
    } else {
      request.ticker = value.ticker;
    }

    if (this.endpoint.feedSupport === 'FS_HISTORY') {
      if (value.fromDate) {
        request.fromDate = moment(value.fromDate).format('YYYY-MM-DD');
      }
      if (value.toDate) {
        request.toDate = moment(value.toDate).format('YYYY-MM-DD');
      }
    }

    this.genericConnectorDefService.testEndpoint(request).subscribe({
      next: result => {
        this.testResult = result;
        this.buildSummaryItems(result);
        if (result.parsedRows?.length > 0) {
          this.parsedColumns = Object.keys(result.parsedRows[0]);
        } else {
          this.parsedColumns = [];
        }
        this.configObject.submit.disabled = false;
      },
      error: () => {
        this.configObject.submit.disabled = false;
      }
    });
  }

  private buildSummaryItems(result: GenericConnectorTestResult): void {
    const t = this.summaryTranslations;
    this.summaryItems = [
      {key: 'success', label: t['SUCCESS'] || 'Success', value: result.success},
      {key: 'httpStatus', label: t['HTTP_STATUS'] || 'HTTP status', value: result.httpStatus},
      {key: 'requestUrl', label: t['REQUEST_URL'] || 'Request URL', value: result.requestUrl},
      {key: 'executionTimeMs', label: t['EXECUTION_TIME_MS'] || 'Execution time (ms)', value: result.executionTimeMs},
    ];
    if (result.errorMessage) {
      this.summaryItems.push(
        {key: 'errorMessage', label: t['ERROR_MESSAGE'] || 'Error message', value: result.errorMessage}
      );
    }
  }
}
