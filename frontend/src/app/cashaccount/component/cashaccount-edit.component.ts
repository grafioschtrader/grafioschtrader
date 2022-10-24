import {Component, Input, OnInit} from '@angular/core';
import {GlobalparameterService} from '../../shared/service/globalparameter.service';
import {TranslateService} from '@ngx-translate/core';
import {AppHelper} from '../../shared/helper/app.helper';
import {CashaccountService} from '../service/cashaccount.service';
import {CallParam} from '../../shared/maintree/types/dialog.visible';
import {Cashaccount} from '../../entities/cashaccount';
import {Portfolio} from '../../entities/portfolio';
import {MessageToastService} from '../../shared/message/message.toast.service';
import {PortfolioService} from '../../portfolio/service/portfolio.service';
import {HelpIds} from '../../shared/help/help.ids';
import {SimpleEntityEditBase} from '../../shared/edit/simple.entity.edit.base';
import {DynamicFieldHelper} from '../../shared/helper/dynamic.field.helper';
import {SelectOptionsHelper} from '../../shared/helper/select.options.helper';
import {TranslateHelper} from '../../shared/helper/translate.helper';
import {AppSettings} from '../../shared/app.settings';

/**
 * Edit a cash account the currency of a cash account can only be changed when there is no transaction for it.
 */
@Component({
  selector: 'cashaccount-edit',
  template: `
    <p-dialog header="{{i18nRecord | translate}}" [(visible)]="visibleDialog"
              [responsive]="true" [style]="{width: '400px', minWidth: '350px', minHeight:'180px' }"
              (onShow)="onShow($event)" (onHide)="onHide($event)" [modal]="true">

      <dynamic-form [config]="config" [formConfig]="formConfig" [translateService]="translateService"
                    #form="dynamicForm"
                    (submitBt)="submit($event)">
      </dynamic-form>
    </p-dialog>`
})
export class CashaccountEditComponent extends SimpleEntityEditBase<Cashaccount> implements OnInit {

  @Input() callParam: CallParam;

  portfolio: Portfolio;

  constructor(private portfolioService: PortfolioService,
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

    this.config = [
      DynamicFieldHelper.createFieldInputString('name', 'CASHACCOUNT_NAME', 25, true),
      DynamicFieldHelper.createFieldSelectStringHeqF('currency', true,
        {inputWidth: 5, disabled: this.callParam.optParam && this.callParam.optParam.hasTransaction}),
      DynamicFieldHelper.createFieldSelectNumber('connectIdSecurityaccount', 'SECURITYACCOUNT_ASSIGNMENT', false),
      DynamicFieldHelper.createFieldTextareaInputStringHeqF('note', AppSettings.FID_MAX_LETTERS, false),
      DynamicFieldHelper.createSubmitButton()
    ];
    this.configObject = TranslateHelper.prepareFieldsAndErrors(this.translateService, this.config);
  }

  protected override initialize(): void {
    this.portfolio = <Portfolio>this.callParam.parentObject;
    this.gps.getCurrencies().subscribe(data => {
      this.configObject.currency.valueKeyHtmlOptions = data;

      this.prepareSecurityaccountOption();
      this.form.setDefaultValuesAndEnableSubmit();
      if (this.callParam.thisObject != null) {
        this.form.transferBusinessObjectToForm(this.callParam.thisObject);
      }
      this.configObject.name.elementRef.nativeElement.focus();
    });
  }

  protected override getNewOrExistingInstanceBeforeSave(value: { [name: string]: any }): Cashaccount {
    const cashaccount: Cashaccount = this.copyFormToPrivateBusinessObject(new Cashaccount(), <Cashaccount>this.callParam.thisObject);
    cashaccount.portfolio = this.portfolio;
    return cashaccount;
  }

  private prepareSecurityaccountOption() {
    if (this.portfolio.securityaccountList.length >= 2) {
      AppHelper.enableAndVisibleInput(this.configObject.connectIdSecurityaccoun);
      this.configObject.connectIdSecurityaccount.valueKeyHtmlOptions =
        SelectOptionsHelper.createValueKeyHtmlSelectOptionsFromArray('idSecuritycashAccount', 'name',
          this.portfolio.securityaccountList, true);
    } else {
      AppHelper.disableAndHideInput(this.configObject.connectIdSecurityaccount);
    }
  }

}
