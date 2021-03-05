import {Component, Input, OnInit} from '@angular/core';
import {Stockexchange} from '../../entities/stockexchange';
import {DataType} from '../../dynamic-form/models/data.type';
import {GlobalparameterService} from '../../shared/service/globalparameter.service';
import {MessageToastService} from '../../shared/message/message.toast.service';
import {TranslateService} from '@ngx-translate/core';
import {StockexchangeService} from '../service/stockexchange.service';
import {AppHelper} from '../../shared/helper/app.helper';
import {HelpIds} from '../../shared/help/help.ids';
import {SimpleEntityEditBase} from '../../shared/edit/simple.entity.edit.base';
import {AuditHelper} from '../../shared/helper/audit.helper';
import {ProposeChangeEntityWithEntity} from '../../entities/proposechange/propose.change.entity.whit.entity';
import {DynamicFieldHelper} from '../../shared/helper/dynamic.field.helper';
import {TranslateHelper} from '../../shared/helper/translate.helper';
import {StockexchangeCallParam} from './stockexchange.call.param';
import {FormHelper} from '../../dynamic-form/components/FormHelper';


@Component({
  selector: 'stockexchange-edit',
  template: `
      <p-dialog header="{{'STOCKEXCHANGE' | translate}}" [(visible)]="visibleDialog"
                [responsive]="true" [style]="{width: '500px'}"
                (onShow)="onShow($event)" (onHide)="onHide($event)" [modal]="true">

          <dynamic-form [config]="config" [formConfig]="formConfig" [translateService]="translateService" #form="dynamicForm"
                        (submit)="submit($event)">
          </dynamic-form>
      </p-dialog>`
})
export class StockexchangeEditComponent extends SimpleEntityEditBase<Stockexchange> implements OnInit {

  @Input() callParam: StockexchangeCallParam;
  @Input() proposeChangeEntityWithEntity: ProposeChangeEntityWithEntity;

  constructor(translateService: TranslateService,
              globalparameterService: GlobalparameterService,
              messageToastService: MessageToastService,
              stockexchangeService: StockexchangeService) {
    super(HelpIds.HELP_BASEDATA_STOCKEXCHANGE, 'STOCKEXCHANGE', translateService, globalparameterService,
      messageToastService, stockexchangeService);
  }


  ngOnInit(): void {
    this.formConfig = AppHelper.getDefaultFormConfig(this.globalparameterService,
      4, this.helpLink.bind(this));

    this.config = [
      DynamicFieldHelper.createFieldInputString('name', 'NAME', 32, true, {minLength: 2}),
      DynamicFieldHelper.createFieldSelectStringHeqF('countryCode', true),
      DynamicFieldHelper.createFieldInputStringHeqF('symbol', 8, true, {minLength: 3}),
      DynamicFieldHelper.createFieldCheckboxHeqF('secondaryMarket', {defaultValue: true}),
      DynamicFieldHelper.createFieldCheckboxHeqF('noMarketValue'),
      DynamicFieldHelper.createFieldDAInputString(DataType.TimeString, 'timeClose', 'STOCKEXCHANGE_CLOSE', 8, true),
      DynamicFieldHelper.createFieldSelectStringHeqF('timeZone', true),
      ...AuditHelper.getFullNoteRequestInputDefinition(this.closeDialog, this)
    ];
    this.configObject = TranslateHelper.prepareFieldsAndErrors(this.translateService, this.config);
  }


  protected initialize(): void {
    this.globalparameterService.getTimezones().subscribe(data => {
      this.configObject.timeZone.valueKeyHtmlOptions = data;
      this.configObject.countryCode.valueKeyHtmlOptions = this.callParam.countriesAsHtmlOptions;
      this.form.setDefaultValuesAndEnableSubmit();
      AuditHelper.transferToFormAndChangeButtonForProposaleEdit(this.translateService, this.globalparameterService,
        this.callParam.stockexchange, this.form, this.configObject, this.proposeChangeEntityWithEntity);
      FormHelper.disableEnableFieldConfigs(this.callParam.hasSecurity, [this.configObject.noMarketValue]);
      this.configObject.name.elementRef.nativeElement.focus();
    });
  }


  protected getNewOrExistingInstanceBeforeSave(value: { [name: string]: any }): Stockexchange {
    const stockexchange: Stockexchange = new Stockexchange();
    this.copyFormToPublicBusinessObject(stockexchange, this.callParam.stockexchange, this.proposeChangeEntityWithEntity);
    return stockexchange;
  }

}
