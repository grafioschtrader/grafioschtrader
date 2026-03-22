import {Component, Input, OnInit} from '@angular/core';
import {Validators} from '@angular/forms';
import {GlobalparameterService} from '../../lib/services/globalparameter.service';
import {MessageToastService} from '../../lib/message/message.toast.service';
import {TranslateModule, TranslateService} from '@ngx-translate/core';
import {GenericConnectorDefService} from '../service/generic.connector.def.service';
import {AppHelper} from '../../lib/helper/app.helper';
import {GenericConnectorDef} from '../../entities/generic.connector.def';
import {RateLimitType} from '../../shared/types/rate.limit.type';
import {AssetclassCategory} from '../../shared/types/assetclass.category';
import {SimpleEntityEditBase} from '../../lib/edit/simple.entity.edit.base';
import {DynamicFieldHelper} from '../../lib/helper/dynamic.field.helper';
import {SelectOptionsHelper} from '../../lib/helper/select.options.helper';
import {TranslateHelper} from '../../lib/helper/translate.helper';
import {AppSettings} from '../../shared/app.settings';
import {AppHelpIds} from '../../shared/help/help.ids';
import {DialogModule} from 'primeng/dialog';
import {DynamicFormModule} from '../../lib/dynamic-form/dynamic-form.module';
import {FieldsetModule} from 'primeng/fieldset';
import {YamlEditorComponent} from '../../algo/component/yaml-editor.component';
import {HttpClient} from '@angular/common/http';
import {MultilanguageString} from '../../lib/entities/multilanguage.string';
import {StockexchangeService} from '../../stockexchange/service/stockexchange.service';
import {Stockexchange} from '../../entities/stockexchange';
import {ValueKeyHtmlSelectOptions} from '../../lib/dynamic-form/models/value.key.html.select.options';
import {TreeNode} from 'primeng/api';
import {combineLatest} from 'rxjs';

@Component({
  selector: 'generic-connector-def-edit',
  template: `
    <p-dialog header="{{'GENERIC_CONNECTOR_DEF' | translate}}" [visible]="visibleDialog"
              [style]="{width: '900px'}"
              (onShow)="onShow($event)" (onHide)="onHide($event)" [modal]="true">

      <dynamic-form [config]="config" [formConfig]="formConfig" [translateService]="translateService"
                    #form="dynamicForm" (submitBt)="submit($event)">
      </dynamic-form>

      <p-fieldset [legend]="'TOKEN_CONFIG_YAML' | translate" [toggleable]="true" [collapsed]="true"
                  styleClass="mt-3">
        <yaml-editor [height]="'300px'" [(value)]="tokenConfigYamlValue"
                     [schema]="tokenConfigSchema"></yaml-editor>
      </p-fieldset>
    </p-dialog>`,
  standalone: true,
  imports: [DialogModule, DynamicFormModule, TranslateModule, FieldsetModule, YamlEditorComponent]
})
export class GenericConnectorDefEditComponent extends SimpleEntityEditBase<GenericConnectorDef> implements OnInit {
  @Input() callParam: GenericConnectorDef;

  tokenConfigYamlValue = '';
  tokenConfigSchema: any;
  private geoTreeNodes: TreeNode[] = [];
  private catTranslations: {[key: string]: string} = {};

  constructor(private genericConnectorDefService: GenericConnectorDefService,
              private stockexchangeService: StockexchangeService,
              private httpClient: HttpClient,
              translateService: TranslateService,
              gps: GlobalparameterService,
              messageToastService: MessageToastService) {
    super(AppHelpIds.HELP_BASEDATA_GENERIC_CONNECTOR, AppHelper.toUpperCaseWithUnderscore(AppSettings.GENERIC_CONNECTOR_DEF),
      translateService, gps, messageToastService, genericConnectorDefService);
    this.loadTokenConfigSchema();
  }

