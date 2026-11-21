// Root build file. Subprojects apply convention plugins from buildSrc.
//
// JokkoRM (Records Management) — module isolé d'archivage physique qui se
// branche sur une GED/SAE maître (Maarch RM, Alfresco, Nuxeo, SI custom)
// via le SaeMasterPort (ADR-0003). Pattern "une seule archive, deux gardiens".

tasks.register("printModules") {
    group = "help"
    description = "Lists all modules in the build."
    doLast {
        rootProject.allprojects
            .filter { it != rootProject }
            .map { it.path }
            .sorted()
            .forEach { println(it) }
    }
}
