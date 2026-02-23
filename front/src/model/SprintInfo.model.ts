import { BurnupData } from "../service/burnup.service";

export interface SprintInfo {
  id: number;
  name: string;
  state: string;
  startDate?: string;
  endDate?: string;
  completeDate?: string;
  velocity: number;
  velocityStart: number;
  originBoardId?: string;
  burnupData: BurnupData;
  sprintCommitInfo: SprintCommitInfo;
  sprintKpiInfo: SprintKpiInfo;
}

export interface Ticket {
  ticketKey: string;
  url: string;
  assignee: string;
  status: string;
  storyPoints: number;
  storyPointsRealiseAvantSprint: number;
  avancement: number;
  engagementSprint: string;
  type: string;
  versionCorrigee: string;
  createdDate: string;
  sprintIds: string[];
  devTermineAvantSprint: boolean;
}

export interface SprintCommitInfo {
  committedAtStart: Ticket[];
  addedDuring: Ticket[];
  removedDuring: Ticket[];
}

/** Statistiques par type de ticket */
export interface CountersInfo {
  nbTickets: number;
  nbStoryPoints: number;
}

export interface SprintKpiInfo {
  totalTickets: number;
  committedAtStart: number;
  committedAndDone: number;
  addedDuring: number;
  removedDuring: number;
  doneTickets: number;
  devDoneBeforeSprint: number;
  engagementRespectePourcent: number;
  ajoutsNonPrevusPourcent: number;
  nonTerminesEngagesPourcent: number;
  succesGlobalPourcent: number;
  pointsCommited: number;
  pointsAdded: number;
  pointsRemoved: number;
  typeCountCommitted: Record<string, CountersInfo>;
  typeCountAdded: Record<string, CountersInfo>;
  typeCountAll: Record<string, CountersInfo>;
}
