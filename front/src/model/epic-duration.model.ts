export interface EpicSprintDelivery {
  sprintId: number;
  sprintName: string;
}

export interface EpicChildTicket {
  ticketKey: string;
  ticketUrl: string;
  summary: string;
  status: string;
  storyPoints: number;
  timeSpentDays: number;
  developers: string[];
}

export interface EpicDeliveryOverview {
  epicKey: string;
  epicSummary: string;
  status: string;
  versionNames: string[];
  sprintDeliveries: EpicSprintDelivery[];
  developers: string[];
  totalStoryPoints: number;
  totalTimeSpentDays: number;
  epicVelocity: number;
  childTickets: EpicChildTicket[];
}
