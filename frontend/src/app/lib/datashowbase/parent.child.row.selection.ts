export interface ParentChildRowSelection<T> {
  rowSelectionChanged(childEntityList: T[], childSelectedEntity: T);
}
