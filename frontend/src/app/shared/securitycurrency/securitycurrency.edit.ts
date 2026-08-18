import {Directive, EventEmitter, Input, Output, ViewChild} from '@angular/core';
import {DynamicFormComponent} from '../../lib/dynamic-form/containers/dynamic-form/dynamic-form.component';
import {FieldConfig} from '../../lib/dynamic-form/models/field.config';
import {FeedIdentifier, FeedSupport, IFeedConnector} from './ifeed.connector';
import {ProcessedActionData} from '../../lib/types/processed.action.data';
import {ProcessedAction} from '../../lib/types/processed.action';
import {TranslateService} from '@ngx-translate/core';
import {Subscription} from 'rxjs';
import {Validators} from '@angular/forms';
import {FormBase} from '../../lib/edit/form.base';
import {AuditHelper} from '../../lib/helper/audit.helper';
import {ProposeChangeEntityWithEntity} from '../../lib/proposechange/model/propose.change.entity.whit.entity';
import {GlobalparameterService} from '../../lib/services/globalparameter.service';
import {Security} from '../../entities/security';
import {Securitycurrency} from '../../entities/securitycurrency';
import {DynamicFieldHelper} from '../../lib/helper/dynamic.field.helper';
import {SelectOptionsHelper} from '../../lib/helper/select.options.helper';
import {FormHelper} from '../../lib/dynamic-form/components/FormHelper';
import {AppHelper} from '../../lib/helper/app.helper';


@Directive()
export abstract class SecuritycurrencyEdit extends FormBase {

  // Input from parent component
  @Input() securityCurrencypairCallParam: Security | Securitycurrency;
  @Input() proposeChangeEntityWithEntity: ProposeChangeEntityWithEntity;
  // Access child components
  @ViewChild(DynamicFormComponent, {static: true}) dynamicForm: DynamicFormComponent;
  // Output for parent view
  @Output() closeDialog = new EventEmitter<ProcessedActionData>();
  connectorSubscribe: { [fieldName: string]: Subscription } = {};
  feedPriceConnectors: IFeedConnector[];
  protected readonly ID_CONNECTOR_HISTORY = 'idConnectorHistory';
  protected readonly ID_CONNECTOR_INTRA = 'idConnectorIntra';
  protected connectorPriceFieldConfig: FieldConfig[];
  /**
   * Generic help text of every connector selection, captured before the first selection replaces it with the help of the
   * chosen connector. Keyed by field name, it is restored when no connector is selected or the selected one ships no help.
   */
  private readonly connectorHelpFallback: { [fieldName: string]: string } = {};

  // connectorFieldConfig: FieldConfig[];

  protected constructor(public translateService: TranslateService,
              public gps: GlobalparameterService) {
    super();
  }

  public onShow(event): void {
    setTimeout(() => this.loadHelperData());
  }

  public hideVisibleFeedConnectorsFields(connectorFieldConfig: FieldConfig[], hideConnector: boolean,
                                         feedIdentifier: FeedIdentifier): void {
    const idFieldConfigurations = connectorFieldConfig.filter(field => field.field.startsWith('id'));
    const urlFieldConfigurations = connectorFieldConfig.filter(field => field.field.startsWith('url'));
    if (hideConnector) {
      idFieldConfigurations.forEach(cfc => {
        if (this.connectorSubscribe[cfc.field]) {
          this.connectorSubscribe[cfc.field].unsubscribe();
          this.connectorSubscribe[cfc.field] = null;
        }
      });
    } else {
      this.valueChangedOnFeedConnectors(idFieldConfigurations, urlFieldConfigurations, feedIdentifier);
    }
    FormHelper.hideVisibleFieldConfigs(hideConnector, connectorFieldConfig);
  }

  onHide(event): void {
    Object.values(this.connectorSubscribe).forEach(cs => cs && cs.unsubscribe);
    this.closeDialog.emit(new ProcessedActionData(ProcessedAction.NO_CHANGE));
  }

  protected abstract loadHelperData(): void;

  protected prepareFeedConnectors(feedConnectors: IFeedConnector[], isCurrency: boolean): void {
    this.feedPriceConnectors = feedConnectors;
    this.feedConnectorsCreateValueKeyHtmlSelectOptions(this.configObject[this.ID_CONNECTOR_HISTORY], FeedSupport.FS_HISTORY, isCurrency);
    this.feedConnectorsCreateValueKeyHtmlSelectOptions(this.configObject[this.ID_CONNECTOR_INTRA], FeedSupport.FS_INTRA, isCurrency);
  }

  protected prepareExistingSecuritycurrency(focusControl: FieldConfig): void {
    this.dynamicForm.setDefaultValuesAndEnableSubmit();
    AuditHelper.transferToFormAndChangeButtonForProposaleEdit(this.translateService, this.gps,
      this.securityCurrencypairCallParam, this.dynamicForm, this.configObject, this.proposeChangeEntityWithEntity);
    focusControl.elementRef.nativeElement.focus();
  }

