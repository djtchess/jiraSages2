package fr.agile.service;

import java.util.List;

import org.springframework.stereotype.Component;

import fr.agile.entities.JoursFeries;
import fr.agile.repository.JoursFeriesRepository;

@Component
public class JoursFeriesService{

    private final JoursFeriesRepository joursFeriesRepository;

    public JoursFeriesService(JoursFeriesRepository joursFeriesRepository) {
        this.joursFeriesRepository = joursFeriesRepository;
    }

    public List<JoursFeries> getAll() {
        return joursFeriesRepository.findAll();
    }
}
