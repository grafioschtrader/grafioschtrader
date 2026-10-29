import { requireFormControl } from '../../lib/dynamic-form/models/form-control-access';
import { Directive, inject } from '@angular/core';
import { SimpleEntityEditBase } from '../../lib/edit/simple.entity.edit.base';
import { ServiceEntityUpdate } from '../../lib/edit/service.entity.update';
import { AssetclassType } from '../../shared/types/assetclass.type';
import { SpecialInvestmentInstruments } from '../../shared/types/special.investment.instruments';
import { forkJoin } from 'rxjs';
import { ValueKeyHtmlSelectOptions } from '../../lib/dynamic-form/models/value.key.html.select.options';
import { InputType } from '../../lib/dynamic-form/models/input.type';
import { GlobalparameterGTService } from '../../gtservice/globalparameter.gt.service';

/**
 * Abstract base component for editing entities with asset class type and special investment instrument selection.
 * Provides common functionality for components that need to filter or configure fields based on asset class
 * and instrument type relationships. Handles dynamic updates of special instrument options when asset class
 * selections change, ensuring only valid instrument combinations are available.
 */
@Directive()
export abstract class AssetClassTypeSpecInstrument<T> extends SimpleEntityEditBase<T> {
  private gpsGT: GlobalparameterGTService = inject(GlobalparameterGTService);

  /** Map of asset class types to their valid special investment instruments */
  protected assetclassSpezInstMap: { [key in AssetclassType]: SpecialInvestmentInstruments[] };

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
   * @param fieldSpecialInvestmentInstrument - Form field name for the special instrument selector
   * @param helpId - Help context identifier for the help system
   * @param i18nRecord - Translation key for the entity record name
   * @param serviceEntityUpdate - Service for entity update operations
   * @protected
   */
  protected constructor(
    private fieldCategoryType: string,
    private fieldSpecialInvestmentInstrument: string,
    helpId: string,
    i18nRecord: string,
    serviceEntityUpdate: ServiceEntityUpdate<T>
  ) {
    super(helpId, i18nRecord, serviceEntityUpdate);
  }

  /**
   * Sets up listener for category type changes to dynamically update special instrument options.
   * Handles both single-select and multi-select category type inputs, filtering instrument options
   * to show only those valid for the selected asset class types.
   */
  valueChangedOnCategoryType(): void {
    requireFormControl(this.configObject[this.fieldCategoryType])
      .valueChanges.pipe(this.untilDialogClosed())
      .subscribe((categoryType) => {
        if (this.configObject[this.fieldCategoryType].inputType === InputType.MultiSelect) {
          const vkh: ValueKeyHtmlSelectOptions[] = [];
          if (categoryType && categoryType.length > 0) {
            categoryType.forEach((ct: string | number) => {
              vkh.push(
                ...this.valueKeyHtmlOptionsSpecInvest
                  .filter((v) =>
                    this.assetclassSpezInstMap[ct as AssetclassType].includes(v.key as SpecialInvestmentInstruments)
                  )
                  .filter((va) => vkh.indexOf(va) < 0)
              );
            });
          }
          this.configObject[this.fieldSpecialInvestmentInstrument].valueKeyHtmlOptions = vkh;
          vkh.length === 0 && requireFormControl(this.configObject[this.fieldSpecialInvestmentInstrument]).setValue('');
        } else {
          if (this.canChangeValues() && categoryType && categoryType.length > 0) {
            this.configObject[this.fieldSpecialInvestmentInstrument].valueKeyHtmlOptions =
              this.valueKeyHtmlOptionsSpecInvest.filter((v) =>
                this.assetclassSpezInstMap[categoryType as AssetclassType].includes(
                  v.key as SpecialInvestmentInstruments
                )
              );
            requireFormControl(this.configObject[this.fieldSpecialInvestmentInstrument]).setValue(
              this.configObject[this.fieldSpecialInvestmentInstrument].valueKeyHtmlOptions[0].key
            );
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
    const categoryKeys = Object.values(AssetclassType).filter(
      (value): value is string => typeof value === 'string' && !['CURRENCY_CASH', 'CURRENCY_FOREIGN'].includes(value)
    );
    const instrumentKeys = Object.values(SpecialInvestmentInstruments).filter(
      (value): value is string => typeof value === 'string'
    );
    forkJoin({
      mapping: this.gpsGT.getPossibleAssetclassInstrumentMap(),
      texts: this.translateService.get([...categoryKeys, ...instrumentKeys])
    })
      .pipe(this.untilDialogClosed())
      .subscribe(({ mapping, texts }) => {
        this.assetclassSpezInstMap = mapping;
        this.form().setDefaultValuesAndEnableSubmit();
        const options = (keys: string[]) =>
          keys
            .map((key) => new ValueKeyHtmlSelectOptions(key, texts[key]))
            .sort((a, b) => a.value.localeCompare(b.value));
        this.configObject[this.fieldCategoryType].valueKeyHtmlOptions = options(categoryKeys);
        this.valueKeyHtmlOptionsSpecInvest = options(instrumentKeys);
        this.configObject[this.fieldSpecialInvestmentInstrument].valueKeyHtmlOptions =
          this.valueKeyHtmlOptionsSpecInvest;
        this.valueChangedOnCategoryType();
        this.initializeOthers();
      });
  }
}
