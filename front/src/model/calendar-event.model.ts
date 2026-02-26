export interface CalendarEvent {
  id: number;
  dateDebutEvent: Date;
  dateFinEvent: Date;
  isMatin: boolean;
  isApresMidi: boolean;
  isJournee: boolean;
}

export interface SaveCalendarEventPayload {
  dateDebutEvent: string;
  dateFinEvent: string;
  isMatin: boolean;
  isApresMidi: boolean;
  isJournee: boolean;
  developper: {
    idResource: number;
  };
}

export interface CalendarEventDto {
  id?: number;
  idEvent?: number;
  dateDebutEvent?: string | Date;
  dateFinEvent?: string | Date;
  isMatin?: boolean;
  isApresMidi?: boolean;
  isJournee?: boolean;
}
