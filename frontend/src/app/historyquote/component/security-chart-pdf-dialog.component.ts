import { Component, inject, OnInit } from '@angular/core';
import { InfoLevelType } from '../../lib/message/info.leve.type';
import { SignalDynamicFormComponent } from '../../lib/dynamic-form/signal/signal-dynamic-form.component';
import { ValueKeyHtmlSelectOptions } from '../../lib/dynamic-form/models/value.key.html.select.options';
import { SimpleDynamicFormBase } from '../../lib/edit/simple.dynamic.form.base';
import saveAs from '../../lib/filesaver/filesaver';
import { HelpIds } from '../../lib/help/help.ids';
import { AppHelper } from '../../lib/helper/app.helper';
import { DynamicFieldHelper } from '../../lib/helper/dynamic.field.helper';
import { TranslateHelper } from '../../lib/helper/translate.helper';
import { performanceReportFilename } from '../../performanceperiod/model/performance-report';
import { SecurityService } from '../../securitycurrency/service/security.service';
import { ChartPdfExport, ChartPdfScope, SecurityChartPdfOptions } from '../model/security-chart-pdf';

/** What the opener hands over: the choices of the server, the scope of the chart and the way to picture the chart. */
export interface SecurityChartPdfDialogData {
  options: SecurityChartPdfOptions;
  scope: ChartPdfScope;
  /**
   * Pictures the chart as it is displayed and numbers its transaction markers. The dialog calls it when the user
   * confirms, so the picture shows the chart at that moment and a dialog that is cancelled costs nothing.
   *
   * @param includeTransactions whether the markers are numbered for a table of transactions
   */
  exportChart: (includeTransactions: boolean) => Promise<ChartPdfExport>;
}

/** The fields of the dialog. */
interface ChartPdfModel {
  paper: string | null;
  language: string | null;
  numberFormat: string | null;
  includeTransactions: boolean | null;
}

/**
 * Dynamic dialog in which the user decides how the PDF of the displayed time-series chart is made: paper format,
 * language and number format of the texts, and whether the transactions the chart marks follow as a numbered table.
 * Confirming pictures the chart, sends it to the server and saves the answer; the dialog closes when the file has
 * been handed to the browser, and stays open with the same entries when something fails.
 */
@Component({
  template: `
    <signal-dynamic-form
      [config]="config"
      [formConfig]="formConfig"
      [values]="startValues"
      #form="signalDynamicForm"
      (submitBt)="submit($event)" />
  `,
  imports: [SignalDynamicFormComponent]
})
export class SecurityChartPdfDialogComponent
  extends SimpleDynamicFormBase<SignalDynamicFormComponent<ChartPdfModel>>
  implements OnInit
{
  private readonly securityService = inject(SecurityService);
  private readonly data: SecurityChartPdfDialogData = this.dynamicDialogConfig.data;

  readonly startValues: Partial<ChartPdfModel> = this.initialValues();

  constructor() {
    super(HelpIds.HELP_WATCHLIST_HISTORYQUOTES_CHART);
  }

  ngOnInit(): void {
    this.formConfig = AppHelper.getDefaultFormConfig(this.gps, 5, this.helpLink.bind(this));
    this.config = [
      DynamicFieldHelper.createFieldSelectString('paper', 'CHART_PDF_PAPER', true),
      DynamicFieldHelper.createFieldSelectString('language', 'REPORT_LANGUAGE', true),
      DynamicFieldHelper.createFieldSelectString('numberFormat', 'REPORT_NUMBER_FORMAT', true),
      DynamicFieldHelper.createFieldCheckbox('includeTransactions', 'CHART_PDF_INCLUDE_TRANSACTIONS'),
      DynamicFieldHelper.createSubmitButton('CREATE_PDF')
    ];
    this.configObject = TranslateHelper.prepareFieldsAndErrors(this.translateService, this.config, false);
    this.configObject['paper'].valueKeyHtmlOptions = this.data.options.papers;
    this.configObject['language'].valueKeyHtmlOptions = this.data.options.languages;
    this.configObject['numberFormat'].valueKeyHtmlOptions = this.data.options.numberFormats;
  }

  submit(value: { [name: string]: unknown }): void {
    const paper = value['paper'];
    const language = value['language'];
    const numberFormat = value['numberFormat'];
    if (typeof paper !== 'string' || typeof language !== 'string' || typeof numberFormat !== 'string') {
      this.form().enableSubmit();
      return;
    }
    const includeTransactions = value['includeTransactions'] === true;
    this.data.exportChart(includeTransactions).then(
      (chart) => this.download(chart, paper, language, numberFormat, includeTransactions),
      () => {
        this.messageToastService.showMessageI18n(InfoLevelType.ERROR, 'CHART_PDF_EXPORT_FAILED');
        this.form().enableSubmit();
      }
    );
  }

  private download(
    chart: ChartPdfExport,
    paper: string,
    language: string,
    numberFormat: string,
    includeTransactions: boolean
  ): void {
    this.securityService
      .downloadChartPdf({
        ...this.data.scope,
        svg: chart.svg,
        paper,
        language,
        numberFormat,
        includeTransactions,
        transactionNumbers: chart.transactionNumbers
      })
      .pipe(this.untilDialogClosed())
      .subscribe({
        next: (response) => {
          if (response.body == null) {
            this.form().enableSubmit();
            return;
          }
          saveAs(response.body, performanceReportFilename(response.headers.get('Content-Disposition'), 'chart.pdf'));
          this.dynamicDialogRef.close();
        },
        error: () => this.form().enableSubmit()
      });
  }

  /**
   * The language and the formats of the user, and the paper that goes with the number format: North America prints
   * on letter paper, everywhere else A4 is used. Anything the server does not offer is left out.
   */
  private initialValues(): Partial<ChartPdfModel> {
    const options: SecurityChartPdfOptions = this.dynamicDialogConfig.data.options;
    const numberFormat =
      this.keyOf(options.numberFormats, (key) => key.toLowerCase() === this.gps.getLocale().toLowerCase()) ??
      this.keyOf(options.numberFormats, (key) => key.startsWith(this.gps.getUserLang())) ??
      options.numberFormats[0]?.key ??
      null;
    const paper = numberFormat === 'en-US' ? 'LETTER' : 'A4';
    return {
      paper: this.keyOf(options.papers, (key) => key === paper) ?? String(options.papers[0]?.key ?? ''),
      language: this.keyOf(options.languages, (key) => key === this.gps.getUserLang()) ?? null,
      numberFormat: numberFormat == null ? null : String(numberFormat),
      includeTransactions: true
    };
  }

  private keyOf(options: ValueKeyHtmlSelectOptions[], matches: (key: string) => boolean): string | null {
    const found = options.find((option) => matches(String(option.key)));
    return found == null ? null : String(found.key);
  }
}
