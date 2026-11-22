.PHONY: help dev dev-down test verify build clean seed smoke adr-new pattern-check

help:
	@echo "JokkoRM — commandes courantes"
	@echo ""
	@echo "  make dev             Lève la stack locale (Postgres + Keycloak + Mailpit)"
	@echo "  make dev-down        Arrête et supprime la stack locale"
	@echo "  make test            Lance tous les tests Gradle"
	@echo "  make verify          Build + test + checks"
	@echo "  make build           Build sans tests"
	@echo "  make clean           gradle clean"
	@echo "  make seed            Seed le tenant demo (à venir — phase 2)"
	@echo "  make smoke           Appelle /actuator/health et affiche la réponse"
	@echo "  make pattern-check   Vérifie que les docs pattern sont présentes (CI)"

dev:
	docker compose -f deploy/compose.dev.yml up -d
	@echo ""
	@echo "Stack prête :"
	@echo "  Postgres  : localhost:5490   (user jokkorm / pwd jokkorm / db jokkorm)"
	@echo "  Keycloak  : http://localhost:8085  (admin / admin — realm 'jokkorm' à créer)"
	@echo "  Mailpit   : http://localhost:8025"

dev-down:
	docker compose -f deploy/compose.dev.yml down

test:
	./gradlew test

verify:
	./gradlew build

build:
	./gradlew assemble -x test

clean:
	./gradlew clean

smoke:
	@curl -fsSL http://localhost:8091/actuator/health | python3 -m json.tool || echo "Backend injoignable sur :8091"

pattern-check:
	@test -f docs/pattern/README.md && \
	 test -f docs/pattern/01-une-archive-deux-gardiens.md && \
	 test -f docs/pattern/02-regle-un-seul-proprietaire.md && \
	 test -f docs/pattern/03-qui-detient-quoi.md && \
	 test -f docs/pattern/04-dossier-hybride.md && \
	 test -f docs/pattern/05-cycle-vie-papier.md && \
	 echo "Pattern docs OK" || (echo "Pattern docs INCOMPLÈTES" && exit 1)

adr-new:
	@read -p "Numéro ADR (4 chiffres, ex 0005) : " n ; \
	 read -p "Slug kebab (ex retention-calendar) : " s ; \
	 file="docs/adr/$${n}-$${s}.md" ; \
	 touch $$file && echo "# ADR $$n — ..." > $$file && echo "Créé : $$file"
