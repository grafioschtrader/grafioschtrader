import {Component, Input, OnInit} from '@angular/core';
import {Assetclass} from '../../entities/assetclass';
import {AppHelper} from '../../shared/helper/app.helper';
import {GlobalparameterService} from '../../shared/service/globalparameter.service';
import {MessageToastService} from '../../shared/message/message.toast.service';
import {AssetclassService} from '../service/assetclass.service';
import {TranslateService} from '@ngx-translate/core';
import {AssetclassType} from '../../shared/types/assetclass.type';
import {SpecialInvestmentInstruments} from '../../shared/types/special.investment.instruments';
import {HelpIds} from '../../shared/help/help.ids';
import {SimpleEntityEditBase} from '../../shared/edit/simple.entity.edit.base';
import {AuditHelper} from '../../shared/helper/audit.helper';
import {AssetclassCallParam} from './assetclass.call.param';
import {ProposeChangeEntityWithEntity} from '../../entities/proposechange/propose.change.entity.whit.entity';
import {DynamicFieldHelper} from '../../shared/helper/dynamic.field.helper';
import {SelectOptionsHelper} from '../../shared/helper/select.options.helper';
import {TranslateHelper} from '../../shared/helper/translate.helper';
import {FormHelper} from '../../dynamic-form/components/FormHelper';
import {Subscription} from 'rxjs';
import {ValueKeyHtmlSelectOptions} from '../../dynamic-form/models/value.key.html.select.options';
import {AppSettings} from '../../shared/app.settings';

/**
 * Edit asset classes in a dialog
 */
@Component({
  selector: 'assetclass-edit',
  template: `
    <p-dialog header="{{i18nRecord | translate}}" [(visible)]="visibleDialog"
              [responsive]="true" [style]="{width: '500px'}"
              (onShow)="onShow($event)" (onHide)="onHide($event)" [modal]="true">

      <dynamic-form [config]="config" [formConfig]="formConfig" [translateService]="translateService"
                    #form="dynamicForm"
                    (submitBt)="submit($event)">
      </dynamic-form>
    </p-dialog>
  `
})
export class AssetclassEditComponent extends SimpleEntityEditBase<Assetclass> implements OnInit {

  @Input() callParam: AssetclassCallParam;
  @Input() proposeChangeEntityWithEntity: ProposeChangeEntityWithEntity;

  private assetclassSpezInstMap: { [key in AssetclassType]: SpecialInvestmentInstruments[] };
  private categoryTypeSubscribe: Subscription;
  private valueKeyHtmlOptionsSpecInvest: ValueKeyHtmlSelectOptions[];

  constructor(translateService: TranslateService,
              gps: GlobalparameterService,
              messageToastService: MessageToastService,
              assetclassService: AssetclassService) {
    super(HelpIds.HELP_BASEDATA_ASSETCLASS, AppSettings.ASSETCLASS.toUpperCase(), translateService, gps,
      messageToastService, assetclassService);
  }

  ngOnInit(): void {
    this.formConfig = AppHelper.getDefaultFormConfig(this.gps,
      4, this.helpLink.bind(this));

    this.config = [
      DynamicFieldHelper.createFieldSelectString('categoryType', AppSettings.ASSETCLASS.toUpperCase(), true),
      DynamicFieldHelper.createFieldSuggestionInputString('en', 'SUB_ASSETCLASS', 64, true,
        {dataproperty: 'subCategoryNLS.map.en', labelSuffix: 'EN', suggestionsFN: this.filterSuggestionsEN.bind(this)}),
      DynamicFieldHelper.createFieldSuggestionInputString('de', 'SUB_ASSETCLASS', 64, true,
        {dataproperty: 'subCategoryNLS.map.de', labelSuffix: 'DE', suggestionsFN: this.filterSuggestionsDE.bind(this)}),
      DynamicFieldHelper.createFieldSelectString('specialInvestmentInstrument', 'FINANCIAL_INSTRUMENT', true),
      ...AuditHelper.getFullNoteRequestInputDefinition(this.closeDialog, this)
    ];
    this.configObject = TranslateHelper.prepareFieldsAndErrors(this.translateService, this.config);
  }

  public filterSuggestionsEN(event: any) {
    const query: string = event.query.toLocaleLowerCase();
    this.configObject.en.suggestions = this.callParam.subCategorySuggestionsEN.filter(
      suggestion => suggestion.toLocaleLowerCase().startsWith(query));
  }

  public filterSuggestionsDE(event: any) {
    const query: string = event.query.toLocaleLowerCase();
    this.configObject.de.suggestions = this.callParam.subCategorySuggestionsDE.filter(
      suggestion => suggestion.toLocaleLowerCase().startsWith(query));
  }

  valueChangedOnCategoryType(): void {
    this.categoryTypeSubscribe = this.configObject.categoryType
      .formControl.valueChanges.subscribe(categoryType => {
        if (!this.callParam.assetclass && categoryType && categoryType.length > 0) {
          this.configObject.specialInvestmentInstrument.valueKeyHtmlOptions = this.valueKeyHtmlOptionsSpecInvest.filter(
            v => this.assetclassSpezInstMap[categoryType].includes(v.key));
          this.configObject.specialInvestmentInstrument.formControl.setValue(
            this.configObject.specialInvestmentInstrument.valueKeyHtmlOptions[0].key);
        }
      });
  }

  onHide(event): void {
    this.categoryTypeSubscribe && this.categoryTypeSubscribe.unsubscribe();
    super.onHide(event);
  }

  protected override initialize(): void {
    (<AssetclassService>this.serviceEntityUpdate).getPossibleAssetclassInstrumentMap().subscribe(assetclassSpezInstMap => {
      this.assetclassSpezInstMap = assetclassSpezInstMap;
      this.form.setDefaultValuesAndEnableSubmit();
      this.configObject.categoryType.valueKeyHtmlOptions = SelectOptionsHelper.createHtmlOptionsFromEnum(this.translateService,
        AssetclassType, [AssetclassType.CURRENCY_CASH, AssetclassType.CURRENCY_FOREIGN], true);
      this.valueKeyHtmlOptionsSpecInvest = SelectOptionsHelper.createHtmlOptionsFromEnum(
        this.translateService, SpecialInvestmentInstruments);
      this.configObject.specialInvestmentInstrument.valueKeyHtmlOptions = this.valueKeyHtmlOptionsSpecInvest;
      this.configObject.en.suggestions = this.callParam.subCategorySuggestionsEN;
      AuditHelper.transferToFormAndChangeButtonForProposaleEdit(this.translateService, this.gps,
        this.callParam.assetclass, this.form, this.configObject, this.proposeChangeEntityWithEntity);
      this.valueChangedOnCategoryType();
      FormHelper.disableEnableFieldConfigs(this.callParam.hasSecurity, [this.configObject.categoryType,
        this.configObject.specialInvestmentInstrument]);

      if (!this.callParam.assetclass) {
        setTimeout(() => this.configObject.categoryType.elementRef.nativeElement.focus());
      }
    });
  }

  protected override getNewOrExistingInstanceBeforeSave(value: { [name: string]: any }): Assetclass {
    const assetclass: Assetclass = new Assetclass();
    this.copyFormToPublicBusinessObject(assetclass, this.callParam.assetclass, this.proposeChangeEntityWithEntity);

    const values: any = {};
    this.form.cleanMaskAndTransferValuesToBusinessObject(values, true);
    assetclass.subCategoryNLS.map.de = values.de;
    assetclass.subCategoryNLS.map.en = values.en;
    return assetclass;
  }

}

