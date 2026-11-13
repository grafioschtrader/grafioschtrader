import { describe, expect, it } from 'vitest';
import { EntityLimit } from '../../entities/entity.limit';
import { ProposeChangeField } from '../../entities/propose.change.field';
import { ProposeUserTask } from '../../entities/propose.user.task';
import { UserTaskType } from '../../types/user.task.type';
import { dayCudKeyId, projectLimitRequest, readLimitRequest } from './limit.request.projection';

/** The change fields the backend stores for a limit request, as they arrive from the user administration. */
function limitRequest(entity: string, dayLimit: number, untilDate: string): ProposeUserTask {
  const task = new ProposeUserTask(UserTaskType.LIMIT_CUD_CHANGE);
  task.idProposeRequest = 133;
  task.addProposeChangeField(new ProposeChangeField('entity', entity));
  task.addProposeChangeField(new ProposeChangeField('untilDate', untilDate));
  task.addProposeChangeField(new ProposeChangeField('dayLimit', dayLimit));
  return task;
}

describe('limit request projection', () => {
  it('shows the requested entity, limit and expiry under the properties the limit dialog binds', () => {
    const task = limitRequest('ImportTransactionHead', 50, '2026-11-11');
    const request = readLimitRequest(task);
    expect(request).toEqual({ entityName: 'ImportTransactionHead', dayLimit: 50, untilDate: '2026-11-11' });

    const projection = projectLimitRequest(new EntityLimit(), task, request!);
    expect(projection.proposedEntity.keyId).toBe('DAY_CUD|ImportTransactionHead|||');
    expect(projection.proposedEntity.limitValue).toBe(50);
    expect(projection.proposedEntity.validUntil).toBe('2026-11-11');
    expect(projection.proposedEntity.entity).toBeUndefined();
    expect(projection.proposedEntity.dayLimit).toBeUndefined();
    expect(projection.proposedEntity.untilDate).toBeUndefined();
    expect(projection.proposeChangeEntity.idProposeRequest).toBe(133);
  });

  it('replaces the values of an existing limit by the requested ones and leaves the limit itself unchanged', () => {
    const existing = new EntityLimit();
    existing.keyId = dayCudKeyId('Assetclass');
    existing.limitValue = 12;
    existing.validUntil = '2026-01-01' as unknown as Date;

    const task = limitRequest('Assetclass', 30, '2026-12-31');
    const projection = projectLimitRequest(existing, task, readLimitRequest(task)!);

    expect(projection.proposedEntity.limitValue).toBe(30);
    expect(projection.proposedEntity.validUntil).toBe('2026-12-31');
    expect(existing.limitValue).toBe(12);
    expect(existing.validUntil).toBe('2026-01-01');
  });

  it('reads no request from a proposal that names no entity', () => {
    expect(readLimitRequest(new ProposeUserTask(UserTaskType.LIMIT_CUD_CHANGE))).toBeNull();
  });
});
