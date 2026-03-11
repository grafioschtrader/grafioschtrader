import {AfterViewInit, Component, OnInit} from '@angular/core';
import {DynamicDialogConfig, DynamicDialogRef} from 'primeng/dynamicdialog';
import {TranslateService} from '@ngx-translate/core';
import {GlobalparameterService} from '../../lib/services/globalparameter.service';
import {DynamicFieldHelper} from '../../lib/helper/dynamic.field.helper';
import {TranslateHelper} from '../../lib/helper/translate.helper';
import {SecurityActionService} from '../service/security-action.service';
import {SecurityAction} from '../model/security-action.model';
import {DataType} from '../../lib/dynamic-form/models/data.type';
import {DynamicFormModule} from '../../lib/dynamic-form/dynamic-form.module';
import {ValueKeyHtmlSelectOptions} from '../../lib/dynamic-form/models/value.key.html.select.options';
import {AppHelper} from '../../lib/helper/app.helper';
import {HelpIds} from '../../lib/help/help.ids';
import {MessageToastService} from '../../lib/message/message.toast.service';
import {SimpleDynamicEditBase} from '../../lib/edit/simple.dynamic.edit.base';
import {ProcessedActionData} from '../../lib/types/processed.action.data';
import {ProcessedAction} from '../../lib/types/processed.action';
import {PortfolioService} from '../../portfolio/service/portfolio.service';
import {Portfolio} from '../../entities/portfolio';

/**
 * Dialog for creating a security transfer. Pre-filled with security and source account from context. User selects
 * target securities account, transfer date, and units. Opened programmatically via DialogService.open().
 */
@Component({
  template: `
    <dynamic-form [config]="config" [formConfig]="formConfig" [translateService]="translateService"
                  #form="dynamicForm" (submitBt)="submit($event)">
    </dynamic-form>
  `,
  standalone: true,
  imports: [DynamicFormModule]
})
export class SecurityTransferCreateComponent extends SimpleDynamicEditBase<SecurityAction> implements OnInit, AfterViewInit {

  private idSecurity: number;
  private idSecurityaccountSource: number;
  private units: number;
  private portfolios: Portfolio[] = [];

  constructor(dynamicDialogConfig: DynamicDialogConfig,
              dynamicDialogRef: DynamicDialogRef,
              translateService: TranslateService,
              gps: GlobalparameterService,
              messageToastService: MessageToastService,
              private securityActionService: SecurityActionService,
              private portfolioService: PortfolioService) {
    super(dynamicDialogConfig, dynamicDialogRef, HelpIds.HELP_BASEDATA_SECURITY_ACTION_SECURITY_TRANSFER, translateService, gps,
      messageToastService, securityActionService);
  }

  ngOnInit(): void {
    const data = this.dynamicDialogConfig.data;
    this.idSecurity = data.idSecurity;
    this.idSecurityaccountSource = data.idSecurityaccountSource;
    this.units = data.units;

    this.formConfig = AppHelper.getDefaultFormConfig(this.gps, 4, this.helpLink.bind(this));
    this.config = [
      DynamicFieldHelper.createFieldInputStringHeqF('securityName', 80, false, {disabled: true}),
      DynamicFieldHelper.createFieldInputStringHeqF('sourceAccount', 80, false, {disabled: true}),
      DynamicFieldHelper.createFieldSelectNumberHeqF('idPortfolioTarget', true),
      DynamicFieldHelper.createFieldSelectNumberHeqF('idSecurityaccountTarget', true),
      DynamicFieldHelper.createFieldPcalendarHeqF(DataType.DateString, 'transferDate', true,
        {calendarConfig: {maxDate: new Date(), disabledDays: [0, 6]}}),
      DynamicFieldHelper.createFieldTextareaInputStringHeqF('note', 1024, false),
      DynamicFieldHelper.createSubmitButton()
    ];
    this.configObject = TranslateHelper.prepareFieldsAndErrors(this.translateService, this.config);
  }

  ngAfterViewInit(): void {
    const data = this.dynamicDialogConfig.data;
    this.portfolioService.getPortfoliosForTenantOrderByName().subscribe(portfolios => {
      this.portfolios = portfolios;
      const eligiblePortfolios = portfolios.filter(p => {
        const accounts = p.securityaccountList || [];
        const targetAccounts = accounts.filter(sa => sa.idSecuritycashAccount !== this.idSecurityaccountSource);
        return targetAccounts.length > 0;
      });
      this.configObject.idPortfolioTarget.valueKeyHtmlOptions =
        eligiblePortfolios.map(p => new ValueKeyHtmlSelectOptions(p.idPortfolio, p.name));
      this.setupPortfolioChangeListener();
      this.form.setDefaultValuesAndEnableSubmit();
      if (this.configObject.securityName) {
        this.configObject.securityName.formControl.setValue(data.securityName || '');
      }
      if (this.configObject.sourceAccount) {
        const sourceAccountName = data.sourceAccountName || this.findAccountName(this.idSecurityaccountSource) || '';
        this.configObject.sourceAccount.formControl.setValue(sourceAccountName);
      }
    });
  }

  private setupPortfolioChangeListener(): void {
    this.configObject.idPortfolioTarget.formControl.valueChanges.subscribe(idPortfolio => {
      this.updateSecurityaccountOptions(idPortfolio);
    });
  }

  private findAccountName(idSecurityaccount: number): string | null {
    for (const portfolio of this.portfolios) {
      const account = (portfolio.securityaccountList || []).find(sa => sa.idSecuritycashAccount === +idSecurityaccount);
      if (account) {
        return account.name;
      }
    }
    return null;
  }

  private updateSecurityaccountOptions(idPortfolio: number): void {
    if (!idPortfolio) {
      this.configObject.idSecurityaccountTarget.valueKeyHtmlOptions = [];
      this.configObject.idSecurityaccountTarget.formControl.setValue(null);
      return;
    }
    const portfolio = this.portfolios.find(p => p.idPortfolio === +idPortfolio);
    const accounts = (portfolio?.securityaccountList || [])
      .filter(sa => sa.idSecuritycashAccount !== this.idSecurityaccountSource);
    this.configObject.idSecurityaccountTarget.valueKeyHtmlOptions =
      accounts.map(sa => new ValueKeyHtmlSelectOptions(sa.idSecuritycashAccount, sa.name));
    if (accounts.length === 1) {
      this.configObject.idSecurityaccountTarget.formControl.setValue(accounts[0].idSecuritycashAccount);
    } else {
      this.configObject.idSecurityaccountTarget.formControl.setValue(null);
    }
  }

  /**
   * Overrides base submit to build a SecurityTransfer from form values plus injected context data.
   */
  override submit(value: { [name: string]: any }): void {
    const transfer: any = {
      idSecurity: this.idSecurity,
      idSecurityaccountSource: this.idSecurityaccountSource,
      idSecurityaccountTarget: value.idSecurityaccountTarget,
      transferDate: value.transferDate,
      units: this.units,
      note: value.note
    };
    this.securityActionService.createTransfer(transfer).subscribe({
      next: (result) => this.dynamicDialogRef.close(new ProcessedActionData(ProcessedAction.CREATED, result)),
      error: () => this.configObject.submit.disabled = false
    });
  }

  protected override getNewOrExistingInstanceBeforeSave(value: { [name: string]: any }): SecurityAction {
    // Not used — submit() is overridden
    return null;
  }
}
