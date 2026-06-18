/**
 * One person who can read the current owner's portfolio, returned by GET /tenant/shares. A 'GRANT' is a registered user
 * holding a read-only access grant (revoking only removes the grant); a 'VIEWER' is a pure read-only viewer login
 * whose home tenant is the owner's tenant (revoking deletes that login). Used to populate the shared-viewers table.
 */
export interface SharedViewerInfo {
  idUser: number;
  /** Login e-mail of the user that can read the owner's portfolio. */
  email: string;
  /** Kind of access: 'GRANT' (registered user with a read grant) or 'VIEWER' (pure read-only login). */
  viewerType: string;
}
