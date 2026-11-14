<div align="center">
  <img src="app/src/main/res/drawable/galaxie_bus_logo.png" width="140" height="140" alt="Logo Galaxie Bus" />
  <h1>Galaxie Bus</h1>
  <p>
    Application Android temps réel pour le réseau de transport <strong>Idelis</strong> (Pau Béarn Pyrénées) :
    prochains départs en direct, widget dynamique sur l'écran d'accueil, infos trafic et horaires hors-ligne.
  </p>
</div>

---

## Fonctionnalités

- **Prochains passages en temps réel (SIRI / Web Idelis)** : affichage direct des lignes, directions, temps d'attente et retards ou avances.
- **Arrêts favoris & géolocalisation** : sélection rapide d'arrêt, tri des favoris et détection de l'arrêt le plus proche.
- **Widget écran d'accueil configurable** : suivi des prochains bus pour un arrêt choisi ou automatique selon la position, avec actualisation manuelle ou périodique.
- **Info trafic & notifications** : consultation des perturbations et alertes réseau, avec alertes en arrière-plan configurables.
- **Base GTFS locale** : fallback hors-ligne et synchronisation des données GTFS du réseau Idelis.
- **Mises à jour intégrées** : vérification et téléchargement des nouvelles versions depuis les releases GitHub.
- **Sauvegarde & personnalisation** : thèmes clair/sombre/système, choix de la couleur principale, et export/import des préférences en JSON.

## Compiler et exécuter localement

**Prérequis :** [Android Studio](https://developer.android.com/studio) (JDK 11+, Android SDK 36)

1. Cloner le dépôt :
   ```bash
   git clone https://github.com/kanooob/bus.git
   ```
2. Ouvrir le dossier du projet dans **Android Studio**.
3. Laisser Gradle synchroniser les dépendances.
4. *(Optionnel)* Si vous compilez en local en dehors de l'environnement AI Studio, vérifier la configuration de signature dans `app/build.gradle.kts` (`debugConfig` / `release`).
5. Lancer l'application sur un émulateur ou un appareil Android (`minSdk 24`).
