import {Component, Input, OnInit} from '@angular/core';
import {SimpleEditBase} from '../../lib/edit/simple.edit.base';
import {AppHelper} from '../../lib/helper/app.helper';
import {TranslateModule, TranslateService} from '@ngx-translate/core';
import {GlobalparameterService} from '../../lib/services/globalparameter.service';
import {HelpIds} from '../../lib/help/help.ids';
import {DynamicFieldHelper} from '../../lib/helper/dynamic.field.helper';
import {TranslateHelper} from '../../lib/helper/translate.helper';
import {TaxDataService, TaxStatementExportRequest} from '../service/tax-data.service';
import {TenantService} from '../../tenant/service/tenant.service';
import {ProcessedActionData} from '../../lib/types/processed.action.data';
import {ProcessedAction} from '../../lib/types/processed.action';
import {DialogModule} from 'primeng/dialog';
import {DynamicFormModule} from '../../lib/dynamic-form/dynamic-form.module';
import {ValueKeyHtmlSelectOptions} from '../../lib/dynamic-form/models/value.key.html.select.options';

/**
 * Dialog for exporting an eCH-0196 v2.2.0 Swiss electronic tax statement.
 * The user provides institution and client details, then downloads a ZIP file containing XML + PDF.
 */
@Component({
  selector: 'tax-statement-export-dialog',
  template: `
    <p-dialog header="{{'EXPORT_TAX_STATEMENT' | translate}}" [visible]="visibleDialog"
              [style]="{width: '550px'}"
              (onShow)="onShow($event)" (onHide)="onHide($event)" [modal]="true">
      <dynamic-form [config]="config" [formConfig]="formConfig" [translateService]="translateService"
                    #form="dynamicForm" (submitBt)="submit($event)">
      </dynamic-form>
    </p-dialog>
  `,
  standalone: true,
  imports: [DialogModule, DynamicFormModule, TranslateModule]
})
export class TaxStatementExportDialogComponent extends SimpleEditBase implements OnInit {

  @Input() idsSecurityaccount: number[] = [];
  @Input() availableTaxYears: number[] = [];
  @Input() taxExportSettings: TaxStatementExportRequest;

  constructor(public translateService: TranslateService,
              private taxDataService: TaxDataService,
              private tenantService: TenantService,
              gps: GlobalparameterService) {
    super(HelpIds.HELP_TAX_DATA, gps);
  }

  ngOnInit(): void {
    this.formConfig = AppHelper.getDefaultFormConfig(this.gps, 5, this.helpLink.bind(this));
    this.config = [
      DynamicFieldHelper.createFieldSelectStringHeqF('taxYear', true),
      DynamicFieldHelper.createFieldSelectStringHeqF('canton', true),
      DynamicFieldHelper.createFieldInputStringHeqF('institutionName', 60, true),
      DynamicFieldHelper.createFieldInputStringHeqF('institutionLei', 20, false),
      DynamicFieldHelper.createFieldInputStringHeqF('clientNumber', 40, true),
      DynamicFieldHelper.createFieldInputStringHeqF('clientFirstName', 60, false),
      DynamicFieldHelper.createFieldInputStringHeqF('clientLastName', 60, false),
      DynamicFieldHelper.createFieldInputStringHeqF('clientTin', 16, false),
      DynamicFieldHelper.createSubmitButton()
    ];
    this.configObject = TranslateHelper.prepareFieldsAndErrors(this.translateService, this.config);
  }

  protected override initialize(): void {
    this.configObject.taxYear.valueKeyHtmlOptions = this.availableTaxYears
      .map(y => new ValueKeyHtmlSelectOptions(String(y), String(y)));
    this.taxDataService.getCantons().subscribe(cantons => {
      this.configObject.canton.valueKeyHtmlOptions = cantons;
      this.form.setDefaultValuesAndEnableSubmit();
      if (this.taxExportSettings) {
        this.form.transferBusinessObjectToForm({
          ...this.taxExportSettings,
          taxYear: String(this.taxExportSettings.taxYear)
        });
      }
    });
  }

  submit(value: { [name: string]: any }): void {
    const request: TaxStatementExportRequest = {
      taxYear: parseInt(value.taxYear, 10),
      canton: value.canton,
      institutionName: value.institutionName,
      institutionLei: value.institutionLei || undefined,
      clientNumber: value.clientNumber,
      clientFirstName: value.clientFirstName || undefined,
      clientLastName: value.clientLastName || undefined,
      clientTin: value.clientTin || undefined,
      idsSecurityaccount: this.idsSecurityaccount
    };

    this.configObject.submit.disabled = true;
    this.taxDataService.exportEch0196(request).subscribe({
      next: (blob: Blob) => {
        this.tenantService.saveTaxExportSettings(request).subscribe();
        const url = window.URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `tax_statement_${request.taxYear}.zip`;
        a.click();
        window.URL.revokeObjectURL(url);
        this.closeDialog.emit(new ProcessedActionData(ProcessedAction.UPDATED, request));
      },
      error: () => this.configObject.submit.disabled = false
    });
  }
}
