import { requireFormControl } from '../../lib/dynamic-form/models/form-control-access';
import { switchMap, combineLatest, Observable, of } from 'rxjs';
import { Component, OnInit, inject } from '@angular/core';
import { Validators } from '@angular/forms';
import { AlgoAssetclass } from '../model/algo.assetclass';
import { TranslateModule } from '@ngx-translate/core';
import { AlgoAssetclassService } from '../service/algo.assetclass.service';
import { AppHelper } from '../../lib/helper/app.helper';
import { AssetclassService } from '../../assetclass/service/assetclass.service';
import { Assetclass } from '../../entities/assetclass';
import { ValueKeyHtmlSelectOptions } from '../../lib/dynamic-form/models/value.key.html.select.options';
import { distinctUntilChanged } from 'rxjs/operators';
import { requireAlgoNodeId } from '../model/algo.top.asset.security';
import { FormHelper } from '../../lib/dynamic-form/components/FormHelper';
import { AlgoAssetclassSecurityBaseEdit } from './algo.assetclass.security.base.edit';
import { DynamicFieldModelHelper } from '../../lib/helper/dynamic.field.model.helper';
import { DynamicFieldHelper } from '../../lib/helper/dynamic.field.helper';
import { TranslateHelper } from '../../lib/helper/translate.helper';
import { AppSettings } from '../../shared/app.settings';
import { BusinessSelectOptionsHelper } from '../../shared/securitycurrency/business.select.options.helper';
import { DialogModule } from '@openng/optimus-ui/dialog';
import { DYNAMIC_FORM_IMPORTS } from '../../lib/dynamic-form/dynamic-form.imports';

@Component({
  selector: 'algo-assetclass-edit',
  template: ` <p-dialog
    header="{{ 'ALGO_ASSETCLASS' | translate }}"
    [visible]="visibleDialog()"
    [style]="{ width: '600px' }"
    (onShow)="onShow($event)"
    (onHide)="onHide($event)"
    [modal]="true">
    <dynamic-form
      [config]="config"
      [formConfig]="formConfig"
      [translateService]="translateService"
      #form="dynamicForm"
      (submitBt)="submit($event)" />
  </p-dialog>`,
  imports: [DialogModule, ...DYNAMIC_FORM_IMPORTS, TranslateModule]
})
export class AlgoAssetclassEditComponent extends AlgoAssetclassSecurityBaseEdit<AlgoAssetclass> implements OnInit {
  private assetclassService: AssetclassService = inject(AssetclassService);

  constructor() {
    super('ALGO_ASSETCLASS', inject(AlgoAssetclassService));
  }

  ngOnInit(): void {
    const definition = this.algoCallParam().formDefinition;
    if (!definition) throw new Error('Algo asset-class editing requires its form definition.');
    this.formConfig = AppHelper.getDefaultFormConfig(this.gps, 4, this.helpLink.bind(this));

    this.config = [
      DynamicFieldHelper.createFieldCheckboxHeqF('customCategory'),
      DynamicFieldHelper.createFieldInputStringHeqF('name', 40, false),
      DynamicFieldHelper.createFieldCheckboxHeqF('addInstrumentsBySearch'),
      DynamicFieldHelper.createFieldSelectStringHeqF(AppSettings.ASSETCLASS_KEY, true, {
        dataproperty: 'assetclass.idAssetClass'
      }),
      ...DynamicFieldModelHelper.createConfigFieldsFromDescriptor(
        this.translateService,
        definition.fieldDescriptorInputAndShows,
        '',
        false
      ).map((field) => ({ ...field, labelHelpText: 'REBALANCE_OVERRIDE_HELP' })),
      ...this.getFieldDefinition()
    ];
    this.configObject = TranslateHelper.prepareFieldsAndErrors(this.translateService, this.config);
  }

