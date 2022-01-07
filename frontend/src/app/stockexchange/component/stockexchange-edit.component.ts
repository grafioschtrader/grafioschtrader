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
import {SecurityService} from '../../securitycurrency/service/security.service';
import {SecuritycurrencySearch} from '../../entities/search/securitycurrency.search';
import {AssetclassType} from '../../shared/types/assetclass.type';
import {SpecialInvestmentInstruments} from '../../shared/types/special.investment.instruments';
import {combineLatest} from 'rxjs';
import {Observable} from 'rxjs/internal/Observable';
import {Security} from '../../entities/security';
import {SelectOptionsHelper} from '../../shared/helper/select.options.helper';
import * as moment from 'moment';
import {AppSettings} from '../../shared/app.settings';

/**
 * Edit stockexchnage
 */
@Component({
  selector: 'stockexchange-edit',
  template: `
    <p-dialog header="{{i18nRecord | translate}}" [(visible)]="visibleDialog"
              [responsive]="true" [style]="{width: '500px'}"
              (onShow)="onShow($event)" (onHide)="onHide($event)" [modal]="true">

      <dynamic-form [config]="config" [formConfig]="formConfig" [translateService]="translateService"
                    #form="dynamicForm"
                    (submitBt)="submit($event)">
      </dynamic-form>
    </p-dialog>`
})
export class StockexchangeEditComponent extends SimpleEntityEditBase<Stockexchange> implements OnInit {

  @Input() callParam: StockexchangeCallParam;
  @Input() proposeChangeEntityWithEntity: ProposeChangeEntityWithEntity;

  constructor(translateService: TranslateService,
              gps: GlobalparameterService,
              messageToastService: MessageToastService,
              stockexchangeService: StockexchangeService,
              private securityService: SecurityService) {
    super(HelpIds.HELP_BASEDATA_STOCKEXCHANGE, AppSettings.STOCKEXCHANGE.toUpperCase(), translateService, gps,
      messageToastService, stockexchangeService);
  }

  ngOnInit(): void {
    this.formConfig = AppHelper.getDefaultFormConfig(this.gps,
      4, this.helpLink.bind(this));

    this.config = [
      DynamicFieldHelper.createFieldInputStringHeqF('name', 32, true, {minLength: 2}),
      DynamicFieldHelper.createFieldSelectStringHeqF('countryCode', true),
      DynamicFieldHelper.createFieldInputStringHeqF('symbol', 8, true, {minLength: 3}),
      DynamicFieldHelper.createFieldCheckboxHeqF('secondaryMarket', {defaultValue: true}),
      DynamicFieldHelper.createFieldCheckboxHeqF('noMarketValue'),
      DynamicFieldHelper.createFieldDAInputStringHeqF(DataType.TimeString, 'timeOpen', 8, true),
      DynamicFieldHelper.createFieldDAInputStringHeqF(DataType.TimeString, 'timeClose', 8, true),
      DynamicFieldHelper.createFieldSelectStringHeqF('timeZone', true),
      DynamicFieldHelper.createFieldSelectNumberHeqF('idIndexUpdCalendar', false),

      ...AuditHelper.getFullNoteRequestInputDefinition(this.closeDialog, this)
    ];
    this.configObject = TranslateHelper.prepareFieldsAndErrors(this.translateService, this.config);
  }


  protected initialize(): void {
    const obserables: Observable<any>[] = [this.gps.getTimezones()];
    if (this.callParam.stockexchange) {
      obserables.push(this.getSecurityObservable());
    }

    combineLatest(obserables).subscribe(data => {
      this.configObject.timeZone.valueKeyHtmlOptions = data[0];
      this.configObject.countryCode.valueKeyHtmlOptions = this.callParam.countriesAsHtmlOptions;
      this.form.setDefaultValuesAndEnableSubmit();
      AuditHelper.transferToFormAndChangeButtonForProposaleEdit(this.translateService, this.gps,
        this.callParam.stockexchange, this.form, this.configObject, this.proposeChangeEntityWithEntity);
      FormHelper.disableEnableFieldConfigs(this.callParam.hasSecurity, [this.configObject.noMarketValue,
        this.configObject.countryCode, this.configObject.timeZone]);
      if (data.length > 1) {
        this.configObject.idIndexUpdCalendar.valueKeyHtmlOptions = SelectOptionsHelper.createValueKeyHtmlSelectOptions(
          'idSecuritycurrency', 'name', data[1], true);
      }
      this.configObject.name.elementRef.nativeElement.focus();
    });
  }

  protected getNewOrExistingInstanceBeforeSave(value: { [name: string]: any }): Stockexchange {
    const stockexchange: Stockexchange = new Stockexchange();
    this.copyFormToPublicBusinessObject(stockexchange, this.callParam.stockexchange, this.proposeChangeEntityWithEntity);
    return stockexchange;
  }

  private getSecurityObservable(): Observable<Security[]> {
    const securitycurrencySearch = new SecuritycurrencySearch();
    securitycurrencySearch.assetclassType = AssetclassType[AssetclassType.EQUITIES];
    securitycurrencySearch.specialInvestmentInstruments = SpecialInvestmentInstruments[SpecialInvestmentInstruments.NON_INVESTABLE_INDICES];
    securitycurrencySearch.activeDate = moment().format(AppSettings.FORMAT_DATE_SHORT_US);
    securitycurrencySearch.stockexchangeCounrtyCode = this.callParam.stockexchange.countryCode;
    return this.securityService.searchByCriteria(securitycurrencySearch);
  }

}
