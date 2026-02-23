export class Resource {
    constructor(
      public idResource: number,
      public nomResource: string,
      public prenomResource: string,
      public dateDebut: Date,
      public dateFin: Date,
      public events: Event[] = [] 
    ) {}
  }

export class Event {
    constructor(
      public idEvent?: number,
      public libelleEvent?: string,
      public type?: string,
      public dateDebutEvent?: Date,
      public dateFinEvent?: Date,
      public isMatin?: Boolean,
      public isApresMidi?: Boolean,
      public isJournee?: Boolean,
      public developper?: Resource
    ) {}
  }
  

  export class Month {
    constructor(
      public numero: number,
      public libelle: string
     ) {}
  }

  export class Holiday {
    constructor(public date:Date, public name : String) {};
  }


