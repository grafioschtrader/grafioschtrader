import { describe, expect, it } from 'vitest';
import { ProposeChangeField } from '../entities/propose.change.field';
import { ProposeUserTask } from '../entities/propose.user.task';
import { User } from '../entities/user';
import { UserTaskType } from '../types/user.task.type';
import { AuditHelper } from './audit.helper';

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
