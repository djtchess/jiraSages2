export interface EpicSprintDelivery {
  sprintId: number;
  sprintName: string;
}

export interface EpicDeliveryOverview {
  epicKey: string;
  epicSummary: string;
  status: string;
  versionNames: string[];
  sprintDeliveries: EpicSprintDelivery[];
}
