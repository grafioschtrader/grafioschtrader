import {SimpleEntityEditBase} from '../../shared/edit/simple.entity.edit.base';
import {Subscription} from 'rxjs';
import {Portfolio} from '../../entities/portfolio';
import {TranslateService} from '@ngx-translate/core';
import {GlobalparameterService} from '../../shared/service/globalparameter.service';
import {MessageToastService} from '../../shared/message/message.toast.service';
import {HelpIds} from '../../shared/help/help.ids';
import {ServiceEntityUpdate} from '../../shared/edit/service.entity.update';
import {ValueKeyHtmlSelectOptions} from '../../dynamic-form/models/value.key.html.select.options';
import {FieldConfig} from '../../dynamic-form/models/field.config';
import {DataType} from '../../dynamic-form/models/data.type';
import {Directive, Input} from '@angular/core';
import {AlgoAssetclassSecurity} from '../model/algo.assetclass.security';
import {AlgoCallParam} from '../model/algo.dialog.visible';
import {DynamicFieldHelper} from '../../shared/helper/dynamic.field.helper';

@Directive()
export abstract class AlgoAssetclassSecurityBaseEdit<T> extends SimpleEntityEditBase<T> {
  @Input() algoCallParam: AlgoCallParam;

  protected securityaccount1ChangedSub: Subscription;
  protected portfolios: Portfolio[];

  constructor(i18nRecord: string,
              translateService: TranslateService,
              gps: GlobalparameterService,
              messageToastService: MessageToastService,
              serviceEntityUpdate: ServiceEntityUpdate<T>) {
    super(HelpIds.HELP_ALGO, i18nRecord, translateService, gps,
      messageToastService, serviceEntityUpdate);
  }

  onHide(event): void {
    this.securityaccount1ChangedSub && this.securityaccount1ChangedSub.unsubscribe();
    super.onHide(event);
  }

  protected getFieldDefinition(): FieldConfig[] {
    return [
      DynamicFieldHelper.createFieldSelectString('idSecurityaccount1', 'ALGO_SECURITYACCOUNT_1', false),
      DynamicFieldHelper.createFieldSelectString('idSecurityaccount2', 'ALGO_SECURITYACCOUNT_2', false),
      DynamicFieldHelper.createFieldMinMaxNumber(DataType.Numeric, 'percentage', 'ALGO_PERCENTAGE', true,
        0.1, 100, {fieldSuffix: '%'}),
      DynamicFieldHelper.createSubmitButton()
    ];
  }

  protected setSecurityaccounts(): void {
    this.configObject.idSecurityaccount1.valueKeyHtmlOptions = this.createPorfolioSecurityaccountHtmlSelectOptions(
      this.portfolios, '-1');
    if (this.algoCallParam.thisObject != null) {
      this.form.transferBusinessObjectToForm(this.algoCallParam.thisObject);
      const securityaccount1: number = (<AlgoAssetclassSecurity>this.algoCallParam.thisObject).idSecurityaccount1;
      this.configObject.idSecurityaccount2.valueKeyHtmlOptions = this.createPorfolioSecurityaccountHtmlSelectOptions(this.portfolios,
        securityaccount1 ? ('' + securityaccount1) : '');
    }
  }

  protected createPorfolioSecurityaccountHtmlSelectOptions(portfolios: Portfolio[],
                                                           idSecurityaccount: string): ValueKeyHtmlSelectOptions[] {
    const valueKeyHtmlSelectOptions: ValueKeyHtmlSelectOptions[] = [];
    valueKeyHtmlSelectOptions.push(new ValueKeyHtmlSelectOptions('', ''));
    if (idSecurityaccount) {
      portfolios.forEach(portfolio => {
        portfolio.securityaccountList.forEach(securityaccount => {
          if (idSecurityaccount !== ('' + securityaccount.idSecuritycashAccount)) {
            valueKeyHtmlSelectOptions.push(new ValueKeyHtmlSelectOptions(securityaccount.idSecuritycashAccount, portfolio.name
              + ' / ' + securityaccount.name));
          }
        });
      });
    }
    return valueKeyHtmlSelectOptions;
  }

  protected valueChangedOnSecurityaccount1(): void {
    this.securityaccount1ChangedSub = this.configObject.idSecurityaccount1.formControl.valueChanges
      .subscribe((idSecurityaccount: string) => {
        this.configObject.idSecurityaccount2.valueKeyHtmlOptions = this.createPorfolioSecurityaccountHtmlSelectOptions(this.portfolios,
          idSecurityaccount);
      });
  }
}