  ngOnInit(): void {
    this.formConfig = AppHelper.getDefaultFormConfig(this.gps, 5, this.helpLink.bind(this));

    this.config = [
      DynamicFieldHelper.createFieldInputStringHeqF('shortId', 32, true),
      DynamicFieldHelper.createFieldTextareaInputStringHeqF('readableName', 100, true),
      DynamicFieldHelper.createFieldTextareaInputString('en', 'DESCRIPTION', 2000, true,
        {labelSuffix: 'EN', dataproperty: 'descriptionNLS.map.en'}),
      DynamicFieldHelper.createFieldTextareaInputString('de', 'DESCRIPTION', 2000, true,
        {labelSuffix: 'DE', dataproperty: 'descriptionNLS.map.de'}),
      DynamicFieldHelper.createFieldTextareaInputStringHeqF('domainUrl', 255, true),
      DynamicFieldHelper.createFieldCheckboxHeqF('needsApiKey'),
      DynamicFieldHelper.createFieldSelectStringHeqF('rateLimitType', true),
      DynamicFieldHelper.createFieldInputNumberHeqF('rateLimitRequests', false, 5, 0, false),
      DynamicFieldHelper.createFieldInputNumberHeqF('rateLimitPeriodSec', false, 5, 0, false),
      DynamicFieldHelper.createFieldInputNumberHeqF('rateLimitConcurrent', false, 3, 0, false),
      DynamicFieldHelper.createFieldInputNumberHeqF('intradayDelaySeconds', false, 5, 0, false),
      DynamicFieldHelper.createFieldTextareaInputStringHeqF('regexUrlPattern', 255, false),
      DynamicFieldHelper.createFieldCheckboxHeqF('supportsSecurity'),
      DynamicFieldHelper.createFieldCheckboxHeqF('supportsCurrency'),
      DynamicFieldHelper.createFieldCheckboxHeqF('needHistoryGapFiller'),
      DynamicFieldHelper.createFieldCheckboxHeqF('gbxDividerEnabled'),
      DynamicFieldHelper.createFieldMultiSelectStringHeqF('supportedCategories', false),
      DynamicFieldHelper.createFieldTreeSelectHeqF('geoInclusions', false, {propagateTreeSelection: false}),
      DynamicFieldHelper.createFieldTreeSelectHeqF('geoExclusions', false),
      DynamicFieldHelper.createSubmitButton()
    ];
    this.configObject = TranslateHelper.prepareFieldsAndErrors(this.translateService, this.config);
  }

