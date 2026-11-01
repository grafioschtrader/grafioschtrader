import {
  afterNextRender,
  Component,
  inject,
  Injector,
  OnInit,
  signal,
  viewChild,
  input,
  computed
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { disabled, hidden, metadata, required, SchemaPathTree } from '@angular/forms/signals';
import { FIELD_DISPLAY, SignalValueLink } from '../../lib/dynamic-form/signal/signal-form.model';
import { TranslateModule } from '@ngx-translate/core';
import { GenericConnectorDefService } from '../service/generic.connector.def.service';
import { AppHelper } from '../../lib/helper/app.helper';
import { GenericConnectorDef } from '../../entities/generic.connector.def';
import { RateLimitType } from '../../shared/types/rate.limit.type';
import { AssetclassCategory } from '../../shared/types/assetclass.category';
import { SimpleEntityEditBase } from '../../lib/edit/simple.entity.edit.base';
import { DynamicFieldHelper } from '../../lib/helper/dynamic.field.helper';
import { TranslateHelper } from '../../lib/helper/translate.helper';
import { AppSettings } from '../../shared/app.settings';
import { AppHelpIds } from '../../shared/help/help.ids';
import { DialogModule } from '@openng/optimus-ui/dialog';
import { SignalDynamicFormComponent } from '../../lib/dynamic-form/signal/signal-dynamic-form.component';
import { FieldsetModule } from '@openng/optimus-ui/fieldset';
import { YamlEditorComponent } from '../../algo/component/yaml-editor.component';
import { HttpClient } from '@angular/common/http';
import { MultilanguageString } from '../../lib/entities/multilanguage.string';
import { StockexchangeService } from '../../stockexchange/service/stockexchange.service';
import { Stockexchange } from '../../entities/stockexchange';
import { ValueKeyHtmlSelectOptions } from '../../lib/dynamic-form/models/value.key.html.select.options';
import { TreeNode } from '@openng/optimus-ui/api';
import { combineLatest } from 'rxjs';

interface GeoTreeNode extends TreeNode {
  key: string;
  label: string;
  children?: GeoTreeNode[];
}

interface ConnectorFormModel {
  shortId: string | null;
  domainUrl: string | null;
  readableName: string | null;
  needsApiKey: boolean;
  rateLimitType: string | null;
  rateLimitRequests: number | null;
  rateLimitPeriodSec: number | null;
  rateLimitConcurrent: number | null;
  regexUrlPattern: string | null;
  supportsSecurity: boolean;
  supportsCurrency: boolean;
  supportedCategories: string[] | null;
  geoInclusions: string[] | null;
  geoExclusions: string[] | null;
}

@Component({
  selector: 'generic-connector-def-edit',
  template: ` <p-dialog
    styleClass="big-dialog"
    header="{{ 'GENERIC_CONNECTOR_DEF' | translate }}"
    [visible]="visibleDialog()"
    [focusOnShow]="false"
    [style]="{ width: '900px' }"
    (onShow)="onShow($event)"
    (onHide)="onHide($event)"
    [modal]="true">
    <signal-dynamic-form
      [config]="config"
      [formConfig]="formConfig"
      [source]="callParam()"
      [values]="startValues()"
      [rules]="rules"
      [valueLinks]="valueLinks"
      #form="signalDynamicForm"
      (submitBt)="submit($event)" />

    <p-fieldset [legend]="'TOKEN_CONFIG_YAML' | translate" [toggleable]="true" [collapsed]="true" styleClass="mt-3">
      <yaml-editor
        #yamlEditor
        format="TOKENS"
        (syntaxValidChange)="updateSubmitState()"
        (validationPendingChange)="updateSubmitState()"
        [height]="'300px'"
        [(value)]="tokenConfigYamlValue"
        [schema]="tokenConfigSchema()" />
    </p-fieldset>
  </p-dialog>`,
  imports: [DialogModule, SignalDynamicFormComponent, TranslateModule, FieldsetModule, YamlEditorComponent]
})
export class GenericConnectorDefEditComponent
  extends SimpleEntityEditBase<GenericConnectorDef, SignalDynamicFormComponent<ConnectorFormModel>>
  implements OnInit
{
  private genericConnectorDefService: GenericConnectorDefService = inject(GenericConnectorDefService);
  private stockexchangeService: StockexchangeService = inject(StockexchangeService);
  private httpClient: HttpClient = inject(HttpClient);

  readonly yamlEditor = viewChild.required(YamlEditorComponent);
  readonly callParam = input<GenericConnectorDef | null>();

  readonly tokenConfigYamlValue = signal('');
  readonly tokenConfigSchema = signal<any>(null);
  readonly optionsLoading = signal(true);
  private readonly injector = inject(Injector);
  private readonly geoTree = signal<GeoTreeNode[]>([]);
  private get geoTreeNodes(): GeoTreeNode[] {
    return this.geoTree();
  }
  private catTranslations: { [key: string]: string } = {};

  private readonly choiceOptions = signal<{
    rates: ValueKeyHtmlSelectOptions[];
    categories: ValueKeyHtmlSelectOptions[];
  }>({ rates: [], categories: [] });
  private readonly fieldsLocked = computed(
    () => !!this.callParam()?.endpoints?.some((endpoint) => endpoint.everUsedSuccessfully)
  );
  readonly startValues = computed(() => ({
    supportedCategories:
      this.callParam()
        ?.supportedCategories?.split(',')
        .map((value) => value.trim()) ?? null
  }));
  readonly rules = (p: SchemaPathTree<ConnectorFormModel>): void => {
    disabled(p.shortId, { when: () => !!this.callParam()?.idGenericConnector });
    disabled(p.domainUrl, { when: () => !!this.callParam()?.idGenericConnector });
    for (const field of ['readableName', 'rateLimitType', 'regexUrlPattern'] as const)
      disabled(p[field], { when: () => this.fieldsLocked() });
    for (const field of ['needsApiKey', 'supportsSecurity', 'supportsCurrency'] as const)
      disabled(p[field], { when: () => this.fieldsLocked() });
    for (const field of ['rateLimitRequests', 'rateLimitPeriodSec', 'rateLimitConcurrent'] as const) {
      const activeType = field === 'rateLimitConcurrent' ? 'SEMAPHORE' : 'TOKEN_BUCKET';
      disabled(p[field], { when: ({ valueOf }) => this.fieldsLocked() || valueOf(p.rateLimitType) !== activeType });
      required(p[field]);
    }
    metadata(p.rateLimitType, FIELD_DISPLAY, () => ({ valueKeyHtmlOptions: this.choiceOptions().rates }));
    metadata(p.supportedCategories, FIELD_DISPLAY, () => ({ valueKeyHtmlOptions: this.choiceOptions().categories }));
    for (const field of ['geoInclusions', 'geoExclusions'] as const)
      hidden(p[field], { when: () => this.optionsLoading() });
    metadata(p.geoInclusions, FIELD_DISPLAY, () => ({ treeNodes: this.geoTree() }));
    metadata(p.geoExclusions, FIELD_DISPLAY, ({ valueOf }) => ({
      treeNodes: this.exclusionTree(valueOf(p.geoInclusions))
    }));
  };
  readonly valueLinks: SignalValueLink<ConnectorFormModel>[] = [
    {
      sources: ['geoInclusions'],
      target: 'geoExclusions',
      derive: (values, current) => {
        const tree = this.exclusionTree(values.geoInclusions ?? null);
        const keys = new Set(Array.isArray(current) ? current : []);
        const retained = tree
          .flatMap((node) => [node, ...(node.children ?? [])])
          .flatMap((node) => (node.key != null && keys.has(node.key) ? [node.key] : []));
        return retained.length ? retained : null;
      }
    }
  ];

  updateSubmitState(): void {
    const editor = this.yamlEditor();
    this.form().setButtonState('submit', {
      disabled: this.optionsLoading() || !editor.syntaxValid || editor.validating
    });
  }

  constructor() {
    super(
      AppHelpIds.HELP_BASEDATA_GENERIC_CONNECTOR,
      AppHelper.toUpperCaseWithUnderscore(AppSettings.GENERIC_CONNECTOR_DEF),
      inject(GenericConnectorDefService)
    );
    this.loadTokenConfigSchema();
  }

  override async submit(value: { [name: string]: any }): Promise<void> {
    if (await this.yamlEditor().validateForSubmit()) super.submit(value);
    else {
      this.form().enableSubmit();
      this.updateSubmitState();
    }
  }

  ngOnInit(): void {
    this.formConfig = AppHelper.getDefaultFormConfig(this.gps, 5, this.helpLink.bind(this));

    this.config = [
      DynamicFieldHelper.createFieldInputStringHeqF('shortId', 32, true),
      DynamicFieldHelper.createFieldTextareaInputStringHeqF('readableName', 100, true),
      DynamicFieldHelper.createFieldTextareaInputString('en', 'DESCRIPTION', 2000, true, {
        labelSuffix: 'EN',
        dataproperty: 'descriptionNLS.map.en'
      }),
      DynamicFieldHelper.createFieldTextareaInputString('de', 'DESCRIPTION', 2000, true, {
        labelSuffix: 'DE',
        dataproperty: 'descriptionNLS.map.de'
      }),
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
      DynamicFieldHelper.createFieldTreeSelectHeqF('geoInclusions', false, {
        propagateTreeSelection: false
      }),
      DynamicFieldHelper.createFieldTreeSelectHeqF('geoExclusions', false),
      DynamicFieldHelper.createSubmitButton()
    ];
    this.configObject = TranslateHelper.prepareFieldsAndErrors(this.translateService, this.config, false);
    this.configObject.submit.disabled = true;
  }

  protected override initialize(): void {
    this.optionsLoading.set(true);
    this.form().setButtonState('submit', { disabled: true });
    this.tokenConfigYamlValue.set(this.callParam()?.tokenConfigYaml || '');
    this.loadOptions();
    // Own the initial focus; Optimus' delayed focusOnShow would otherwise interrupt an early entry in another field.
    afterNextRender(() => this.configObject.shortId.baseInputComponent?.focus(), { injector: this.injector });
  }

  /** Publishes the translated choices and restores geographic selections once their tree nodes are available. */
  private loadOptions(): void {
    const categoryNames = Object.keys(AssetclassCategory).filter((k) => isNaN(Number(k)));
    const rateLimitNames = Object.keys(RateLimitType).filter((k) => isNaN(Number(k)));
    combineLatest([
      this.stockexchangeService.getAllStockexchangesBaseData(),
      this.translateService.get([...categoryNames, ...rateLimitNames])
    ])
      .pipe(this.untilDialogClosed())
      .subscribe(([baseData, translations]) => {
        this.catTranslations = translations;
        const rates = rateLimitNames
          .map((name) => new ValueKeyHtmlSelectOptions(name, translations[name]))
          .sort((a, b) => a.value.localeCompare(b.value));
        const categories = categoryNames
          .map((name) => new ValueKeyHtmlSelectOptions(name, translations[name]))
          .sort((a, b) => a.value.localeCompare(b.value));
        this.choiceOptions.set({ rates, categories });
        this.geoTree.set(this.buildGeoTree(baseData.stockexchanges, baseData.countries));
        if (this.callParam()) {
          this.loadGeoRestrictions();
        }
        this.optionsLoading.set(false);
        this.updateSubmitState();
      });
  }

  /**
   * Builds a Country→MIC TreeNode hierarchy for the geo inclusions TreeSelect. Uses configured Stockexchange entities
   * (not all ISO 10383 MICs). With propagateSelectionDown/Up disabled, country and MIC nodes are independently
   * selectable — checking "US" means the whole country without requiring all US MICs to be checked.
   */
  private buildGeoTree(stockexchanges: Stockexchange[], countries: ValueKeyHtmlSelectOptions[]): GeoTreeNode[] {
    const countryNames: { [cc: string]: string } = {};
    countries.forEach((c) => (countryNames[c.key as string] = c.value));

    const countryMap: Record<string, GeoTreeNode & { children: GeoTreeNode[] }> = {};
    stockexchanges.forEach((se) => {
      const { countryCode, mic } = se;
      if (countryCode == null || mic == null) return;
      if (!countryMap[countryCode]) {
        countryMap[countryCode] = {
          key: countryCode,
          label: countryNames[countryCode] || countryCode,
          data: countryCode,
          icon: 'fi fi-' + countryCode.toLowerCase(),
          children: [],
          selectable: true
        };
      }
      countryMap[countryCode].children.push({
        key: mic,
        label: se.name + ' (' + mic + ')',
        data: mic,
        selectable: true
      });
    });
    return Object.values(countryMap).sort((a, b) => a.label.localeCompare(b.label));
  }

  /**
   * Parses geoRestrictions into geoInclusions TreeSelect (plain codes) and geoExclusions TreeSelect (:-CATEGORY
   * tokens). Since geoInclusions has no propagation, each node is checked independently.
   */
  private loadGeoRestrictions(): void {
    const callParam = this.callParam();
    if (!callParam?.geoRestrictions) {
      return;
    }
    const tokens = callParam.geoRestrictions.trim().split(/\s+/);
    const inclusions: string[] = [];
    const exclusionTokens: string[] = [];
    tokens.forEach((token) => {
      const colonIdx = token.indexOf(':-');
      if (colonIdx > 0) {
        exclusionTokens.push(token);
        inclusions.push(token.substring(0, colonIdx));
      } else {
        inclusions.push(token);
      }
    });
    // Keep only selectable keys; option nodes receive cyclic parent references in the tree widget.
    const inclusionSet = new Set(inclusions);
    const inclusionKeys: string[] = [];
    this.geoTreeNodes.forEach((countryNode) => {
      if (inclusionSet.has(countryNode.key)) {
        inclusionKeys.push(countryNode.key);
      }
      countryNode.children?.forEach((micNode) => {
        if (inclusionSet.has(micNode.key)) {
          inclusionKeys.push(micNode.key);
        }
      });
    });
    this.form().setValue('geoInclusions', inclusionKeys.length > 0 ? inclusionKeys : null);
    // Build exclusions tree from current inclusions, then set exclusion selections
    if (exclusionTokens.length > 0) {
      const exclKeySet = new Set(
        exclusionTokens.map((token) => {
          const colonIdx = token.indexOf(':-');
          return token.substring(0, colonIdx) + ':-' + token.substring(colonIdx + 2);
        })
      );
      // Include a parent key when all its category children are selected (propagation enabled).
      const exclKeys: string[] = [];
      const exclTreeNodes = this.exclusionTree(this.form().value('geoInclusions') as string[] | null);
      exclTreeNodes.forEach((geoNode) => {
        const childCount = geoNode.children?.length || 0;
        let checkedCount = 0;
        geoNode.children?.forEach((catNode) => {
          if (catNode.key != null && exclKeySet.has(catNode.key)) {
            exclKeys.push(catNode.key);
            checkedCount++;
          }
        });
        if (checkedCount > 0 && checkedCount === childCount) {
          if (geoNode.key != null) exclKeys.push(geoNode.key);
        }
      });
      this.form().setValue('geoExclusions', exclKeys.length > 0 ? exclKeys : null);
    }
  }

  /**
   * Rebuilds the geoExclusions tree based on currently selected geo codes in geoInclusions. Each selected geo code
   * becomes a parent node with AssetclassCategory children (translated labels). The value link retains selections
   * whose keys still occur in this tree.
   */
  private exclusionTree(inclusionVal: string[] | null): TreeNode[] {
    const categoryNames = Object.keys(AssetclassCategory).filter((k) => isNaN(Number(k)));

    const selectedKeys = new Set(inclusionVal ?? []);

    const geoCodes: { key: string; label: string; icon?: string }[] = [];
    this.geoTreeNodes.forEach((countryNode) => {
      if (selectedKeys.has(countryNode.key)) {
        geoCodes.push({
          key: countryNode.key,
          label: countryNode.label,
          icon: countryNode.icon
        });
      }
      countryNode.children?.forEach((micNode) => {
        if (selectedKeys.has(micNode.key)) {
          geoCodes.push({ key: micNode.key, label: micNode.label });
        }
      });
    });

    // Build tree: GeoCode → Category
    const newExclusionNodes = geoCodes.map((geo) => ({
      key: geo.key,
      label: geo.label,
      data: geo.key,
      icon: geo.icon,
      selectable: true,
      children: categoryNames.map((catName) => ({
        key: geo.key + ':-' + catName,
        label: this.catTranslations[catName] || catName,
        data: { geo: geo.key, category: catName },
        selectable: true
      }))
    }));
    return newExclusionNodes;
  }

  /**
   * Merges geoInclusions (checked geo codes) and geoExclusions (checked GEO:-CATEGORY nodes) back into a single
   * geoRestrictions string.
   */
  private mergeGeoRestrictions(entity: GenericConnectorDef): void {
    const inclusionVal = this.form().value('geoInclusions') as string[] | null;
    const tokens: string[] = [];

    if (inclusionVal) {
      const inclusionKeys = new Set(inclusionVal);
      this.geoTreeNodes.forEach((countryNode) => {
        if (inclusionKeys.has(countryNode.key)) {
          tokens.push(countryNode.key);
        }
        countryNode.children?.forEach((micNode) => {
          if (inclusionKeys.has(micNode.key)) {
            tokens.push(micNode.key);
          }
        });
      });
    }
    const exclVal = this.form().value('geoExclusions') as string[] | null;
    if (exclVal) {
      // Only serialize leaf nodes (geo:-CATEGORY), skip parent geo nodes
      const exclKeys = new Set(exclVal);
      const exclNodes = this.exclusionTree(inclusionVal);
      exclNodes.forEach((geoNode) => {
        geoNode.children?.forEach((catNode) => {
          if (catNode.key != null && exclKeys.has(catNode.key)) {
            tokens.push(catNode.key);
          }
        });
      });
    }
    entity.geoRestrictions = tokens.length > 0 ? tokens.join(' ') : null;
  }

  protected override getNewOrExistingInstanceBeforeSave(value: { [name: string]: any }): GenericConnectorDef {
    const entity = new GenericConnectorDef();
    this.copyFormToPublicBusinessObject(entity, this.callParam(), null);
    this.form().cleanMaskAndTransferValuesToBusinessObject(entity);
    const values: any = {};
    this.form().cleanMaskAndTransferValuesToBusinessObject(values, true);
    entity.descriptionNLS = entity.descriptionNLS || new MultilanguageString();
    entity.descriptionNLS.map = entity.descriptionNLS.map || {
      de: null,
      en: null
    };
    entity.descriptionNLS.map.en = values.en;
    entity.descriptionNLS.map.de = values.de;
    entity.tokenConfigYaml = this.tokenConfigYamlValue().trim() || null;

    // supportedCategories: convert array back to comma-separated string
    const catArray = this.form().value('supportedCategories') as string[] | null;
    entity.supportedCategories = catArray && catArray.length > 0 ? catArray.join(',') : null;

    // Inactive rate settings are cleared on save; locked existing settings retain their values.
    if (!this.fieldsLocked()) {
      if (this.form().value('rateLimitType') !== 'TOKEN_BUCKET') {
        entity.rateLimitRequests = null;
        entity.rateLimitPeriodSec = null;
      }
      if (this.form().value('rateLimitType') !== 'SEMAPHORE') entity.rateLimitConcurrent = null;
    }
    // geoRestrictions: merge tree selection + exclusions
    this.mergeGeoRestrictions(entity);

    return entity;
  }

  private loadTokenConfigSchema(): void {
    this.httpClient
      .get('assets/schemas/token-config-schema.json')
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (schema: any) => this.tokenConfigSchema.set(schema),
        error: () => console.warn('Failed to load token config schema')
      });
  }
}
