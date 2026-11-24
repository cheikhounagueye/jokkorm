// Copie le moteur de lecture des codes-barres (zxing-cpp compilé en WebAssembly) dans
// public/vendor/zxing/ : le mode magasin doit marcher sur un réseau local sans Internet,
// donc le .wasm est servi par JokkoRM lui-même et jamais téléchargé depuis un CDN.
import { copyFileSync, existsSync, mkdirSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const web = join(dirname(fileURLToPath(import.meta.url)), "..");
const source = join(web, "node_modules", "zxing-wasm", "dist", "reader", "zxing_reader.wasm");
const target = join(web, "public", "vendor", "zxing", "zxing_reader.wasm");

if (!existsSync(source)) {
  console.warn("[copy-zxing-wasm] zxing_reader.wasm introuvable : le lecteur de l'iPhone ne marchera pas.");
  process.exit(0);
}
mkdirSync(dirname(target), { recursive: true });
copyFileSync(source, target);
console.log("[copy-zxing-wasm] public/vendor/zxing/zxing_reader.wasm à jour");