  protected override initialize(): void {
    this.configObject.rateLimitType.valueKeyHtmlOptions =
      SelectOptionsHelper.createHtmlOptionsFromEnum(this.translateService, RateLimitType);
    this.configObject.supportedCategories.valueKeyHtmlOptions =
      SelectOptionsHelper.createHtmlOptionsFromEnum(this.translateService, AssetclassCategory);

    this.configObject.rateLimitType.formControl.valueChanges.subscribe(value =>
      this.updateRateLimitTypeDependencies(value));
    this.updateRateLimitTypeDependencies(this.callParam?.rateLimitType != null
      ? RateLimitType[this.callParam.rateLimitType] : null);

    const categoryNames = Object.keys(AssetclassCategory).filter(k => isNaN(Number(k)));
    combineLatest([
      this.stockexchangeService.getAllStockexchangesBaseData(),
      this.translateService.get(categoryNames)
    ]).subscribe(([baseData, catTrans]) => {
      this.catTranslations = catTrans;
      this.geoTreeNodes = this.buildGeoTree(baseData.stockexchanges, baseData.countries);
      this.configObject.geoInclusions.treeNodes = this.geoTreeNodes;

      // Rebuild geoExclusions tree whenever geoInclusions selection changes
      this.configObject.geoInclusions.formControl.valueChanges.subscribe(() =>
        this.rebuildExclusionsTree());

      this.form.setDefaultValuesAndEnableSubmit();
      if (this.callParam) {
        this.form.transferBusinessObjectToForm(this.callParam);
        this.loadSupportedCategories();
        this.loadGeoRestrictions();
        if (this.callParam.instrumentCount > 0) {
          this.configObject.shortId.formControl.disable();
        }
      }
      this.configObject.shortId.elementRef.nativeElement.focus();
      this.tokenConfigYamlValue = this.callParam?.tokenConfigYaml || '';
    });
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

  private updateRateLimitTypeDependencies(rateLimitType: string): void {
    if (rateLimitType === 'TOKEN_BUCKET') {
      this.enableField('rateLimitRequests', true);
      this.enableField('rateLimitPeriodSec', true);
      this.disableAndClearField('rateLimitConcurrent');
    } else if (rateLimitType === 'SEMAPHORE') {
      this.disableAndClearField('rateLimitRequests');
      this.disableAndClearField('rateLimitPeriodSec');
      this.enableField('rateLimitConcurrent', true);
    } else {
      this.disableAndClearField('rateLimitRequests');
      this.disableAndClearField('rateLimitPeriodSec');
      this.disableAndClearField('rateLimitConcurrent');
    }
  }

  /**
   * Builds a Country→MIC TreeNode hierarchy for the geo inclusions TreeSelect. Uses configured Stockexchange entities
   * (not all ISO 10383 MICs). With propagateSelectionDown/Up disabled, country and MIC nodes are independently
   * selectable — checking "US" means the whole country without requiring all US MICs to be checked.
   */
  private buildGeoTree(stockexchanges: Stockexchange[], countries: ValueKeyHtmlSelectOptions[]): TreeNode[] {
    const countryNames: {[cc: string]: string} = {};
    countries.forEach(c => countryNames[c.key as string] = c.value);

    const countryMap: {[cc: string]: TreeNode} = {};
    stockexchanges.forEach(se => {
      if (!countryMap[se.countryCode]) {
        countryMap[se.countryCode] = {
          key: se.countryCode,
          label: countryNames[se.countryCode] || se.countryCode,
          data: se.countryCode,
          icon: 'fi fi-' + se.countryCode.toLowerCase(),
          children: [],
          selectable: true
        };
      }
      countryMap[se.countryCode].children.push({
        key: se.mic,
        label: se.name + ' (' + se.mic + ')',
        data: se.mic,
        selectable: true
      });
    });
    return Object.values(countryMap).sort((a, b) => a.label.localeCompare(b.label));
  }

  /**
   * Loads the supportedCategories comma-separated string into the multi-select as an array of enum name strings.
   */
  private loadSupportedCategories(): void {
    if (this.callParam.supportedCategories) {
      const cats = this.callParam.supportedCategories.split(',').map(s => s.trim());
      this.configObject.supportedCategories.formControl.setValue(cats);
    }
  }

  /**
   * Parses geoRestrictions into geoInclusions TreeSelect (plain codes) and geoExclusions TreeSelect (:-CATEGORY
   * tokens). Since geoInclusions has no propagation, each node is checked independently.
   */
  private loadGeoRestrictions(): void {
    if (!this.callParam.geoRestrictions) {
      return;
    }
    const tokens = this.callParam.geoRestrictions.trim().split(/\s+/);
    const inclusions: string[] = [];
    const exclusionTokens: string[] = [];
    tokens.forEach(token => {
      const colonIdx = token.indexOf(':-');
      if (colonIdx > 0) {
        exclusionTokens.push(token);
        inclusions.push(token.substring(0, colonIdx));
      } else {
        inclusions.push(token);
      }
    });
    // Set geoInclusions as TreeNode[] — collect actual TreeNode references (no propagation)
    const inclusionSet = new Set(inclusions);
    const inclusionNodes: TreeNode[] = [];
    this.geoTreeNodes.forEach(countryNode => {
      if (inclusionSet.has(countryNode.key)) {
        inclusionNodes.push(countryNode);
      }
      countryNode.children?.forEach(micNode => {
        if (inclusionSet.has(micNode.key)) {
          inclusionNodes.push(micNode);
        }
      });
    });
    this.configObject.geoInclusions.formControl.setValue(inclusionNodes.length > 0 ? inclusionNodes : null);
    // Build exclusions tree from current inclusions, then set exclusion selections
    this.rebuildExclusionsTree();
    if (exclusionTokens.length > 0) {
      const exclKeySet = new Set(exclusionTokens.map(token => {
        const colonIdx = token.indexOf(':-');
        return token.substring(0, colonIdx) + ':-' + token.substring(colonIdx + 2);
      }));
      // Collect matching TreeNode references from the exclusions tree (propagation ON: add parent when all children selected)
      const exclNodes: TreeNode[] = [];
      const exclTreeNodes = this.configObject.geoExclusions.treeNodes || [];
      exclTreeNodes.forEach(geoNode => {
        const childCount = geoNode.children?.length || 0;
        let checkedCount = 0;
        geoNode.children?.forEach(catNode => {
          if (exclKeySet.has(catNode.key)) {
            exclNodes.push(catNode);
            checkedCount++;
          }
        });
        if (checkedCount > 0 && checkedCount === childCount) {
          exclNodes.push(geoNode);
        }
      });
      this.configObject.geoExclusions.formControl.setValue(exclNodes.length > 0 ? exclNodes : null);
    }
  }

  /**
   * Rebuilds the geoExclusions tree based on currently selected geo codes in geoInclusions. Each selected geo code
   * becomes a parent node with AssetclassCategory children (translated labels). Preserves existing exclusion
   * selections where possible.
   */
  private rebuildExclusionsTree(): void {
    const inclusionVal: TreeNode[] = this.configObject.geoInclusions.formControl.value;
    const oldExclVal: TreeNode[] = this.configObject.geoExclusions.formControl.value;
    const categoryNames = Object.keys(AssetclassCategory).filter(k => isNaN(Number(k)));

    // Collect all checked geo codes from inclusions (TreeNode[])
    const selectedKeys = new Set<string>(inclusionVal?.map(n => n.key) || []);

    const geoCodes: {key: string; label: string; icon?: string}[] = [];
    this.geoTreeNodes.forEach(countryNode => {
      if (selectedKeys.has(countryNode.key)) {
        geoCodes.push({key: countryNode.key, label: countryNode.label, icon: countryNode.icon});
      }
      countryNode.children?.forEach(micNode => {
        if (selectedKeys.has(micNode.key)) {
          geoCodes.push({key: micNode.key, label: micNode.label});
        }
      });
    });

    // Build tree: GeoCode → Category
    const newExclusionNodes = geoCodes.map(geo => ({
      key: geo.key,
      label: geo.label,
      data: geo.key,
      icon: geo.icon,
      selectable: true,
      children: categoryNames.map(catName => ({
        key: geo.key + ':-' + catName,
        label: this.catTranslations[catName] || catName,
        data: {geo: geo.key, category: catName},
        selectable: true
      }))
    }));
    this.configObject.geoExclusions.treeNodes = newExclusionNodes;

    // Preserve old selections that still exist in the new tree (match by key, use NEW node references)
    if (oldExclVal && oldExclVal.length > 0) {
      const oldKeys = new Set<string>(oldExclVal.map(n => n.key));
      const newNodesByKey = new Map<string, TreeNode>();
      newExclusionNodes.forEach(geoNode => {
        newNodesByKey.set(geoNode.key, geoNode);
        geoNode.children?.forEach(catNode => newNodesByKey.set(catNode.key, catNode));
      });
      const preserved: TreeNode[] = [];
      oldKeys.forEach(key => {
        const newNode = newNodesByKey.get(key);
        if (newNode) {
          preserved.push(newNode);
        }
      });
      this.configObject.geoExclusions.formControl.setValue(preserved.length > 0 ? preserved : null);
    } else {
      this.configObject.geoExclusions.formControl.setValue(null);
    }
  }

  /**
   * Merges geoInclusions (checked geo codes) and geoExclusions (checked GEO:-CATEGORY nodes) back into a single
   * geoRestrictions string.
   */
  private mergeGeoRestrictions(entity: GenericConnectorDef): void {
    const inclusionVal: TreeNode[] = this.configObject.geoInclusions.formControl.value;
    const tokens: string[] = [];

    if (inclusionVal) {
      const inclusionKeys = new Set<string>(inclusionVal.map(n => n.key));
      this.geoTreeNodes.forEach(countryNode => {
        if (inclusionKeys.has(countryNode.key)) {
          tokens.push(countryNode.key);
        }
        countryNode.children?.forEach(micNode => {
          if (inclusionKeys.has(micNode.key)) {
            tokens.push(micNode.key);
          }
        });
      });
    }
    const exclVal: TreeNode[] = this.configObject.geoExclusions.formControl.value;
    if (exclVal) {
      // Only serialize leaf nodes (geo:-CATEGORY), skip parent geo nodes
      const exclKeys = new Set<string>(exclVal.map(n => n.key));
      const exclNodes = this.configObject.geoExclusions.treeNodes || [];
      exclNodes.forEach(geoNode => {
        geoNode.children?.forEach(catNode => {
          if (exclKeys.has(catNode.key)) {
            tokens.push(catNode.key);
          }
        });
      });
    }
    entity.geoRestrictions = tokens.length > 0 ? tokens.join(' ') : null;
  }

  protected override getNewOrExistingInstanceBeforeSave(value: {[name: string]: any}): GenericConnectorDef {
    const entity = new GenericConnectorDef();
    this.copyFormToPublicBusinessObject(entity, this.callParam, null);
    this.form.cleanMaskAndTransferValuesToBusinessObject(entity);
    const values: any = {};
    this.form.cleanMaskAndTransferValuesToBusinessObject(values, true);
    entity.descriptionNLS = entity.descriptionNLS || new MultilanguageString();
    entity.descriptionNLS.map = entity.descriptionNLS.map || {de: null, en: null};
    entity.descriptionNLS.map.en = values.en;
    entity.descriptionNLS.map.de = values.de;
    entity.tokenConfigYaml = this.tokenConfigYamlValue?.trim() || null;

    // supportedCategories: convert array back to comma-separated string
    const catArray = this.configObject.supportedCategories.formControl.value;
    entity.supportedCategories = catArray && catArray.length > 0 ? catArray.join(',') : null;

    // geoRestrictions: merge tree selection + exclusions
    this.mergeGeoRestrictions(entity);

    return entity;
  }

  private loadTokenConfigSchema(): void {
    this.httpClient.get('assets/schemas/token-config-schema.json').subscribe({
      next: (schema: any) => this.tokenConfigSchema = schema,
      error: () => console.warn('Failed to load token config schema')
    });
  }
}
