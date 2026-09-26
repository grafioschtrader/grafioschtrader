import { SimpleEntityEditBase } from '../../lib/edit/simple.entity.edit.base';
import { Observable, Subscription } from 'rxjs';
import { map } from 'rxjs/operators';
import { TranslateService } from '@ngx-translate/core';
import { GlobalparameterService } from '../../lib/services/globalparameter.service';
import { MessageToastService } from '../../lib/message/message.toast.service';
import { HelpIds } from '../../lib/help/help.ids';
import { ServiceEntityUpdate } from '../../lib/edit/service.entity.update';
import { ValueKeyHtmlSelectOptions } from '../../lib/dynamic-form/models/value.key.html.select.options';
import { FieldConfig } from '../../lib/dynamic-form/models/field.config';
import { DataType } from '../../lib/dynamic-form/models/data.type';
import { Directive, Input } from '@angular/core';
import { AlgoCallParam } from '../model/algo.dialog.visible';
import { DynamicFieldHelper } from '../../lib/helper/dynamic.field.helper';
import { SecurityaccountService } from '../../securityaccount/service/securityaccount.service';

/**
 * Base of the dialogs that edit an asset class or an instrument of an algo hierarchy. Both carry the two security
 * account priorities a simulation trades the node at. The accounts offered are served by the backend, which keeps only
 * those whose trading periods allow the instrument type of the node; the second priority offers every such account
 * except the one chosen as first priority.
 */
@Directive()
export abstract class AlgoAssetclassSecurityBaseEdit<T> extends SimpleEntityEditBase<T> {
  @Input() algoCallParam: AlgoCallParam;

  protected securityaccount1ChangedSub: Subscription;
  /** The security accounts allowed for the instrument type currently selected in the dialog. */
  protected accountOptions: ValueKeyHtmlSelectOptions[] = [];

  protected constructor(
    i18nRecord: string,
    translateService: TranslateService,
    gps: GlobalparameterService,
    messageToastService: MessageToastService,
    serviceEntityUpdate: ServiceEntityUpdate<T>,
    protected securityaccountService: SecurityaccountService
  ) {
    super(HelpIds.HELP_ALGO_TREE, i18nRecord, translateService, gps, messageToastService, serviceEntityUpdate);
  }

  override onHide(event): void {
    this.securityaccount1ChangedSub && this.securityaccount1ChangedSub.unsubscribe();
    super.onHide(event);
  }

  protected getFieldDefinition(): FieldConfig[] {
    return [
      DynamicFieldHelper.createFieldSelectString('idSecurityaccount1', 'ALGO_SECURITYACCOUNT_1', false),
      DynamicFieldHelper.createFieldSelectString('idSecurityaccount2', 'ALGO_SECURITYACCOUNT_2', false),
      DynamicFieldHelper.createFieldMinMaxNumber(DataType.Numeric, 'percentage', 'ALGO_PERCENTAGE', true, 0.1, 100, {
        fieldSuffix: '%'
      }),
      DynamicFieldHelper.createSubmitButton()
    ];
  }

  /**
   * Loads the security accounts allowed for an instrument or an asset class.
   *
   * @param idSecuritycurrency - The instrument of the node, if known
   * @param idAssetClass - The asset class of the node, if known; with neither every security account is offered
   * @returns The options with numeric keys, matching the numeric ids of the entity
   */
  protected getAccountOptions(
    idSecuritycurrency?: number,
    idAssetClass?: number
  ): Observable<ValueKeyHtmlSelectOptions[]> {
    return this.securityaccountService
      .getAlgoAccountOptions(idSecuritycurrency, idAssetClass)
      .pipe(map((options) => options.map((o) => new ValueKeyHtmlSelectOptions(+o.key, o.value))));
  }

  /**
   * Applies a freshly loaded list of allowed accounts to both priority selects. A chosen account that is no longer
   * allowed is cleared, since the backend would refuse to save it.
   *
   * @param options - The allowed security accounts
   */
  protected setAccountOptions(options: ValueKeyHtmlSelectOptions[]): void {
    this.accountOptions = options;
    this.configObject.idSecurityaccount1.valueKeyHtmlOptions = this.withEmpty(options);
    this.clearIfNotOffered('idSecurityaccount1');
    this.setSecurityaccount2Options(this.configObject.idSecurityaccount1.formControl.value);
  }

  /** Recomputes the options of the second priority whenever the first one changes. */
  protected valueChangedOnSecurityaccount1(): void {
    this.securityaccount1ChangedSub = this.configObject.idSecurityaccount1.formControl.valueChanges.subscribe(
      (idSecurityaccount: number) => this.setSecurityaccount2Options(idSecurityaccount)
    );
  }

  /**
   * The second priority is only offered once a first one is chosen, and never the same account.
   *
   * @param idSecurityaccount1 - The account chosen as first priority, if any
   */
  private setSecurityaccount2Options(idSecurityaccount1: number): void {
    this.configObject.idSecurityaccount2.valueKeyHtmlOptions = idSecurityaccount1
      ? this.withEmpty(this.accountOptions.filter((o) => o.key !== +idSecurityaccount1))
      : this.withEmpty([]);
    this.clearIfNotOffered('idSecurityaccount2');
  }

  private clearIfNotOffered(fieldName: string): void {
    const fc = this.configObject[fieldName];
    const value = fc.formControl.value;
    if (value != null && value !== '' && !fc.valueKeyHtmlOptions.some((o) => o.key === +value)) {
      fc.formControl.setValue(null);
    }
  }

  private withEmpty(options: ValueKeyHtmlSelectOptions[]): ValueKeyHtmlSelectOptions[] {
    return [new ValueKeyHtmlSelectOptions('', ''), ...options];
  }
}
