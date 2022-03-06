import {Directive, EventEmitter, Input, Output, ViewChild} from '@angular/core';
import {DynamicFormComponent} from '../../dynamic-form/containers/dynamic-form/dynamic-form.component';
import {FieldConfig} from '../../dynamic-form/models/field.config';
import {FeedIdentifier, FeedSupport, IFeedConnector} from './ifeed.connector';
import {ProcessedActionData} from '../../shared/types/processed.action.data';
import {ProcessedAction} from '../../shared/types/processed.action';
import {TranslateService} from '@ngx-translate/core';
import {Subscription} from 'rxjs';
import {Validators} from '@angular/forms';
import {FormBase} from '../../shared/edit/form.base';
import {AuditHelper} from '../../shared/helper/audit.helper';
import {ProposeChangeEntityWithEntity} from '../../entities/proposechange/propose.change.entity.whit.entity';
import {GlobalparameterService} from '../../shared/service/globalparameter.service';
import {Security} from '../../entities/security';
import {Securitycurrency} from '../../entities/securitycurrency';
import {DynamicFieldHelper} from '../../shared/helper/dynamic.field.helper';
import {SelectOptionsHelper} from '../../shared/helper/select.options.helper';
import {FormHelper} from '../../dynamic-form/components/FormHelper';
import {AppHelper} from '../../shared/helper/app.helper';


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

  // connectorFieldConfig: FieldConfig[];

  constructor(public translateService: TranslateService,
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
    this.feedConnectorsCreateValueKeyHtmlSelectOptions(this.configObject[this.ID_CONNECTOR_HISTORY], FeedSupport.HISTORY, isCurrency);
    this.feedConnectorsCreateValueKeyHtmlSelectOptions(this.configObject[this.ID_CONNECTOR_INTRA], FeedSupport.INTRA, isCurrency);
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
   * In a case of editing a currency pair some input fields are disabled.
   *
   * @param connectorIdConfigs Fields of connector Id
   * @param urlExtends Field of url extends
   * @param isCurrency true if a currency pair ist edited
   */
  private valueChangedOnFeedConnectors(connectorIdConfigs: FieldConfig[], urlExtends: FieldConfig[],
                                       feedIdentifier: FeedIdentifier): void {
    for (let i = 0; i < connectorIdConfigs.length; i++) {
      this.connectorSubscribe[connectorIdConfigs[i].field] = connectorIdConfigs[i].formControl.valueChanges.subscribe(
        connector => {
          const foundConnector = this.feedPriceConnectors.find(fc => fc.id === connector);
          if (foundConnector) {
            urlExtends[i].labelHelpText = (this.configObject[this.ID_CONNECTOR_HISTORY] === connectorIdConfigs[i]) ?
              foundConnector.description.historicalDescription : foundConnector.description.intraDescription;
            if (this.ID_CONNECTOR_INTRA === connectorIdConfigs[i].field
              && foundConnector.securitycurrencyFeedSupport[FeedSupport[FeedSupport.INTRA]]) {
              this.disableEnableFeedUrlExtended(urlExtends[i],
                foundConnector.securitycurrencyFeedSupport[FeedSupport[FeedSupport.INTRA]],
                feedIdentifier);
            } else if (this.ID_CONNECTOR_HISTORY === connectorIdConfigs[i].field
              && foundConnector.securitycurrencyFeedSupport[FeedSupport[FeedSupport.HISTORY]]) {
              this.disableEnableFeedUrlExtended(urlExtends[i],
                foundConnector.securitycurrencyFeedSupport[FeedSupport[FeedSupport.HISTORY]],
                feedIdentifier);
            } else if (foundConnector.securitycurrencyFeedSupport[FeedSupport[FeedSupport.DIVIDEND]]) {
              this.disableEnableFeedUrlExtended(urlExtends[i],
                foundConnector.securitycurrencyFeedSupport[FeedSupport[FeedSupport.DIVIDEND]],
                FeedIdentifier.DIVIDEND);
            } else if (foundConnector.securitycurrencyFeedSupport[FeedSupport[FeedSupport.SPLIT]]) {
              this.disableEnableFeedUrlExtended(urlExtends[i],
                foundConnector.securitycurrencyFeedSupport[FeedSupport[FeedSupport.SPLIT]],
                FeedIdentifier.SPLIT);
            }
          } else {
            // No connector is chosen
            AppHelper.disableAndHideInput(urlExtends[i]);
          }
          if (urlExtends[i].labelShowText) {
            urlExtends[i].labelShowText = urlExtends[i].labelHelpText;
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
