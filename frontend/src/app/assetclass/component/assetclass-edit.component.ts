import {Component, Input, OnInit} from '@angular/core';
import {Assetclass} from '../../entities/assetclass';
import {AppHelper} from '../../shared/helper/app.helper';
import {GlobalparameterService} from '../../shared/service/globalparameter.service';
import {MessageToastService} from '../../shared/message/message.toast.service';
import {AssetclassService} from '../service/assetclass.service';
import {TranslateService} from '@ngx-translate/core';
import {HelpIds} from '../../shared/help/help.ids';
import {AuditHelper} from '../../shared/helper/audit.helper';
import {AssetclassCallParam} from './assetclass.call.param';
import {ProposeChangeEntityWithEntity} from '../../entities/proposechange/propose.change.entity.whit.entity';
import {DynamicFieldHelper} from '../../shared/helper/dynamic.field.helper';
import {TranslateHelper} from '../../shared/helper/translate.helper';
import {FormHelper} from '../../dynamic-form/components/FormHelper';
import {AppSettings} from '../../shared/app.settings';
import {AssetClassTypeSpecInstrument} from '../../shared/udfmeta/components/asset.class.type.spec.instrument';
import {GlobalparameterGTService} from '../../gtservice/globalparameter.gt.service';

/**
 * Edit asset classes in a dialog
 */
@Component({
    selector: 'assetclass-edit',
    template: `
    <p-dialog header="{{i18nRecord | translate}}" [(visible)]="visibleDialog"
              [style]="{width: '500px'}" (onShow)="onShow($event)" (onHide)="onHide($event)"
              [modal]="true">
      <dynamic-form [config]="config" [formConfig]="formConfig" [translateService]="translateService"
                    #form="dynamicForm"
                    (submitBt)="submit($event)">
      </dynamic-form>
    </p-dialog>
  `,
    standalone: false
})
export class AssetclassEditComponent extends AssetClassTypeSpecInstrument<Assetclass> implements OnInit {

  @Input() callParam: AssetclassCallParam;
  @Input() proposeChangeEntityWithEntity: ProposeChangeEntityWithEntity;


  constructor(translateService: TranslateService,
    gpsGT: GlobalparameterGTService,
    gps: GlobalparameterService,
    messageToastService: MessageToastService,
    assetclassService: AssetclassService) {
    super(AppSettings.CATEGORY_TYPE, gpsGT, 'specialInvestmentInstrument', HelpIds.HELP_BASEDATA_ASSETCLASS, AppSettings.ASSETCLASS.toUpperCase(), translateService, gps,
      messageToastService, assetclassService);
  }

  ngOnInit(): void {
    this.formConfig = AppHelper.getDefaultFormConfig(this.gps,
      4, this.helpLink.bind(this));

    this.config = [
      DynamicFieldHelper.createFieldSelectString(AppSettings.CATEGORY_TYPE, AppSettings.ASSETCLASS.toUpperCase(), true),
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

  protected override canChangeValues(): boolean {
    return !this.callParam.assetclass;
  }

  override onHide(event): void {
    this.categoryTypeSubscribe && this.categoryTypeSubscribe.unsubscribe();
    super.onHide(event);
  }

  protected override initializeOthers(): void {
    this.configObject.en.suggestions = this.callParam.subCategorySuggestionsEN;
    AuditHelper.transferToFormAndChangeButtonForProposaleEdit(this.translateService, this.gps,
      this.callParam.assetclass, this.form, this.configObject, this.proposeChangeEntityWithEntity);
    FormHelper.disableEnableFieldConfigs(this.callParam.hasSecurity, [this.configObject.categoryType,
      this.configObject.specialInvestmentInstrument]);
    if (!this.callParam.assetclass) {
      setTimeout(() => this.configObject.categoryType.elementRef.nativeElement.focus());
    }

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

