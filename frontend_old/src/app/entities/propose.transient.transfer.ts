export abstract class ProposeTransientTransfer {
  idProposeRequest: number;
  noteRequestOrReject: string = null;

  public getId() {
    return this.idProposeRequest;
  }
}
