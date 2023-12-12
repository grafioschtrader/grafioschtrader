import {SimpleEntityEditBase} from '../../edit/simple.entity.edit.base';
import {TranslateService} from '@ngx-translate/core';
import {GlobalparameterService} from '../../service/globalparameter.service';
import {MessageToastService} from '../../message/message.toast.service';
import {HelpIds} from '../../help/help.ids';
import {ServiceEntityUpdate} from '../../edit/service.entity.update';
import {AssetclassType} from '../../types/assetclass.type';
import {SpecialInvestmentInstruments} from '../../types/special.investment.instruments';
import {Subscription} from 'rxjs';
import {ValueKeyHtmlSelectOptions} from '../../../dynamic-form/models/value.key.html.select.options';
import {SelectOptionsHelper} from '../../helper/select.options.helper';

export abstract class AssetClassTypeSpecInstrument<T> extends SimpleEntityEditBase<T> {

  protected assetclassSpezInstMap: { [key in AssetclassType]: SpecialInvestmentInstruments[] };
  protected categoryTypeSubscribe: Subscription;
  protected valueKeyHtmlOptionsSpecInvest: ValueKeyHtmlSelectOptions[];

  protected abstract initializeOthers(): void;


  constructor(private includeEnumAll: boolean,
    helpId: HelpIds,
    i18nRecord: string,
    translateService: TranslateService,
    gps: GlobalparameterService,
    messageToastService: MessageToastService,
    serviceEntityUpdate: ServiceEntityUpdate<T>) {
    super(helpId, i18nRecord, translateService, gps,
      messageToastService, serviceEntityUpdate);
  }


  valueChangedOnCategoryType(): void {
    this.categoryTypeSubscribe = this.configObject.categoryType
      .formControl.valueChanges.subscribe(categoryType => {
        if (this.canChangeValues() && categoryType && categoryType.length > 0) {
          this.configObject.specialInvestmentInstrument.valueKeyHtmlOptions = this.valueKeyHtmlOptionsSpecInvest.filter(
            v => this.assetclassSpezInstMap[categoryType].includes(v.key));
          this.configObject.specialInvestmentInstrument.formControl.setValue(
            this.configObject.specialInvestmentInstrument.valueKeyHtmlOptions[0].key);
        }
      });
  }

  protected canChangeValues(): boolean {
    return true;
  }

  protected override initialize(): void {
    this.gps.getPossibleAssetclassInstrumentMap().subscribe(assetclassSpezInstMap => {
      this.assetclassSpezInstMap = assetclassSpezInstMap;
      this.form.setDefaultValuesAndEnableSubmit();
      this.configObject.categoryType.valueKeyHtmlOptions = SelectOptionsHelper.createHtmlOptionsFromEnum(this.translateService,
        AssetclassType, [AssetclassType.CURRENCY_CASH, AssetclassType.CURRENCY_FOREIGN].concat(this.includeEnumAll ? [] : [AssetclassType.ALL]), true);
      this.valueKeyHtmlOptionsSpecInvest = SelectOptionsHelper.createHtmlOptionsFromEnum(
        this.translateService, SpecialInvestmentInstruments, this.includeEnumAll ? [] : [SpecialInvestmentInstruments.ALL], true);
      this.configObject.specialInvestmentInstrument.valueKeyHtmlOptions = this.valueKeyHtmlOptionsSpecInvest;
      this.valueChangedOnCategoryType();
      this.initializeOthers();
    });
  }


  override onHide(event): void {
    this.categoryTypeSubscribe && this.categoryTypeSubscribe.unsubscribe();
    super.onHide(event);
  }


}
