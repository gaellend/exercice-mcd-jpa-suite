package pharmacie.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;
import org.springframework.transaction.annotation.Transactional;

import jakarta.validation.constraints.Positive;
import lombok.extern.slf4j.Slf4j;
import pharmacie.dao.CommandeRepository;
import pharmacie.dao.DispensaireRepository;
import pharmacie.dao.LigneRepository;
import pharmacie.dao.MedicamentRepository;
import pharmacie.entity.Commande;
import pharmacie.entity.Ligne;

@Slf4j
@Service
@Validated
public class CommandeService {
    private final CommandeRepository commandeDao;
    private final DispensaireRepository dispensaireDao;
    private final LigneRepository ligneDao;
    private final MedicamentRepository medicamentDao;

    public CommandeService(CommandeRepository commandeDao, DispensaireRepository dispensaireDao, LigneRepository ligneDao, MedicamentRepository medicamentDao) {
        this.commandeDao = commandeDao;
        this.dispensaireDao = dispensaireDao;
        this.ligneDao = ligneDao;
        this.medicamentDao = medicamentDao;
    }

    @Transactional
    public Commande creerCommande(@NonNull String dispensaireCode) {
        log.info("Service : Création d'une commande pour {}", dispensaireCode);
        var dispensaire = dispensaireDao.findById(dispensaireCode).orElseThrow();
        var nouvelleCommande = new Commande(dispensaire);
        nouvelleCommande.setAdresseLivraison(dispensaire.getAdresse());

        var nbArticles = dispensaireDao.nombreArticlesCommandesPar(dispensaireCode);
        if (nbArticles > 100) {
            nouvelleCommande.setRemise(new BigDecimal("0.15"));
        }

        commandeDao.save(nouvelleCommande);
        return nouvelleCommande;
    }

    @Transactional
    public Ligne ajouterLigne(int commandeNum, int medicamentRef, @Positive int quantite) {
        log.info("Service : Ajout de {} unités du médicament {} à la commande {}", quantite, medicamentRef, commandeNum);

        // Vérification de l'existence et du statut de la commande
        var commande = commandeDao.findById(commandeNum).orElseThrow();
        if (commande.getEnvoyeele() != null) {
            throw new IllegalStateException("La commande est déjà envoyée");
        }

        // Vérification du médicament et de sa disponibilité
        var medicament = medicamentDao.findById(medicamentRef).orElseThrow();
        if (medicament.isIndisponible()) {
            throw new IllegalStateException("Le médicament est indisponible");
        }

        // Règle métier : Stock >= total commandé
        if (medicament.getUnitesEnStock() < (medicament.getUnitesCommandees() + quantite)) {
            throw new IllegalStateException("Stock insuffisant");
        }

        // Gestion de l'unicité de la ligne (ajout ou mise à jour)
        var ligne = ligneDao.findByCommandeAndMedicament(commande, medicament)
            .orElse(new Ligne(commande, medicament, 0));

        if (ligne.getId() == null) {
            commande.getLignes().add(ligne);
        }

        ligne.setQuantite(ligne.getQuantite() + quantite);
        medicament.setUnitesCommandees(medicament.getUnitesCommandees() + quantite);

        medicamentDao.save(medicament);
        return ligneDao.save(ligne);
    }

    @Transactional
    public void supprimerLigne(int id) {
        log.info("Service : Suppression de la ligne {}", id);
        var ligne = ligneDao.findById(id).orElseThrow();

        // Vérification que la commande n'est pas envoyée
        if (ligne.getCommande().getEnvoyeele() != null) {
            throw new IllegalStateException("Commande déjà envoyée");
        }

        // Mise à jour des unités commandées
        var medicament = ligne.getMedicament();
        medicament.setUnitesCommandees(medicament.getUnitesCommandees() - ligne.getQuantite());

        medicamentDao.save(medicament);
        ligne.getCommande().getLignes().remove(ligne); // Déclenche l'orphanRemoval
    }

    @Transactional
    public Commande enregistreExpedition(int commandeNum) {
        log.info("Service : Expédition de la commande {}", commandeNum);
        var commande = commandeDao.findById(commandeNum).orElseThrow();

        if (commande.getEnvoyeele() != null) {
            throw new IllegalStateException("Commande déjà envoyée");
        }

        commande.setEnvoyeele(LocalDate.now());

        // Mise à jour des stocks pour chaque médicament
        for (Ligne ligne : commande.getLignes()) {
            var medicament = ligne.getMedicament();
            int qte = ligne.getQuantite();

            medicament.setUnitesEnStock(medicament.getUnitesEnStock() - qte);
            medicament.setUnitesCommandees(medicament.getUnitesCommandees() - qte);

            medicamentDao.save(medicament);
        }

        return commandeDao.save(commande);
    }

    @Transactional
    public Commande getCommande(int commandeNum) {
        return commandeDao.findById(commandeNum).orElseThrow();
    }

    @Transactional
    public List<Commande> getCommandeEnCoursPour(String dispensaireCode) {
        return commandeDao.commandesEnCoursPour(dispensaireCode);
    }
}
