import { Component, Input, OnInit, ChangeDetectionStrategy } from '@angular/core';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { DialogModule } from '@openng/optimus-ui/dialog';
import { combineLatest, of } from 'rxjs';

import { SimpleEntityEditBase } from '../../edit/simple.entity.edit.base';
import { DynamicFormModule } from '../../dynamic-form/dynamic-form.module';
import { DynamicFieldHelper } from '../../helper/dynamic.field.helper';
import { DataType } from '../../dynamic-form/models/data.type';
import { AppHelper } from '../../helper/app.helper';
import { AuditHelper } from '../../helper/audit.helper';
import { GlobalparameterService } from '../../services/globalparameter.service';
import { MessageToastService } from '../../message/message.toast.service';
import { HelpIds } from '../../help/help.ids';
import { TranslateHelper } from '../../helper/translate.helper';
import { SelectOptionsHelper } from '../../helper/select.options.helper';
import { GroupItem, ValueKeyHtmlSelectOptions } from '../../dynamic-form/models/value.key.html.select.options';
import { EntityLimit } from '../../entities/entity.limit';
import { EntityLimitService } from '../service/entity.limit.service';
import { LimitKeyDefinition } from '../model/limit.key.definition';
import { ProposeChangeEntityWithEntity } from '../../proposechange/model/propose.change.entity.whit.entity';
import { BaseSettings } from '../../base.settings';

/**
 * Creates or changes a single limit.
 *
 * The limit key is picked as a whole and posted back as its key id, never field by field: choosing it fixes the limit
 * type, the element entity and the two scopes, which are shown read-only. What stays editable is who the limit applies
 * to, its value and its expiry.
 *
 * The same dialog serves both hosts. Opened from the user administration it is user-scoped, so the role selection is
 * absent and the proposal-aware submit button of a limit increase request applies. Opened from the limit
 * administration it offers the role selection instead.
 */
