import { Component, OnInit, ChangeDetectionStrategy } from '@angular/core';
import { Validators } from '@angular/forms';
import { AlgoAssetclass } from '../model/algo.assetclass';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { GlobalparameterService } from '../../lib/services/globalparameter.service';
import { MessageToastService } from '../../lib/message/message.toast.service';
import { AlgoAssetclassService } from '../service/algo.assetclass.service';
import { AppHelper } from '../../lib/helper/app.helper';
import { SecurityaccountService } from '../../securityaccount/service/securityaccount.service';
import { AssetclassService } from '../../assetclass/service/assetclass.service';
import { combineLatest, Observable, of } from 'rxjs';
import { Assetclass } from '../../entities/assetclass';
import { ValueKeyHtmlSelectOptions } from '../../lib/dynamic-form/models/value.key.html.select.options';
import { distinctUntilChanged } from 'rxjs/operators';
import { AlgoTop } from '../model/algo.top';
import { FormHelper } from '../../lib/dynamic-form/components/FormHelper';
import { AlgoAssetclassSecurityBaseEdit } from './algo.assetclass.security.base.edit';
import { DynamicFieldModelHelper } from '../../lib/helper/dynamic.field.model.helper';
import { DynamicFieldHelper } from '../../lib/helper/dynamic.field.helper';
import { TranslateHelper } from '../../lib/helper/translate.helper';
import { AppSettings } from '../../shared/app.settings';
import { BusinessSelectOptionsHelper } from '../../shared/securitycurrency/business.select.options.helper';
import { DialogModule } from '@openng/optimus-ui/dialog';
import { DynamicFormModule } from '../../lib/dynamic-form/dynamic-form.module';

@Component({
  selector: 'algo-assetclass-edit',
  template: ` <p-dialog
    header="{{ 'ALGO_ASSETCLASS' | translate }}"
    [visible]="visibleDialog"
    [style]="{ width: '600px' }"
    (onShow)="onShow($event)"
    (onHide)="onHide($event)"
    [modal]="true">
    <dynamic-form
      [config]="config"
      [formConfig]="formConfig"
      [translateService]="translateService"
      #form="dynamicForm"
      (submitBt)="submit($event)">
    </dynamic-form>
  </p-dialog>`,
  standalone: true,
  changeDetection: ChangeDetectionStrategy.Eager,
  imports: [DialogModule, DynamicFormModule, TranslateModule]
})
export class AlgoAssetclassEditComponent extends AlgoAssetclassSecurityBaseEdit<AlgoAssetclass> implements OnInit {
  constructor(
    private assetclassService: AssetclassService,
    translateService: TranslateService,
    gps: GlobalparameterService,
    messageToastService: MessageToastService,
    algoAssetclassService: AlgoAssetclassService,
    securityaccountService: SecurityaccountService
  ) {
    super('ALGO_ASSETCLASS', translateService, gps, messageToastService, algoAssetclassService, securityaccountService);
  }