  protected override initialize(): void {
    const existing = <AlgoAssetclass>this.algoCallParam().thisObject;
    // Detect custom category mode for existing entity
    const isCustom = existing && existing.name != null;
    const accountOptionsObservable: Observable<ValueKeyHtmlSelectOptions[]> = this.getAccountOptions(
      undefined,
      isCustom ? undefined : (existing?.assetclass?.idAssetClass ?? undefined)
    );
    this.valueChangedOnSecurityaccount1();

    // Subscribe to customCategory checkbox changes
    requireFormControl(this.configObject.customCategory)
      .valueChanges.pipe(this.untilDialogClosed())
      .subscribe((checked: boolean) => {
        this.updateCustomCategoryDependencies(checked);
      });

    const assetclassObservable = isCustom ? of([] as Assetclass[]) : this.getAssetclassObserver();

    combineLatest([assetclassObservable, accountOptionsObservable])
      .pipe(this.untilDialogClosed())
      .subscribe((data: [Assetclass | Assetclass[], ValueKeyHtmlSelectOptions[]]) => {
        this.configObject.assetclass.referencedDataObject = Array.isArray(data[0]) ? data[0] : [data[0]];
        this.configObject.assetclass.valueKeyHtmlOptions =
          BusinessSelectOptionsHelper.assetclassCreateValueKeyHtmlSelectOptions(
            this.gps,
            this.translateService,
            this.configObject.assetclass.referencedDataObject
          );
        this.setAccountOptions(data[1]);
        if (existing) {
          this.form().transferBusinessObjectToForm(existing);
          // A saved priority whose account no longer allows the type is cleared, the backend would refuse it.
          this.setAccountOptions(data[1]);
        } else {
          // Creating: the allowed accounts follow the asset class; a custom category allows every account.
          requireFormControl(this.configObject.assetclass)
            .valueChanges.pipe(
              distinctUntilChanged(),
              switchMap((id: number | string) => this.getAccountOptions(undefined, id ? +id : undefined)),
              this.untilDialogClosed()
            )
            .subscribe((options) => this.setAccountOptions(options));
        }

        if (isCustom) {
          requireFormControl(this.configObject.customCategory).setValue(true);
        } else {
          this.updateCustomCategoryDependencies(false);
          this.disableEnableInputForExisting(this.algoCallParam().thisObject != null);
        }
      });
  }

  protected override getNewOrExistingInstanceBeforeSave(value: { [name: string]: any }): AlgoAssetclass {
    const algoAssetclass: AlgoAssetclass = new AlgoAssetclass();
    const algoCallParam = this.algoCallParam();
    if (algoCallParam.thisObject) {
      Object.assign(algoAssetclass, algoCallParam.thisObject);
    }
    this.form().cleanMaskAndTransferValuesToBusinessObject(algoAssetclass);
    // Not an entity field: it only asks the hierarchy view to open the instrument search after the save.
    algoCallParam.addInstrumentsBySearch = !!requireFormControl(this.configObject.addInstrumentsBySearch).value;
    delete (algoAssetclass as AlgoAssetclass & { addInstrumentsBySearch?: boolean }).addInstrumentsBySearch;
    if (requireFormControl(this.configObject.customCategory).value) {
      algoAssetclass.assetclass = null;
    } else {
      algoAssetclass.name = null;
    }
    algoAssetclass.idAlgoAssetclassParent = requireAlgoNodeId(algoCallParam.parentObject);
    return algoAssetclass;
  }

  private updateCustomCategoryDependencies(isCustom: boolean): void {
    if (isCustom) {
      this.enableField('name', true);
      this.enableField('addInstrumentsBySearch', false);
      this.disableAndClearField('assetclass');
    } else {
      this.disableAndClearField('name');
      this.disableAndClearField('addInstrumentsBySearch');
      this.enableField('assetclass', true);
    }
    // Disabling must not re-enter the checkbox's valueChanges subscription.
    if (this.algoCallParam().thisObject != null) {
      requireFormControl(this.configObject.customCategory).disable({ emitEvent: false });
      this.viewChanged();
    }
  }

  private enableField(fieldName: string, required: boolean): void {
    const fc = this.configObject[fieldName];
    requireFormControl(fc).enable();
    DynamicFieldHelper.resetValidator(fc, required ? [Validators.required] : []);
  }

  private disableAndClearField(fieldName: string): void {
    const fc = this.configObject[fieldName];
    requireFormControl(fc).setValue(null);
    DynamicFieldHelper.resetValidator(fc, []);
    requireFormControl(fc).disable();
  }

  private getAssetclassObserver(): Observable<Assetclass> | Observable<Assetclass[]> {
    const algoCallParam = this.algoCallParam();
    if (algoCallParam.thisObject) {
      const id = (<AlgoAssetclass>algoCallParam.thisObject).assetclass?.idAssetClass;
      return id == null ? of<Assetclass[]>([]) : this.assetclassService.getAssetclass(id);
    } else {
      return this.assetclassService.getUnusedAssetclassForAlgo(requireAlgoNodeId(algoCallParam.parentObject));
    }
  }

  private disableEnableInputForExisting(disable: boolean): void {
    FormHelper.disableEnableFieldConfigs(disable, [this.configObject.assetclass]);
    if (!disable) {
      this.configObject.assetclass.elementRef?.nativeElement.focus();
    } else {
      this.configObject.idSecurityaccount1.elementRef?.nativeElement.focus();
    }
  }
}
