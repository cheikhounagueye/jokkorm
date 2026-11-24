#!/usr/bin/env bash
# Prépare le HTTPS local du mode magasin (iPhone sur le réseau local, sans Internet).
#
# Un iPhone n'ouvre la caméra d'une page web qu'en HTTPS avec un certificat de confiance.
# mkcert crée une autorité locale sur ce Mac, puis un certificat pour le nom réseau du Mac.
# L'iPhone doit ensuite faire confiance à cette autorité (fichier rootCA.pem, jamais la clé).
#
# Relancer ce script si l'adresse IP du Mac change (le nom en .local, lui, ne change pas).
set -euo pipefail
cd "$(dirname "$0")/.."

command -v brew >/dev/null || { echo "Homebrew est requis (https://brew.sh)."; exit 1; }
command -v mkcert >/dev/null || brew install mkcert

# Ajoute l'autorité locale au trousseau du Mac (mot de passe demandé la première fois).
mkcert -install

HOST="$(scutil --get LocalHostName).local"
IP="$(ipconfig getifaddr en0 || true)"
mkdir -p .certs
mkcert -cert-file .certs/lan-cert.pem -key-file .certs/lan-key.pem \
  "$HOST" localhost 127.0.0.1 ${IP:+"$IP"}

CA="$(mkcert -CAROOT)/rootCA.pem"
cat <<EOF

Certificat prêt pour : https://$HOST:3100${IP:+  et  https://$IP:3100}

Sur l'iPhone (une seule fois) :
  1. Envoyer ce fichier par AirDrop : $CA
     (le Finder s'ouvre dessus ; NE JAMAIS envoyer rootCA-key.pem, qui est à côté)
  2. Réglages › Profil téléchargé › Installer.
  3. Réglages › Général › Informations › Réglages de confiance des certificats
     › activer « mkcert … ». Sans ce dernier interrupteur, la caméra reste bloquée.

Ensuite, dans apps/web : npm run dev:lan
EOF
open -R "$CA"
