package kaptainwutax.seedcrackerX.api;

/**
 * Nachbau der Schnittstelle von SeedCrackerX - Wort fuer Wort so, wie javap
 * sie in seedcrackerX-2.16.1.jar zeigt.
 *
 * Damit laesst sich dagegen uebersetzen, ohne die fremde JAR ins Repo zu
 * legen oder im Build herunterzuladen. Zur Laufzeit gilt ohnehin die echte
 * Schnittstelle aus SeedCrackerX; diese Datei wird beim Packen ausgeschlossen
 * (siehe build.gradle), damit nie zwei Fassungen derselben Klasse kursieren.
 *
 * Ist SeedCrackerX nicht installiert, wird dieser Einstiegspunkt schlicht nie
 * aufgerufen - GlowCube laeuft unveraendert weiter.
 */
public interface SeedCrackerAPI {
    void pushWorldSeed(long worldSeed);
}
