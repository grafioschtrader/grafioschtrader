import {Component, Input, OnInit} from '@angular/core';
import {AppHelper} from '../../shared/helper/app.helper';
import {SimpleEditBase} from '../../shared/edit/simple.edit.base';
import {HelpIds} from '../../shared/help/help.ids';
import {TranslateService} from '@ngx-translate/core';
import {GlobalparameterService} from '../../shared/service/globalparameter.service';
import {PortfolioService} from '../../portfolio/service/portfolio.service';
import {ImportTransactionPosService} from '../service/import.transaction.pos.service';
import {ProcessedAction} from '../../shared/types/processed.action';
import {ProcessedActionData} from '../../shared/types/processed.action.data';
import {DynamicFieldHelper} from '../../shared/helper/dynamic.field.helper';
import {SelectOptionsHelper} from '../../shared/helper/select.options.helper';
import {TranslateHelper} from '../../shared/helper/translate.helper';
import {CombineTemplateAndImpTransPos} from './combine.template.and.imp.trans.pos';
import {AppSettings} from '../../shared/app.settings';

/**
 * Dialog to assign a cash account to an imported transaction position.
 */
@Component({
  selector: 'securityaccount-import-set-cashaccount',
  template: `
    <p-dialog header="{{'CASHACCOUNT' | translate}}" [(visible)]="visibleDialog"
              [responsive]="true" [style]="{width: '400px'}"
              (onShow)="onShow($event)" (onHide)="onHide($event)" [modal]="true">

      <dynamic-form [config]="config" [formConfig]="formConfig" [translateService]="translateService" #form="dynamicForm"
                    (submitBt)="submit($event)">
      </dynamic-form>
    </p-dialog>`
})
export class SecurityaccountImportSetCashaccountComponent extends SimpleEditBase implements OnInit {
  @Input() idSecuritycashAccount: number;
  @Input() combineTemplateAndImpTransPos: CombineTemplateAndImpTransPos[];

  constructor(private portfolioService: PortfolioService,
              private importTransactionPosService: ImportTransactionPosService,
              public translateService: TranslateService,
              gps: GlobalparameterService) {
    super(HelpIds.HELP_PORTFOLIO_SECURITYACCOUNT_TRANSACTIONIMPORT, gps);
  }

  ngOnInit(): void {
    this.formConfig = AppHelper.getDefaultFormConfig(this.gps,
      4, this.helpLink.bind(this));
    this.config = [
      DynamicFieldHelper.createFieldSelectNumber('idCashaccount', AppSettings.CASHACCOUNT.toUpperCase(), false,
        {dataproperty: 'cashaccount.idSecuritycashAccount'}),
      DynamicFieldHelper.createSubmitButton('ASIGN')
    ];
    this.configObject = TranslateHelper.prepareFieldsAndErrors(this.translateService, this.config);
  }

  submit(value: { [name: string]: any }): void {
    this.importTransactionPosService.setCashAccount(value.idCashaccount, this.combineTemplateAndImpTransPos.map(ct =>
      ct.importTransactionPos.idTransactionPos)).subscribe(rcImportTransactionPosList =>
      this.closeDialog.emit(new ProcessedActionData(ProcessedAction.UPDATED)));
  }

  protected initialize(): void {
    this.portfolioService.getPortfolioByIdSecuritycashaccount(this.idSecuritycashAccount).subscribe(portfolio => {
        this.configObject.idCashaccount.valueKeyHtmlOptions = SelectOptionsHelper.createValueKeyHtmlSelectOptionsFromArray(
          'idSecuritycashAccount', 'name', portfolio.cashaccountList, false);
      }
    );
  }
}
