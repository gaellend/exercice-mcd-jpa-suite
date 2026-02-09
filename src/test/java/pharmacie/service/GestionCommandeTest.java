package pharmacie.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import pharmacie.dao.MedicamentRepository;
import pharmacie.entity.Commande;
import pharmacie.entity.Ligne;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class GestionCommandeServiceTest {

    @Autowired
    private CommandeService service;

    @Autowired
    private MedicamentRepository medicamentDao;

    @Test
    void testAjouterLigneMetAJourLesUnitesCommandees() {
        // Utilisation des IDs de test_data.sql
        int commandeNum = 99998; // Commande en cours
        int medicamentRef = 98;  // Medicament avec 20 unités déjà commandées
        int quantiteAAjouter = 5;

        int unitesAvant = medicamentDao.findById(medicamentRef).orElseThrow().getUnitesCommandees();

        service.ajouterLigne(commandeNum, medicamentRef, quantiteAAjouter);

        int unitesApres = medicamentDao.findById(medicamentRef).orElseThrow().getUnitesCommandees();
        assertEquals(unitesAvant + quantiteAAjouter, unitesApres, "Les unités commandées doivent augmenter de 5");
    }

    @Test
    void testAjouterLigneImpossibleSiStockInsuffisant() {
        // Medicament 98 : Stock = 26, Déjà commandé = 20 -> Reste 6 places
        int commandeNum = 99998;
        int medicamentRef = 98;
        int quantiteTropElevee = 10; // 20 + 10 = 30, ce qui dépasse le stock de 26

        assertThrows(IllegalStateException.class, () -> {
            service.ajouterLigne(commandeNum, medicamentRef, quantiteTropElevee);
        }, "On doit refuser car 20 (déjà cmd) + 10 > 26 (stock)");
    }

    @Test
    void testImpossibleDajouterLigneSurCommandeDejaExpediee() {
        // Commande 99999 est déjà envoyée dans test_data.sql
        int commandeExpedieeNum = 99999;

        assertThrows(IllegalStateException.class, () -> {
            service.ajouterLigne(commandeExpedieeNum, 98, 1);
        }, "L'ajout sur une commande expédiée doit échouer");
    }

    @Test
    void testEnregistreExpeditionMetAJourLesStocksEtLaDate() {
        int commandeNum = 99998;
        Commande cmd = service.getCommande(commandeNum);
        Ligne ligne = cmd.getLignes().get(0); // Médicament 98, quantité 16
        int medicamentRef = ligne.getMedicament().getReference();
        int qteLigne = ligne.getQuantite();

        int stockAvant = ligne.getMedicament().getUnitesEnStock();
        int cmdAvant = ligne.getMedicament().getUnitesCommandees();

        service.enregistreExpedition(commandeNum);

        var medicamentApres = medicamentDao.findById(medicamentRef).orElseThrow();

        // Le stock physique doit diminuer
        assertEquals(stockAvant - qteLigne, medicamentApres.getUnitesEnStock());
        // Les unités "en commande" doivent être libérées
        assertEquals(cmdAvant - qteLigne, medicamentApres.getUnitesCommandees());
        // La date doit être mise à jour
        assertEquals(LocalDate.now(), service.getCommande(commandeNum).getEnvoyeele());
    }

    @Test
    void testSupprimerLigneMetAJourLesUnitesCommandees() {
        // Dans test_data.sql, la commande 99998 a une ligne avec le médicament 98 (quantité 16)
        int commandeNum = 99998;
        Commande cmd = service.getCommande(commandeNum);
        Ligne ligneASupprimer = cmd.getLignes().get(0);
        int ligneId = ligneASupprimer.getId();
        int medicamentRef = ligneASupprimer.getMedicament().getReference();
        int qteASupprimer = ligneASupprimer.getQuantite();

        int unitesAvant = medicamentDao.findById(medicamentRef).orElseThrow().getUnitesCommandees();

        service.supprimerLigne(ligneId);

        int unitesApres = medicamentDao.findById(medicamentRef).orElseThrow().getUnitesCommandees();

        // On vérifie que les unités commandées ont bien diminué
        assertEquals(unitesAvant - qteASupprimer, unitesApres, "Les unités commandées doivent diminuer après suppression");
        // On vérifie que la ligne n'existe plus dans la commande
        assertTrue(service.getCommande(commandeNum).getLignes().isEmpty(), "La commande ne doit plus avoir de lignes");
    }
}
