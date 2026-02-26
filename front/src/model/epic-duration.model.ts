export interface EpicSprintDelivery {
  sprintId: number;
  sprintName: string;
}

export interface EpicChildTicket {
  ticketKey: string;
  ticketUrl: string;
  summary: string;
  timeSpentHours: number;
  developers: string[];
}

export interface EpicDeliveryOverview {
  epicKey: string;
  epicSummary: string;
  status: string;
  versionNames: string[];
  sprintDeliveries: EpicSprintDelivery[];
  developers: string[];
  childTickets: EpicChildTicket[];
}
