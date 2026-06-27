import {Component, Input, OnInit} from '@angular/core';
import {GlobalparameterService} from '../../lib/services/globalparameter.service';
import {TranslateModule, TranslateService} from '@ngx-translate/core';
import {AppHelper} from '../../lib/helper/app.helper';
import {CashaccountService} from '../service/cashaccount.service';
import {CallParam} from '../../shared/maintree/types/dialog.visible';
import {Cashaccount} from '../../entities/cashaccount';
import {Portfolio} from '../../entities/portfolio';
import {MessageToastService} from '../../lib/message/message.toast.service';
import {PortfolioService} from '../../portfolio/service/portfolio.service';
import {HelpIds} from '../../lib/help/help.ids';
import {SimpleEntityEditBase} from '../../lib/edit/simple.entity.edit.base';
import {DynamicFieldModelHelper} from '../../lib/helper/dynamic.field.model.helper';
import {FieldConfig} from '../../lib/dynamic-form/models/field.config';
import {SelectOptionsHelper} from '../../lib/helper/select.options.helper';
import {TranslateHelper} from '../../lib/helper/translate.helper';
import {AppSettings} from '../../shared/app.settings';
import {GlobalparameterGTService} from '../../gtservice/globalparameter.gt.service';
import {DialogModule} from 'primeng/dialog';
import {DynamicFormModule} from '../../lib/dynamic-form/dynamic-form.module';

/**
 * Edit a cash account the currency of a cash account can only be changed when there is no transaction for it.
 */
@Component({
  selector: 'cashaccount-edit',
  template: `
    <p-dialog header="{{i18nRecord | translate}}" [visible]="visibleDialog"
              [style]="{width: '400px', minWidth: '350px', minHeight:'180px' }"
              (onShow)="onShow($event)" (onHide)="onHide($event)" [modal]="true">

      <dynamic-form [config]="config" [formConfig]="formConfig" [translateService]="translateService"
                    #form="dynamicForm"
                    (submitBt)="submit($event)">
      </dynamic-form>
    </p-dialog>`,
  standalone: true,
  imports: [DialogModule, DynamicFormModule, TranslateModule]
})
export class CashaccountEditComponent extends SimpleEntityEditBase<Cashaccount> implements OnInit {

  @Input() callParam: CallParam;

  portfolio: Portfolio;

  constructor(private portfolioService: PortfolioService,
    private gpsGT: GlobalparameterGTService,
    translateService: TranslateService,
    gps: GlobalparameterService,
    messageToastService: MessageToastService,
    cashaccountSercice: CashaccountService) {
    super(HelpIds.HELP_PORTFOLIO_ACCOUNT, AppSettings.CASHACCOUNT.toUpperCase(), translateService, gps,
      messageToastService, cashaccountSercice);
  }

  ngOnInit(): void {
    this.formConfig = AppHelper.getDefaultFormConfig(this.gps,
      4, this.helpLink.bind(this));
    // The whole form (fields, order, constraints, labels) is generated from the Cashaccount entity's
    // @DynamicFormField + Bean Validation annotations. The definition is pre-fetched by the opener and
    // passed via callParam, so the form is built synchronously here and edit values transfer reliably.
    this.config = <FieldConfig[]>DynamicFieldModelHelper.createFieldsFromClassDescriptorInputAndShow(
      this.translateService, this.callParam.optParam.formDefinition, '', true);
    this.configObject = TranslateHelper.prepareFieldsAndErrors(this.translateService, this.config);
  }

  protected override initialize(): void {
    this.portfolio = <Portfolio>this.callParam.parentObject;
    // Initialise and transfer synchronously (config is ready from ngOnInit). Doing this inside the async
    // getCurrencies() subscription would let a later form.reset() wipe fields the user already entered
    // before the currency dropdown was populated (e.g. activeToDate).
    this.configObject.currency.inputWidth = 5;
    if (this.callParam.optParam && this.callParam.optParam.hasTransaction) {
      this.configObject.currency.formControl.disable();
    }
    this.prepareSecurityaccountOption();
    this.form.setDefaultValuesAndEnableSubmit();
    if (this.callParam.thisObject != null) {
      this.form.transferBusinessObjectToForm(this.callParam.thisObject);
    }
    this.configObject.name.elementRef.nativeElement.focus();
    // Currency options load asynchronously and only populate the dropdown; they must not reset the form.
    this.gpsGT.getCurrencies().subscribe(data => {
      this.configObject.currency.valueKeyHtmlOptions = data;
    });
  }

  protected override getNewOrExistingInstanceBeforeSave(value: { [name: string]: any }): Cashaccount {
    const cashaccount: Cashaccount = this.copyFormToPrivateBusinessObject(new Cashaccount(), <Cashaccount>this.callParam.thisObject);
    cashaccount.portfolio = this.portfolio;
    return cashaccount;
  }

  private prepareSecurityaccountOption() {
    if (this.portfolio.securityaccountList.length >= 2) {
      AppHelper.enableAndVisibleInput(this.configObject.connectIdSecurityaccount);
      this.configObject.connectIdSecurityaccount.valueKeyHtmlOptions =
        SelectOptionsHelper.createValueKeyHtmlSelectOptionsFromArray('idSecuritycashAccount', 'name',
          this.portfolio.securityaccountList, true);
    } else {
      AppHelper.disableAndHideInput(this.configObject.connectIdSecurityaccount);
    }
  }

}
