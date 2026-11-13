import { EntityLimit } from '../../entities/entity.limit';
import type { ProposeUserTask } from '../../entities/propose.user.task';
import { AuditHelper } from '../../helper/audit.helper';
import type { ProposeChangeEntityWithEntity } from '../../proposechange/model/propose.change.entity.whit.entity';

/**
 * What a user asked for in a request for a higher limit. The backend stores it with the field names of
 * `LimitCudChange` (`entity`, `dayLimit`, `untilDate`), which are not the properties of {@link EntityLimit} the limit
 * dialog binds.
 */
export interface LimitRequest {
  /** Class name of the entity the daily limit is requested for. */
  entityName: string;
  /** Requested number of changes per day. */
  dayLimit: number | null;
  /** Last day the requested limit applies, as `yyyy-MM-dd`. */
  untilDate: string | null;
}

/**
 * Key id of the daily change limit of an entity. A limit request always asks for this limit: one user, no count scope
 * and no element entity, so the three trailing parts of the key stay empty.
 *
 * @param entityName - Class name of the entity
 * @returns The key id in the five-part form the backend parses
 */
export function dayCudKeyId(entityName: string): string {
  return 'DAY_CUD|' + entityName + '|||';
}

/**
 * Reads the request out of the change fields of a limit proposal.
 *
 * @param proposeUserTask - The proposal of a user
 * @returns The request, or null when the proposal names no entity
 */
export function readLimitRequest(proposeUserTask: ProposeUserTask): LimitRequest | null {
  const valueOf = (field: string) =>
    proposeUserTask.proposeChangeFieldList.find((p) => p.field === field)?.valueDesarialized;
  const entityName = valueOf('entity');
  return entityName
    ? { entityName, dayLimit: valueOf('dayLimit') ?? null, untilDate: valueOf('untilDate') ?? null }
    : null;
}

/**
 * Pairs a limit with the request that wants to change it. The proposed side carries the requested values under the
 * property names of the limit dialog, so the administrator sees the requested entity, limit and expiry there. The
 * given limit is left as it is, apart from the id of the proposal that the generic conversion records on it.
 *
 * @param entityLimit - The limit the request applies to: an existing row, or the row standing in for a new one
 * @param proposeUserTask - The proposal that carries the request
 * @param request - The request read from that proposal
 * @returns The limit together with its proposed state
 */
export function projectLimitRequest(
  entityLimit: EntityLimit,
  proposeUserTask: ProposeUserTask,
  request: LimitRequest
): ProposeChangeEntityWithEntity {
  const projection = AuditHelper.convertToProposeChangeEntityWithEntity(entityLimit, proposeUserTask);
  for (const requestField of ['entity', 'dayLimit', 'untilDate']) {
    delete projection.proposedEntity[requestField];
  }
  projection.proposedEntity.keyId = dayCudKeyId(request.entityName);
  projection.proposedEntity.limitValue = request.dayLimit;
  projection.proposedEntity.validUntil = request.untilDate;
  return projection;
}
