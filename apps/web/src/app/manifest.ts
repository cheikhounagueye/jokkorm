import type { MetadataRoute } from "next";

/**
 * Appli « Magasin » à poser sur l'écran d'accueil de l'iPhone (pattern 12). Elle s'ouvre sur
 * /terrain et parle à l'instance JokkoRM d'où elle a été installée.
 */
export default function manifest(): MetadataRoute.Manifest {
  return {
    name: "JokkoRM — Magasin",
    short_name: "Magasin",
    description: "Gestes d'archives au scan : arrivée, placer, retirer.",
    lang: "fr",
    start_url: "/terrain",
    scope: "/",
    display: "standalone",
    orientation: "portrait",
    background_color: "#14181f",
    theme_color: "#14181f",
    icons: [
      { src: "/icons/magasin-192.png", sizes: "192x192", type: "image/png", purpose: "any" },
      { src: "/icons/magasin-512.png", sizes: "512x512", type: "image/png", purpose: "any" },
      { src: "/icons/magasin-512.png", sizes: "512x512", type: "image/png", purpose: "maskable" },
    ],
  };
}
