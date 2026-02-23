export interface EpicTeamSprintDelivery {
  teamName: string;
  sprintId: number;
  sprintName: string;
}

export interface EpicDeliveryOverview {
  epicKey: string;
  epicSummary: string;
  status: string;
  versionNames: string[];
  sprintDeliveries: EpicTeamSprintDelivery[];
}
