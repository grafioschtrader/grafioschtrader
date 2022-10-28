import {Component, Input, OnInit} from '@angular/core';
import {GlobalparameterService} from '../../shared/service/globalparameter.service';
import {PortfolioService} from '../service/portfolio.service';
import {TranslateService} from '@ngx-translate/core';
import {AppHelper} from '../../shared/helper/app.helper';
import {Portfolio} from '../../entities/portfolio';
import {CallParam} from '../../shared/maintree/types/dialog.visible';
import {MessageToastService} from '../../shared/message/message.toast.service';
import {HelpIds} from '../../shared/help/help.ids';
import {Tenant} from '../../entities/tenant';
import {SimpleEntityEditBase} from '../../shared/edit/simple.entity.edit.base';
import {DynamicFieldHelper} from '../../shared/helper/dynamic.field.helper';
import {SelectOptionsHelper} from '../../shared/helper/select.options.helper';
import {TranslateHelper} from '../../shared/helper/translate.helper';
import {AppSettings} from '../../shared/app.settings';

/**
 * Component for editing the portfolio.
 */
@Component({
  selector: 'portfolio-edit',
  template: `
    <p-dialog header="{{i18nRecord | translate}}" [(visible)]="visibleDialog"
              [responsive]="true" [style]="{width: '400px'}"
              (onShow)="onShow($event)" (onHide)="onHide($event)" [modal]="true">

      <dynamic-form [config]="config" [formConfig]="formConfig" [translateService]="translateService" #form="dynamicForm"
                    (submitBt)="submit($event)">
      </dynamic-form>
    </p-dialog>`
})
export class PortfolioEditComponent extends SimpleEntityEditBase<Portfolio> implements OnInit {

  @Input() callParam: CallParam;

  constructor(translateService: TranslateService,
              gps: GlobalparameterService,
              messageToastService: MessageToastService,
              portfolioService: PortfolioService) {
    super(HelpIds.HELP_PORTFOLIO, AppSettings.PORTFOLIO.toUpperCase(), translateService, gps, messageToastService, portfolioService);
  }

  ngOnInit(): void {
    this.formConfig = AppHelper.getDefaultFormConfig(this.gps,
      6, this.helpLink.bind(this));

    this.config = [
      DynamicFieldHelper.createFieldInputString('name', 'PORTFOLIO_NAME', 25, true),
      DynamicFieldHelper.createFieldSelectStringHeqF('currency', true),
      DynamicFieldHelper.createSubmitButton()
    ];
    this.configObject = TranslateHelper.prepareFieldsAndErrors(this.translateService, this.config);
  }

  protected override initialize(): void {
    this.gps.getCurrencies().subscribe(data => {
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