  ngOnInit(): void {
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
        this.algoCallParam.formDefinition.fieldDescriptorInputAndShows,
        '',
        false
      ).map((field) => ({ ...field, labelHelpText: 'REBALANCE_OVERRIDE_HELP' })),
      ...this.getFieldDefinition()
    ];
    this.configObject = TranslateHelper.prepareFieldsAndErrors(this.translateService, this.config);
  }

  protected override initialize(): void {
    const existing = <AlgoAssetclass>this.algoCallParam.thisObject;
    // Detect custom category mode for existing entity
    const isCustom = existing && existing.name != null;
    const accountOptionsObservable: Observable<ValueKeyHtmlSelectOptions[]> = this.getAccountOptions(
      undefined,
      isCustom ? undefined : existing?.assetclass?.idAssetClass
    );
    this.valueChangedOnSecurityaccount1();

    // Subscribe to customCategory checkbox changes
    this.configObject.customCategory.formControl.valueChanges.subscribe((checked: boolean) => {
      this.updateCustomCategoryDependencies(checked);
    });

    const assetclassObservable = isCustom ? of([] as Assetclass[]) : this.getAssetclassObserver();

    combineLatest([assetclassObservable, accountOptionsObservable]).subscribe(
      (data: [Assetclass | Assetclass[], ValueKeyHtmlSelectOptions[]]) => {
        this.configObject.assetclass.referencedDataObject = Array.isArray(data[0]) ? data[0] : [data[0]];
        this.configObject.assetclass.valueKeyHtmlOptions =
          BusinessSelectOptionsHelper.assetclassCreateValueKeyHtmlSelectOptions(
            this.gps,
            this.translateService,
            this.configObject.assetclass.referencedDataObject
          );
        this.setAccountOptions(data[1]);
        if (existing) {
          this.form.transferBusinessObjectToForm(existing);
          // A saved priority whose account no longer allows the type is cleared, the backend would refuse it.
          this.setAccountOptions(data[1]);
        } else {
          // Creating: the allowed accounts follow the asset class; a custom category allows every account.
          this.configObject.assetclass.formControl.valueChanges
            .pipe(distinctUntilChanged())
            .subscribe((idAssetClass: number | string) =>
              this.getAccountOptions(undefined, idAssetClass ? +idAssetClass : undefined).subscribe((options) =>
                this.setAccountOptions(options)
              )
            );
        }

        if (isCustom) {
          this.configObject.customCategory.formControl.setValue(true);
        } else {
          this.updateCustomCategoryDependencies(false);
          this.disableEnableInputForExisting(this.algoCallParam.thisObject != null);
        }
      }
    );
  }

  protected override getNewOrExistingInstanceBeforeSave(value: { [name: string]: any }): AlgoAssetclass {
    const algoAssetclass: AlgoAssetclass = new AlgoAssetclass();
    if (this.algoCallParam.thisObject) {
      Object.assign(algoAssetclass, this.algoCallParam.thisObject);
    }
    this.form.cleanMaskAndTransferValuesToBusinessObject(algoAssetclass);
    // Not an entity field: it only asks the hierarchy view to open the instrument search after the save.
    this.algoCallParam.addInstrumentsBySearch = !!this.configObject.addInstrumentsBySearch.formControl.value;
    delete algoAssetclass['addInstrumentsBySearch'];
    if (this.configObject.customCategory.formControl.value) {
      algoAssetclass.assetclass = null;
    } else {
      algoAssetclass.name = null;
    }
    algoAssetclass.idAlgoAssetclassParent = (<AlgoTop>this.algoCallParam.parentObject).idAlgoAssetclassSecurity;
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
    if (this.algoCallParam.thisObject != null) {
      this.configObject.customCategory.formControl.disable({ emitEvent: false });
    }
  }

  private enableField(fieldName: string, required: boolean): void {
    const fc = this.configObject[fieldName];
    fc.formControl.enable();
    DynamicFieldHelper.resetValidator(fc, required ? [Validators.required] : []);
  }

  private disableAndClearField(fieldName: string): void {
    const fc = this.configObject[fieldName];
    fc.formControl.setValue(null);
    DynamicFieldHelper.resetValidator(fc, []);
    fc.formControl.disable();
  }

  private getAssetclassObserver(): Observable<Assetclass> | Observable<Assetclass[]> {
    if (this.algoCallParam.thisObject) {
      return this.assetclassService.getAssetclass(
        (<AlgoAssetclass>this.algoCallParam.thisObject).assetclass.idAssetClass
      );
    } else {
      return this.assetclassService.getUnusedAssetclassForAlgo(
        (<AlgoTop>this.algoCallParam.parentObject).idAlgoAssetclassSecurity
      );
    }
  }

  private disableEnableInputForExisting(disable: boolean): void {
    FormHelper.disableEnableFieldConfigs(disable, [this.configObject.assetclass]);
    if (!disable) {
      this.configObject.assetclass.elementRef.nativeElement.focus();
    } else {
      this.configObject.idSecurityaccount1.elementRef.nativeElement.focus();
    }
  }
}
