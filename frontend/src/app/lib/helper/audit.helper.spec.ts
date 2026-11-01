import { describe, expect, it } from 'vitest';
import { BaseSettings } from '../base.settings';
import { DataType } from '../dynamic-form/models/data.type';
import { FieldConfig } from '../dynamic-form/models/field.config';
import { InputType } from '../dynamic-form/models/input.type';
import { ProposeChangeField } from '../entities/propose.change.field';
import { ProposeUserTask } from '../entities/propose.user.task';
import { User } from '../entities/user';
import { UserTaskType } from '../types/user.task.type';
import type { ProposeChangeEntityWithEntity } from '../proposechange/model/propose.change.entity.whit.entity';
import type { GlobalparameterService } from '../services/globalparameter.service';
import { AuditEditMode, AuditHelper } from './audit.helper';

describe('proposal projection', () => {
  it('applies explicit null and zero changes to the proposal without changing the original data', () => {
    const user = new User();
    user.nickname = 'Existing nickname';
    user.limitRequestExceedCount = 10;
    const task = new ProposeUserTask(UserTaskType.LIMIT_CUD_CHANGE);
    task.idProposeRequest = 42;
    task.addProposeChangeField(new ProposeChangeField('nickname', null));
    task.addProposeChangeField(new ProposeChangeField('limitRequestExceedCount', 0));

    expect(AuditHelper.convertToProposeChangeEntityWithEntity(user, null)).toBeUndefined();
    const projection = AuditHelper.convertToProposeChangeEntityWithEntity(user, task);
    expect(projection.proposedEntity.nickname).toBeNull();
    expect(projection.proposedEntity.limitRequestExceedCount).toBe(0);
    expect(user.nickname).toBe('Existing nickname');
    expect(user.limitRequestExceedCount).toBe(10);
    expect(user.idProposeRequest).toBe(42);
  });
});

/** Session of user 7 with the given roles; only what the rights check reads. */
function session(...roles: string[]): GlobalparameterService {
  const gps = {
    getIdUser: () => 7,
    hasRole: (role: string) => roles.includes(role),
    isEntityCreatedByUser: (entity: User) => entity.createdBy === 7
  };
  return gps as unknown as GlobalparameterService;
}

function userCreatedBy(idUser: number): User {
  const user = new User();
  user.createdBy = idUser;
  return user;
}

describe('audit edit mode', () => {
  const proposal = AuditHelper.convertToProposeChangeEntityWithEntity(
    userCreatedBy(3),
    new ProposeUserTask(UserTaskType.LIMIT_CUD_CHANGE)
  );

  it('lets a user without rights only propose a change, also while a change request is pending', () => {
    expect(AuditHelper.auditEditMode(session(), userCreatedBy(3), undefined)).toBe(AuditEditMode.CHANGE_REQUEST);
    expect(AuditHelper.auditEditMode(session(), userCreatedBy(3), proposal)).toBe(AuditEditMode.CHANGE_REQUEST);
  });

  it('lets the creator or a privileged user review a pending change request, otherwise edit directly', () => {
    expect(AuditHelper.auditEditMode(session(), userCreatedBy(7), proposal)).toBe(AuditEditMode.PROPOSAL_REVIEW);
    expect(AuditHelper.auditEditMode(session(), userCreatedBy(7), null)).toBe(AuditEditMode.DIRECT_EDIT);
    expect(AuditHelper.auditEditMode(session(BaseSettings.ROLE_ALL_EDIT), userCreatedBy(3), null)).toBe(
      AuditEditMode.DIRECT_EDIT
    );
  });

  it('edits a new entity directly', () => {
    expect(AuditHelper.auditEditMode(session(), null, proposal)).toBe(AuditEditMode.DIRECT_EDIT);
  });
});

describe('old value of a reviewed proposal', () => {
  const select: FieldConfig = {
    field: 'mostPrivilegedRole',
    inputType: InputType.Select,
    dataType: DataType.String,
    valueKeyHtmlOptions: [
      { key: 'ROLE_USER', value: 'User' },
      { key: 'ROLE_ADMIN', value: 'Administrator' }
    ]
  };
  const text: FieldConfig = { field: 'nickname', inputType: InputType.Input, dataType: DataType.String };

  function proposalOf(oldNickname: string | null, change: Partial<User>): ProposeChangeEntityWithEntity {
    const user = new User();
    user.nickname = oldNickname;
    user.mostPrivilegedRole = 'ROLE_USER';
    return {
      entity: user,
      proposedEntity: Object.assign(new User(), user, change),
      proposeChangeEntity: new ProposeUserTask(UserTaskType.LIMIT_CUD_CHANGE)
    };
  }

  it('shows the label of the old option of a select and nothing for an unchanged field', () => {
    const pcewe = proposalOf('Old', { mostPrivilegedRole: 'ROLE_ADMIN' });
    expect(AuditHelper.oldValueLabel(select, pcewe)).toBe('User');
    expect(AuditHelper.oldValueLabel(text, pcewe)).toBeUndefined();
  });

  it('marks a field whose old value was empty', () => {
    expect(AuditHelper.oldValueLabel(text, proposalOf(null, { nickname: 'New' }))).toBe('');
  });
});
