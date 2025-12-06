import {AfterViewInit, Component, OnInit} from '@angular/core';
import {GlobalparameterService} from '../../lib/services/globalparameter.service';
import {PortfolioService} from '../service/portfolio.service';
import {TranslateModule, TranslateService} from '@ngx-translate/core';
import {AppHelper} from '../../lib/helper/app.helper';
import {Portfolio} from '../../entities/portfolio';
import {CallParam} from '../../shared/maintree/types/dialog.visible';
import {MessageToastService} from '../../lib/message/message.toast.service';
import {HelpIds} from '../../lib/help/help.ids';
import {Tenant} from '../../entities/tenant';
import {DynamicFieldHelper} from '../../lib/helper/dynamic.field.helper';
import {SelectOptionsHelper} from '../../lib/helper/select.options.helper';
import {TranslateHelper} from '../../lib/helper/translate.helper';
import {GlobalparameterGTService} from '../../gtservice/globalparameter.gt.service';
import {DynamicDialogConfig, DynamicDialogRef} from 'primeng/dynamicdialog';
import {SimpleDynamicEditBase} from '../../lib/edit/simple.dynamic.edit.base';
import {DynamicFormModule} from '../../lib/dynamic-form/dynamic-form.module';

/**
 * Component for editing the portfolio.
 */
@Component({
  template: `
    <dynamic-form [config]="config" [formConfig]="formConfig" [translateService]="translateService" #form="dynamicForm"
                  (submitBt)="submit($event)">
    </dynamic-form>
  `,
  standalone: true,
  imports: [
    DynamicFormModule,
    TranslateModule
  ]
})
export class PortfolioEditDynamicComponent extends SimpleDynamicEditBase<Portfolio> implements OnInit, AfterViewInit {
  callParam: CallParam;

  constructor(private gpsGT: GlobalparameterGTService,
    dynamicDialogConfig: DynamicDialogConfig,
    dynamicDialogRef: DynamicDialogRef,
    translateService: TranslateService,
    gps: GlobalparameterService,
    messageToastService: MessageToastService,
    portfolioService: PortfolioService) {
    super(dynamicDialogConfig, dynamicDialogRef, HelpIds.HELP_PORTFOLIO, translateService, gps, messageToastService,
      portfolioService);
  }

  ngOnInit(): void {
    this.formConfig = AppHelper.getDefaultFormConfig(this.gps,
      6, this.helpLink.bind(this));
    this.callParam = this.dynamicDialogConfig.data.callParam;
    this.config = [
      DynamicFieldHelper.createFieldInputString('name', 'PORTFOLIO_NAME', 25, true),
      DynamicFieldHelper.createFieldSelectStringHeqF('currency', true),
      DynamicFieldHelper.createSubmitButton()
    ];
    this.configObject = TranslateHelper.prepareFieldsAndErrors(this.translateService, this.config);
  }

  ngAfterViewInit(): void {
    this.gpsGT.getCurrencies().subscribe(data => {
        this.configObject.currency.valueKeyHtmlOptions = SelectOptionsHelper.createValueKeyHtmlSelectOptionsFromArray('key',
          'value', data, false);
        this.form.setDefaultValuesAndEnableSubmit();
        if (this.callParam.thisObject != null) {
          this.form.transferBusinessObjectToForm(this.callParam.thisObject);
        } else {
          this.configObject.currency.formControl.setValue((<Tenant>this.callParam.parentObject).currency);
        }
        this.configObject.name.elementRef.nativeElement.focus();
      }
    );
  }

  protected override getNewOrExistingInstanceBeforeSave(value: { [name: string]: any }): Portfolio {
    return this.copyFormToPrivateBusinessObject(new Portfolio(), <Portfolio>this.callParam.thisObject);
  }

}
