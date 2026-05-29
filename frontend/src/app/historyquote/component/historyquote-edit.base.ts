import {Directive, Input, OnInit} from '@angular/core';
import moment from 'moment';
import {TranslateService} from '@ngx-translate/core';

import {SimpleEntityEditBase} from '../../lib/edit/simple.entity.edit.base';
import {ServiceEntityUpdate} from '../../lib/edit/service.entity.update';
import {HistoryquoteBase} from '../../entities/historyquote.base';
import {Securitycurrency} from '../../entities/securitycurrency';
import {DataType} from '../../lib/dynamic-form/models/data.type';
import {FieldConfig} from '../../lib/dynamic-form/models/field.config';
import {AppHelper} from '../../lib/helper/app.helper';
import {AppSettings} from '../../shared/app.settings';
import {DynamicFieldHelper} from '../../lib/helper/dynamic.field.helper';
import {TranslateHelper} from '../../lib/helper/translate.helper';
import {AuditHelper} from '../../lib/helper/audit.helper';
import {GlobalparameterService} from '../../lib/services/globalparameter.service';
import {MessageToastService} from '../../lib/message/message.toast.service';
import {ProposeChangeEntityWithEntity} from '../../lib/proposechange/model/propose.change.entity.whit.entity';

/**
 * Shared base for the two history-quote edit dialogs: the live {@link HistoryquoteEditComponent} and the archive
 * {@link HistoryquoteLegacyEditComponent}. Both edit the same OHLCV + trading-date fields, are persisted through the
 * same propose-change approval flow, and differ only in a handful of details that are exposed here as abstract hooks:
 * which call-param entity is edited, which security/currency is the auditable parent, the concrete entity instance to
 * create, any extra leading field (the legacy view adds a read-only {@code transferDate}), and how the immutable
 * fields are toggled when the dialog opens.
 *
 * Subclasses keep their own {@code @Component} (selector, dialog header text, standalone imports) and the
 * {@code @Input() callParam}; everything else lives here.
 *
 * @template T the concrete history-quote entity type (live {@code Historyquote} or {@code HistoryquoteLegacy})
 */
@Directive()
export abstract class HistoryquoteEditBase<T extends HistoryquoteBase> extends SimpleEntityEditBase<T>
  implements OnInit {

  @Input() proposeChangeEntityWithEntity: ProposeChangeEntityWithEntity;

  protected constructor(helpId: string,
    i18nRecord: string,
    translateService: TranslateService,
    gps: GlobalparameterService,
    messageToastService: MessageToastService,
    serviceEntityUpdate: ServiceEntityUpdate<T>) {
    super(helpId, i18nRecord, translateService, gps, messageToastService, serviceEntityUpdate);
  }

  ngOnInit(): void {
    this.formConfig = AppHelper.getDefaultFormConfig(this.gps, 4, this.helpLink.bind(this));

    this.config = [
      DynamicFieldHelper.createFieldPcalendarHeqF(DataType.DateNumeric, 'date', true,
        {
          calendarConfig: {
            maxDate: moment().subtract(1, 'days').toDate(),
            disabledDays: [0, 6]
          }
        }),
      ...this.getAdditionalLeadingFields(),
      ...this.createOhlcvFields(),
      ...AuditHelper.getFullNoteRequestInputDefinition(this.closeDialog, this)
    ];
    this.configObject = this.config.reduce((acc, d) => ({
      ...acc, [d.field]: d
    }), {});
    TranslateHelper.translateMessageErrors(this.translateService, this.config);
  }

  protected override initialize(): void {
    this.transferAndToggleImmutableFields();
    AuditHelper.configureFormFromAuditableRights(this.translateService, this.gps,
      this.getSecuritycurrency(), this.form, this.configObject, this.proposeChangeEntityWithEntity, false);
  }

  protected override getNewOrExistingInstanceBeforeSave(value: { [name: string]: any }): T {
    const entity = this.createEntityInstance();
    this.copyFormToPublicBusinessObject(entity, this.getExistingEntity(), this.proposeChangeEntityWithEntity);
    entity.idSecuritycurrency = this.getSecuritycurrency().idSecuritycurrency;
    return entity;
  }

  /**
   * The volume + open + high + low + close fields shared by both dialogs. The close price is the only required value;
   * fraction-digit limits follow the user's locale settings.
   */
  protected createOhlcvFields(): FieldConfig[] {
    return [
      DynamicFieldHelper.createFieldInputNumberHeqF('volume', false,
        AppSettings.FID_MAX_INTEGER_DIGITS, 0, false),
      DynamicFieldHelper.createFieldInputNumberHeqF('open', false,
        AppSettings.FID_MAX_INT_REAL_DOUBLE, this.gps.getMaxFractionDigits(), false),
      DynamicFieldHelper.createFieldInputNumberHeqF('high', false,
        AppSettings.FID_MAX_INT_REAL_DOUBLE, this.gps.getMaxFractionDigits(), false),
      DynamicFieldHelper.createFieldInputNumberHeqF('low', false,
        AppSettings.FID_MAX_INT_REAL_DOUBLE, this.gps.getMaxFractionDigits(), false),
      DynamicFieldHelper.createFieldInputNumberHeqF('close', true,
        AppSettings.FID_MAX_INT_REAL_DOUBLE, this.gps.getMaxFractionDigits(), false)
    ];
  }

  /**
   * Hook for fields inserted directly after the {@code date} field and before the OHLCV block. The live dialog adds
   * none; the legacy dialog adds the read-only archival {@code transferDate}. Defaults to no extra fields.
   */
  protected getAdditionalLeadingFields(): FieldConfig[] {
    return [];
  }

  /** The auditable security or currency pair the edited row belongs to; drives rights and propose-change decisions. */
  protected abstract getSecuritycurrency(): Securitycurrency;

  /** The entity currently being edited (may be null in the live create case). */
  protected abstract getExistingEntity(): T;

  /** Creates a fresh instance of the concrete entity type for the save payload. */
  protected abstract createEntityInstance(): T;

  /**
   * Transfers the business object into the form (or sets defaults for a new live row) and disables the fields that
   * must not change (trading {@code date}, and the archival {@code transferDate} on the legacy dialog).
   */
  protected abstract transferAndToggleImmutableFields(): void;
}