  protected disableEnableFeedUrlExtended(urlExtended: FieldConfig, feedIdentifiers: string[], feedIdentifier: FeedIdentifier): void {
    AppHelper.invisibleAndHide(urlExtended, feedIdentifiers.indexOf(FeedIdentifier[feedIdentifier]) >= 0);

    DynamicFieldHelper.resetValidator(urlExtended, (urlExtended.invisible) ? null : [Validators.required],
      (urlExtended.invisible) ? null : [DynamicFieldHelper.RULE_REQUIRED_TOUCHED]);
  }

  /**
   * Wires every connector selection to its url extension field. The help text of the chosen connector is put on the
   * selection itself and not on the url extension, because the latter is hidden for every connector which does not
   * require a url extension - its help would be unreachable.
   *
   * In a case of editing a currency pair some input fields are disabled.
   *
   * @param connectorIdConfigs Fields of connector Id
   * @param urlExtends Field of url extends
   * @param feedIdentifier Identifier which marks a connector as not requiring a url extension
   */
  private valueChangedOnFeedConnectors(connectorIdConfigs: FieldConfig[], urlExtends: FieldConfig[],
                                       feedIdentifier: FeedIdentifier): void {
    for (let i = 0; i < connectorIdConfigs.length; i++) {
      const connectorConfig = connectorIdConfigs[i];
      // Only the very first pass sees the generic text, later passes would capture a connector help text.
      this.connectorHelpFallback[connectorConfig.field] ??= connectorConfig.labelHelpText;
      this.connectorSubscribe[connectorConfig.field] = connectorConfig.formControl.valueChanges.subscribe(
        connector => {
          const foundConnector = this.feedPriceConnectors.find(fc => fc.id === connector);
          if (foundConnector) {
            connectorConfig.labelHelpText = ((this.ID_CONNECTOR_HISTORY === connectorConfig.field)
              ? foundConnector.description?.historicalDescription : foundConnector.description?.intraDescription)
              || this.connectorHelpFallback[connectorConfig.field];
            if (this.ID_CONNECTOR_INTRA === connectorConfig.field
              && foundConnector.securitycurrencyFeedSupport[FeedSupport[FeedSupport.FS_INTRA]]) {
              this.disableEnableFeedUrlExtended(urlExtends[i],
                foundConnector.securitycurrencyFeedSupport[FeedSupport[FeedSupport.FS_INTRA]],
                feedIdentifier);
            } else if (this.ID_CONNECTOR_HISTORY === connectorConfig.field
              && foundConnector.securitycurrencyFeedSupport[FeedSupport[FeedSupport.FS_HISTORY]]) {
              this.disableEnableFeedUrlExtended(urlExtends[i],
                foundConnector.securitycurrencyFeedSupport[FeedSupport[FeedSupport.FS_HISTORY]],
                feedIdentifier);
            } else if (foundConnector.securitycurrencyFeedSupport[FeedSupport[FeedSupport.FS_DIVIDEND]]) {
              this.disableEnableFeedUrlExtended(urlExtends[i],
                foundConnector.securitycurrencyFeedSupport[FeedSupport[FeedSupport.FS_DIVIDEND]],
                FeedIdentifier.DIVIDEND);
            } else if (foundConnector.securitycurrencyFeedSupport[FeedSupport[FeedSupport.FS_SPLIT]]) {
              this.disableEnableFeedUrlExtended(urlExtends[i],
                foundConnector.securitycurrencyFeedSupport[FeedSupport[FeedSupport.FS_SPLIT]],
                FeedIdentifier.SPLIT);
            }
          } else {
            // No connector is chosen
            connectorConfig.labelHelpText = this.connectorHelpFallback[connectorConfig.field];
            AppHelper.disableAndHideInput(urlExtends[i]);
          }
          if (connectorConfig.labelShowText) {
            connectorConfig.labelShowText = connectorConfig.labelHelpText;
          }
        });
    }
  }

  private feedConnectorsCreateValueKeyHtmlSelectOptions(fieldConfig: FieldConfig, filterType: FeedSupport, isCurrency: boolean): void {
    const historyProvider: IFeedConnector[] = this.feedPriceConnectors.filter(feedConnector =>
      !!feedConnector.securitycurrencyFeedSupport[FeedSupport[filterType]]
      && this.checkCurrencySecurityProvider(feedConnector, filterType, isCurrency));
    fieldConfig.valueKeyHtmlOptions = SelectOptionsHelper.createValueKeyHtmlSelectOptionsFromArray('id', 'readableName', historyProvider,
      !isCurrency);
  }

  private checkCurrencySecurityProvider(feedConnector: IFeedConnector, filterType: FeedSupport, isCurrency: boolean): boolean {
    if (isCurrency) {
      return feedConnector.securitycurrencyFeedSupport[FeedSupport[filterType]].indexOf(FeedIdentifier[FeedIdentifier.CURRENCY]) >= 0 ||
        feedConnector.securitycurrencyFeedSupport[FeedSupport[filterType]].indexOf(FeedIdentifier[FeedIdentifier.CURRENCY_URL]) >= 0;
    } else {
      return feedConnector.securitycurrencyFeedSupport[FeedSupport[filterType]].indexOf(FeedIdentifier[FeedIdentifier.SECURITY]) >= 0 ||
        feedConnector.securitycurrencyFeedSupport[FeedSupport[filterType]].indexOf(FeedIdentifier[FeedIdentifier.SECURITY_URL]) >= 0;
    }
  }

}