@Component({
  selector: 'entity-limit-edit',
  template: ` <p-dialog
    header="{{ 'ENTITY_LIMIT_INFO_CLASS' | translate }}"
    [visible]="visibleDialog"
    [style]="{ width: '560px' }"
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
export class EntityLimitEditComponent extends SimpleEntityEditBase<EntityLimit> implements OnInit {
  /** When set the dialog is user-scoped: no role selection and the row is written for this user. */
  @Input() idUser: number;
  @Input() existingEntityLimit: EntityLimit;
  @Input() proposeChangeEntityWithEntity: ProposeChangeEntityWithEntity;
  /**
   * Every configured limit, so that a key and role combination which already exists is not offered a second time.
   * Only the limit administration passes them; the user-scoped host leaves it empty, because there the backend
   * already removes the keys the user holds a row for.
   */
  @Input() existingEntityLimits: EntityLimit[] = [];

  private limitKeyDefinitions: LimitKeyDefinition[] = [];
  private roleOptions: ValueKeyHtmlSelectOptions[] = [];
  /** Scopes already configured per limit key: the role id as text, the empty text for the default row. */
  private usedScopesByKeyId = new Map<string, Set<string>>();

  constructor(
    translateService: TranslateService,
    gps: GlobalparameterService,
    messageToastService: MessageToastService,
    entityLimitService: EntityLimitService
  ) {
    super(
      HelpIds.HELP_ENTITY_LIMIT,
      AppHelper.toUpperCaseWithUnderscore(BaseSettings.ENTITY_LIMIT),
      translateService,
      gps,
      messageToastService,
      entityLimitService
    );
  }

  ngOnInit(): void {
    this.formConfig = AppHelper.getDefaultFormConfig(this.gps, 6, this.helpLink.bind(this));
    this.config = [
      // A dropdown rather than a plain select: its options carry the data scope icon, and the derived daily keys
      // cover every entity, so the list needs the search box the native select had through its type ahead.
      DynamicFieldHelper.createFieldDropdownString('keyId', 'ENTITY_NAME', true, { filter: true }),
      // Derived from the picked key and never edited directly; shown so the effect of the choice is visible. Their
      // names must stay free of any property of the entity, because the form is filled from the entity once more
      // after showReadableKeyParts() and the raw value would win.
      DynamicFieldHelper.createFieldInputStringHeqF('limitTypeReadable', 40, false),
      DynamicFieldHelper.createFieldInputStringHeqF('scopeReadable', 60, false),
      ...(this.idUser == null ? [DynamicFieldHelper.createFieldSelectNumberHeqF('idRole', false)] : []),
      DynamicFieldHelper.createFieldMinMaxNumberHeqF(DataType.NumericInteger, 'limitValue', true, 1, 1000000),
      DynamicFieldHelper.createFieldPcalendarHeqF(DataType.DateNumeric, 'validUntil', false),
      ...AuditHelper.getFullNoteRequestInputDefinition(this.closeDialog, this, true)
    ];
    this.configObject = TranslateHelper.prepareFieldsAndErrors(this.translateService, this.config);
  }

  protected override initialize(): void {
    this.configObject.limitTypeReadable.formControl.disable();
    this.configObject.scopeReadable.formControl.disable();

    // The roles are awaited together with the keys, because the offered keys depend on which of their scopes are
    // still free and the role selection is filled from the picked key.
    const entityLimitService = <EntityLimitService>this.serviceEntityUpdate;
    combineLatest([
      entityLimitService.getLimitKeyDefinitions(
        this.idUser,
        this.existingEntityLimit ? this.existingEntityLimit.idEntityLimit : undefined
      ),
      this.idUser == null ? entityLimitService.getRoles() : of<ValueKeyHtmlSelectOptions[]>([])
    ]).subscribe(([limitKeyDefinitions, roles]: [LimitKeyDefinition[], ValueKeyHtmlSelectOptions[]]) => {
      this.limitKeyDefinitions = limitKeyDefinitions;
      this.roleOptions = roles;
      this.usedScopesByKeyId = this.collectUsedScopes();
      this.configObject.keyId.groupItem = this.toKeyOptions(limitKeyDefinitions);
      this.configObject.keyId.formControl.valueChanges.subscribe((keyId: string) => this.applyPickedKey(keyId));
      // Fills the role selection while no key is picked yet and, for an existing row, for its fixed key, because a
      // disabled control emits no value change.
      this.applyRoleOptions(this.existingEntityLimit?.keyId);

      if (this.existingEntityLimit) {
        // The key identifies the row, so it stays fixed once the row exists.
        this.configObject.keyId.formControl.disable();
        this.form.transferBusinessObjectToForm(this.existingEntityLimit);
        this.showReadableKeyParts(
          this.existingEntityLimit.limitTypeKey,
          this.existingEntityLimit.ownerScopeKey,
          this.existingEntityLimit.countScopeKey,
          this.existingEntityLimit.relationEntityName
        );
      }
      AuditHelper.transferToFormAndChangeButtonForProposaleEdit(
        this.translateService,
        this.gps,
        this.proposeChangeEntityWithEntity
          ? this.proposeChangeEntityWithEntity.proposedEntity
          : this.existingEntityLimit,
        this.form,
        this.configObject,
        this.proposeChangeEntityWithEntity
      );
      this.configObject.keyId.baseInputComponent.focus();
    });
  }

  /**
   * The scopes already configured per limit key. The row being edited is left out, otherwise its own scope would be
   * missing from the offer. Rows of a single user are ignored: this dialog either administers the limits of one user,
   * where the backend already removes the keys that user holds, or it administers the roles, which never writes a
   * user row.
   *
   * @return the used scopes per key id, a role id as text and the empty text for the default row
   */
  private collectUsedScopes(): Map<string, Set<string>> {
    const usedScopesByKeyId = new Map<string, Set<string>>();
    (this.existingEntityLimits ?? [])
      .filter(
        (entityLimit) =>
          entityLimit.idUser == null && entityLimit.idEntityLimit !== this.existingEntityLimit?.idEntityLimit
      )
      .forEach((entityLimit) => {
        const usedScopes = usedScopesByKeyId.get(entityLimit.keyId) ?? new Set<string>();
        usedScopes.add(entityLimit.idRole == null ? '' : String(entityLimit.idRole));
        usedScopesByKeyId.set(entityLimit.keyId, usedScopes);
      });
    return usedScopesByKeyId;
  }

  /**
   * Builds the picker options, sorted by their readable label. A key is more than its entity: the two watchlist keys
   * differ only in their counting scope, so the label has to carry the element entity and the scopes as well. The
   * leading icon repeats what the data scope column of the administration table shows, so that it is visible while
   * choosing and not only afterwards.
   */
  private toKeyOptions(limitKeyDefinitions: LimitKeyDefinition[]): GroupItem[] {
    return limitKeyDefinitions
      .filter((definition) => !this.isKeyExhausted(definition.keyId))
      .map((definition) => {
        const parts: string[] = [this.translateService.instant(definition.labelKey)];
        if (definition.relationEntityName) {
          parts.push(this.translateService.instant(AppHelper.toUpperCaseWithUnderscore(definition.relationEntityName)));
        }
        const qualifiers: string[] = [this.translateService.instant('LIMIT_TYPE_' + definition.limitType)];
        if (definition.countScope) {
          qualifiers.push(this.translateService.instant('COUNT_SCOPE_' + definition.countScope));
        }
        if (definition.ownerScope) {
          qualifiers.push(this.translateService.instant('OWNER_SCOPE_' + definition.ownerScope));
        }
        const label = `${parts.join(' / ')} (${qualifiers.join(', ')})`;
        return new GroupItem(definition.keyId, label, label, null, this.toDataScopeIcon(definition.sharedData));
      })
      .sort((a, b) =>
        a.optionsText.toLowerCase() < b.optionsText.toLowerCase()
          ? -1
          : a.optionsText.toLowerCase() > b.optionsText.toLowerCase()
            ? 1
            : 0
      );
  }

  /** The icons of the data scope column: a globe for data shared between all tenants, a lock for private data. */
  private toDataScopeIcon(sharedData: boolean): string {
    return sharedData == null ? null : sharedData ? 'fa fa-globe' : 'fa fa-lock';
  }

  /**
   * Whether every scope of a key is taken, that is the default row and one row per role. Such a key must not be
   * offered any more, because choosing it would leave the role selection without a single entry.
   */
  private isKeyExhausted(keyId: string): boolean {
    if (this.idUser != null) {
      return false;
    }
    const usedScopes = this.usedScopesByKeyId.get(keyId);
    return (
      usedScopes != null && usedScopes.has('') && this.roleOptions.every((role) => usedScopes.has(String(role.key)))
    );
  }

  /**
   * Narrows the role selection to the scopes still free for the given key. A key holds at most one row per role plus
   * one default row, so an already configured combination is withheld here instead of failing on save. The empty
   * entry stands for the default row and disappears as soon as that row exists.
   */
  private applyRoleOptions(keyId: string): void {
    if (this.idUser != null) {
      return;
    }
    const usedScopes = this.usedScopesByKeyId.get(keyId) ?? new Set<string>();
    this.configObject.idRole.valueKeyHtmlOptions = SelectOptionsHelper.translateExistingValueKeyHtmlSelectOptions(
      this.translateService,
      this.roleOptions.filter((role) => !usedScopes.has(String(role.key))),
      !usedScopes.has('')
    );
  }

  /**
   * Reflects the picked key in the read-only fields and narrows the value validation to the rule of that key, so an
   * administrator sees the same bounds the backend enforces.
   */
  private applyPickedKey(keyId: string): void {
    const definition = this.limitKeyDefinitions.find((d) => d.keyId === keyId);
    if (!definition) {
      return;
    }
    this.applyRoleOptions(keyId);
    this.showReadableKeyParts(
      'LIMIT_TYPE_' + definition.limitType,
      definition.ownerScope ? 'OWNER_SCOPE_' + definition.ownerScope : null,
      definition.countScope ? 'COUNT_SCOPE_' + definition.countScope : null,
      definition.relationEntityName
    );
    if (
      this.existingEntityLimit == null &&
      definition.defaultValue != null &&
      this.configObject.limitValue.formControl.value == null
    ) {
      this.configObject.limitValue.formControl.setValue(definition.defaultValue);
    }
  }

  private showReadableKeyParts(
    limitTypeKey: string,
    ownerScopeKey: string,
    countScopeKey: string,
    relationEntityName: string
  ): void {
    this.configObject.limitTypeReadable.formControl.setValue(
      limitTypeKey ? this.translateService.instant(limitTypeKey) : ''
    );
    const scopes: string[] = [];
    if (ownerScopeKey) {
      scopes.push(this.translateService.instant(ownerScopeKey));
    }
    if (countScopeKey) {
      scopes.push(this.translateService.instant(countScopeKey));
    }
    if (relationEntityName) {
      scopes.push(this.translateService.instant(AppHelper.toUpperCaseWithUnderscore(relationEntityName)));
    }
    this.configObject.scopeReadable.formControl.setValue(scopes.join(', '));
  }

  protected override getNewOrExistingInstanceBeforeSave(value: { [name: string]: any }): EntityLimit {
    const entityLimit = new EntityLimit();
    entityLimit.idUser = this.idUser;
    this.copyFormToPublicBusinessObject(entityLimit, this.existingEntityLimit, this.proposeChangeEntityWithEntity);
    // Never post the readable fields or the five derived key parts: the backend parses and validates keyId, then
    // writes those columns from the resulting LimitKey.
    [
      'limitTypeReadable',
      'scopeReadable',
      'limitType',
      'entityName',
      'relationEntityName',
      'countScope',
      'ownerScope'
    ].forEach((property) => delete (entityLimit as any)[property]);
    if (this.existingEntityLimit) {
      entityLimit.keyId = this.existingEntityLimit.keyId;
    }
    return entityLimit;
  }
}
