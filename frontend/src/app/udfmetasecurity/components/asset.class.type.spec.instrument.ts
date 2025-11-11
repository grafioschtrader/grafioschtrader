import {SimpleEntityEditBase} from '../../lib/edit/simple.entity.edit.base';
import {TranslateService} from '@ngx-translate/core';
import {GlobalparameterService} from '../../lib/services/globalparameter.service';
import {MessageToastService} from '../../lib/message/message.toast.service';
import {ServiceEntityUpdate} from '../../lib/edit/service.entity.update';
import {AssetclassType} from '../../shared/types/assetclass.type';
import {SpecialInvestmentInstruments} from '../../shared/types/special.investment.instruments';
import {Subscription} from 'rxjs';
import {ValueKeyHtmlSelectOptions} from '../../lib/dynamic-form/models/value.key.html.select.options';
import {SelectOptionsHelper} from '../../lib/helper/select.options.helper';
import {InputType} from '../../lib/dynamic-form/models/input.type';
import {GlobalparameterGTService} from '../../gtservice/globalparameter.gt.service';

/**
 * Abstract base component for editing entities with asset class type and special investment instrument selection.
 * Provides common functionality for components that need to filter or configure fields based on asset class
 * and instrument type relationships. Handles dynamic updates of special instrument options when asset class
 * selections change, ensuring only valid instrument combinations are available.
 */
export abstract class AssetClassTypeSpecInstrument<T> extends SimpleEntityEditBase<T> {

  /** Map of asset class types to their valid special investment instruments */
  protected assetclassSpezInstMap: { [key in AssetclassType]: SpecialInvestmentInstruments[] };

  /** Subscription to category type changes for dynamic instrument filtering */
  protected categoryTypeSubscribe: Subscription;

  /** All available special investment instrument options for selection */
  protected valueKeyHtmlOptionsSpecInvest: ValueKeyHtmlSelectOptions[];

  /**
   * Subclass-specific initialization logic called after asset class mapping is loaded.
   * Implement to perform additional setup tasks specific to the component.
   *
   * @protected
   */
  protected abstract initializeOthers(): void;

  /**
   * Creates the asset class type and special instrument base component.
   *
   * @param fieldCategoryType - Form field name for the category type selector
   * @param gpsGT - GT-specific global parameter service for asset class mappings
   * @param fieldSpecialInvestmentInstrument - Form field name for the special instrument selector
   * @param helpId - Help context identifier for the help system
   * @param i18nRecord - Translation key for the entity record name
   * @param translateService - Translation service for i18n support
   * @param gps - Global parameter service providing user settings and system configuration
   * @param messageToastService - Service for displaying user notifications
   * @param serviceEntityUpdate - Service for entity update operations
   * @protected
   */
  protected constructor(private fieldCategoryType: string,
    private gpsGT: GlobalparameterGTService,
    private fieldSpecialInvestmentInstrument: string,
    helpId: string,
    i18nRecord: string,
    translateService: TranslateService,
    gps: GlobalparameterService,
    messageToastService: MessageToastService,
    serviceEntityUpdate: ServiceEntityUpdate<T>) {
    super(helpId, i18nRecord, translateService, gps,
      messageToastService, serviceEntityUpdate);
  }

  /**
   * Sets up listener for category type changes to dynamically update special instrument options.
   * Handles both single-select and multi-select category type inputs, filtering instrument options
   * to show only those valid for the selected asset class types.
   */
  valueChangedOnCategoryType(): void {
    this.categoryTypeSubscribe = this.configObject[this.fieldCategoryType]
      .formControl.valueChanges.subscribe(categoryType => {
        if (this.configObject[this.fieldCategoryType].inputType === InputType.MultiSelect) {
          const vkh: ValueKeyHtmlSelectOptions[] = [];
          if (categoryType && categoryType.length > 0) {
            categoryType.forEach(ct => {
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

  /**
   * Determines if special instrument values can be automatically changed when category type changes.
   * Subclasses can override to prevent automatic value updates in specific scenarios.
   *
   * @returns True if values can be automatically updated, false otherwise
   * @protected
   */
  protected canChangeValues(): boolean {
    return true;
  }

  /**
   * Initializes component by loading asset class to instrument mapping and setting up form options.
   * Retrieves valid instrument combinations, populates select options, and sets up category type change listener.
   *
   * @protected
   */
  protected override initialize(): void {
    this.gpsGT.getPossibleAssetclassInstrumentMap().subscribe(assetclassSpezInstMap => {
      this.assetclassSpezInstMap = assetclassSpezInstMap;
      this.form.setDefaultValuesAndEnableSubmit();
      this.configObject[this.fieldCategoryType].valueKeyHtmlOptions = SelectOptionsHelper.createHtmlOptionsFromEnum(this.translateService,
        AssetclassType, [AssetclassType.CURRENCY_CASH, AssetclassType.CURRENCY_FOREIGN], true);
      this.valueKeyHtmlOptionsSpecInvest = SelectOptionsHelper.createHtmlOptionsFromEnum(
        this.translateService, SpecialInvestmentInstruments, [], true);
      this.configObject[this.fieldSpecialInvestmentInstrument].valueKeyHtmlOptions = this.valueKeyHtmlOptionsSpecInvest;
      this.valueChangedOnCategoryType();
      this.initializeOthers();
    });
  }

  /**
   * Cleanup handler when dialog is closed.
   * Unsubscribes from category type change listener to prevent memory leaks.
   *
   * @param event - Dialog hide event
   */
  override onHide(event): void {
    this.categoryTypeSubscribe && this.categoryTypeSubscribe.unsubscribe();
    super.onHide(event);
  }

}
