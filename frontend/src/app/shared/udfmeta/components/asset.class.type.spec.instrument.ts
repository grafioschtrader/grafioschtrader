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
import {InputType} from '../../../dynamic-form/models/input.type';

export abstract class AssetClassTypeSpecInstrument<T> extends SimpleEntityEditBase<T> {

  protected assetclassSpezInstMap: { [key in AssetclassType]: SpecialInvestmentInstruments[] };
  protected categoryTypeSubscribe: Subscription;
  protected valueKeyHtmlOptionsSpecInvest: ValueKeyHtmlSelectOptions[];

  protected abstract initializeOthers(): void;

  constructor(private fieldCategoryType: string,
    private fieldSpecialInvestmentInstrument: string,
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
    this.categoryTypeSubscribe = this.configObject[this.fieldCategoryType]
      .formControl.valueChanges.subscribe(categoryType => {
        if(this.configObject[this.fieldCategoryType].inputType === InputType.MultiSelect) {
          const vkh: ValueKeyHtmlSelectOptions[] = [];
          if(categoryType && categoryType.length > 0) {
            categoryType.forEach( ct => {
              vkh.push(...this.valueKeyHtmlOptionsSpecInvest.filter(
                v => this.assetclassSpezInstMap[ct].includes(v.key))
                .filter(va => vkh.indexOf(va) < 0));

            });
          }
          this.configObject[this.fieldSpecialInvestmentInstrument].valueKeyHtmlOptions = vkh;
          vkh.length === 0 && this.configObject[this.fieldSpecialInvestmentInstrument].formControl.setValue('');
        } else {
          if (this.canChangeValues() && categoryType && categoryType.length > 0) {
            this.configObject[this.fieldSpecialInvestmentInstrument].valueKeyHtmlOptions = this.valueKeyHtmlOptionsSpecInvest.filter(
              v => this.assetclassSpezInstMap[categoryType].includes(v.key));
            this.configObject[this.fieldSpecialInvestmentInstrument].formControl.setValue(
              this.configObject[this.fieldSpecialInvestmentInstrument].valueKeyHtmlOptions[0].key);
          }
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
      this.configObject[this.fieldCategoryType].valueKeyHtmlOptions = SelectOptionsHelper.createHtmlOptionsFromEnum(this.translateService,
        AssetclassType, [AssetclassType.CURRENCY_CASH, AssetclassType.CURRENCY_FOREIGN], true);
      this.valueKeyHtmlOptionsSpecInvest = SelectOptionsHelper.createHtmlOptionsFromEnum(
        this.translateService, SpecialInvestmentInstruments,  [], true);
      this.configObject[this.fieldSpecialInvestmentInstrument].valueKeyHtmlOptions = this.valueKeyHtmlOptionsSpecInvest;
      this.valueChangedOnCategoryType();
      this.initializeOthers();
    });
  }

  override onHide(event): void {
    this.categoryTypeSubscribe && this.categoryTypeSubscribe.unsubscribe();
    super.onHide(event);
  }

}
