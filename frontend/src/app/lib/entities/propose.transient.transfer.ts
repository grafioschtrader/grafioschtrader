export abstract class ProposeTransientTransfer {
  idProposeRequest?: number;
  noteRequestOrReject: string | null = null;

  public getId(): number | null | undefined {
    return this.idProposeRequest;
  }
}
