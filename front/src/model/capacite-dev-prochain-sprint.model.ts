export interface CapaciteDevProchainSprintDTO {
  idDevelopper: number;
  nom: string;
  prenom: string;
  capaciteNouveauSprint: number;
  resteAFaireSprintPrecedent: number;
  capaciteNette: number;
  ticketsRestantsKeys?: string[];
}
