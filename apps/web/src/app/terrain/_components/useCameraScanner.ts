"use client";

import { useCallback, useEffect, useRef, useState } from "react";

/**
 * Lecture des étiquettes à la caméra (mode magasin, iPhone).
 *
 * Safari sur iPhone n'a pas de lecteur de codes-barres intégré : on utilise zxing-cpp compilé
 * en WebAssembly (paquet `barcode-detector`), servi par JokkoRM depuis /vendor/zxing/ pour que
 * tout marche sur un réseau local sans Internet. Lit le Code128 (étiquettes de boîte par défaut)
 * et le QR.
 *
 * On ne lit que le cadre de visée au centre de l'image : sur une tablette, les étiquettes des
 * boîtes voisines sont dans le champ et ne doivent pas être prises par erreur.
 */

export type CameraState = "idle" | "starting" | "running" | "denied" | "unsupported" | "error";

type Detected = { rawValue: string; format: string };
type DetectorLike = { detect(source: CanvasImageSource): Promise<Detected[]> };

/** Part du cadre de visée dans l'image affichée (largeur, hauteur). */
export const AIM = { width: 0.8, height: 0.34 };

/** Le même code n'est relu qu'après avoir quitté l'image au moins ce temps (ms). */
const REARM_MS = 900;
const FRAME_MS = 140;

let detectorPromise: Promise<DetectorLike> | null = null;

function getDetector(): Promise<DetectorLike> {
  if (!detectorPromise) {
    detectorPromise = (async () => {
      const mod = await import("barcode-detector/ponyfill");
      await mod.prepareZXingModule({
        overrides: {
          locateFile: (path: string, prefix: string) =>
            path.endsWith(".wasm") ? `/vendor/zxing/${path}` : prefix + path,
        },
        fireImmediately: true,
      });
      return new mod.BarcodeDetector({ formats: ["code_128", "qr_code"] }) as DetectorLike;
    })();
    detectorPromise.catch(() => { detectorPromise = null; });
  }
  return detectorPromise;
}

export function useCameraScanner(onCode: (text: string, format: string) => void, paused: boolean) {
  const videoRef = useRef<HTMLVideoElement | null>(null);
  const streamRef = useRef<MediaStream | null>(null);
  const canvasRef = useRef<HTMLCanvasElement | null>(null);
  const timerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const lastRef = useRef<{ text: string; seenAt: number }>({ text: "", seenAt: 0 });
  const onCodeRef = useRef(onCode);
  const pausedRef = useRef(paused);
  const [state, setState] = useState<CameraState>("idle");
  const [error, setError] = useState<string | null>(null);
  const [torch, setTorch] = useState<{ available: boolean; on: boolean }>({ available: false, on: false });

  onCodeRef.current = onCode;
  pausedRef.current = paused;

  const stop = useCallback(() => {
    if (timerRef.current) clearTimeout(timerRef.current);
    timerRef.current = null;
    streamRef.current?.getTracks().forEach((t) => t.stop());
    streamRef.current = null;
    if (videoRef.current) videoRef.current.srcObject = null;
    setTorch({ available: false, on: false });
    setState((s) => (s === "running" || s === "starting" ? "idle" : s));
  }, []);

  /** Découpe le cadre de visée dans l'image vidéo (affichée en « cover »). */
  const aimFrame = useCallback((video: HTMLVideoElement): HTMLCanvasElement | null => {
    const vw = video.videoWidth, vh = video.videoHeight;
    const cw = video.clientWidth, ch = video.clientHeight;
    if (!vw || !vh || !cw || !ch) return null;
    const scale = Math.max(cw / vw, ch / vh);
    const w = Math.round((cw * AIM.width) / scale);
    const h = Math.round((ch * AIM.height) / scale);
    const x = Math.round((vw - w) / 2), y = Math.round((vh - h) / 2);
    const canvas = canvasRef.current ?? (canvasRef.current = document.createElement("canvas"));
    canvas.width = w; canvas.height = h;
    const ctx = canvas.getContext("2d", { willReadFrequently: true });
    if (!ctx) return null;
    ctx.drawImage(video, x, y, w, h, 0, 0, w, h);
    return canvas;
  }, []);

  const loop = useCallback(async (detector: DetectorLike) => {
    const video = videoRef.current;
    if (!streamRef.current || !video) return;
    if (!pausedRef.current && video.readyState >= 2) {
      const frame = aimFrame(video);
      if (frame) {
        try {
          const found = await detector.detect(frame);
          const now = performance.now();
          const code = found.find((c) => c.rawValue.trim() !== "");
          if (code) {
            const text = code.rawValue.trim();
            const last = lastRef.current;
            const fresh = text !== last.text || now - last.seenAt > REARM_MS;
            lastRef.current = { text, seenAt: now };
            if (fresh && !pausedRef.current) onCodeRef.current(text, code.format);
          }
        } catch {
          // image illisible : on passe à la suivante
        }
      }
    }
    if (streamRef.current) timerRef.current = setTimeout(() => loop(detector), FRAME_MS);
  }, [aimFrame]);

  const start = useCallback(async () => {
    if (streamRef.current) return;
    if (!navigator.mediaDevices?.getUserMedia) {
      setState("unsupported");
      setError(window.isSecureContext
        ? "Ce navigateur ne donne pas accès à la caméra."
        : "La caméra exige une connexion sécurisée (https) avec un certificat de confiance.");
      return;
    }
    setState("starting"); setError(null);
    try {
      const [detector, stream] = await Promise.all([
        getDetector(),
        navigator.mediaDevices.getUserMedia({
          audio: false,
          video: { facingMode: { ideal: "environment" }, width: { ideal: 1920 }, height: { ideal: 1080 } },
        }),
      ]);
      streamRef.current = stream;
      const video = videoRef.current;
      if (!video) { stop(); return; }
      video.srcObject = stream;
      video.setAttribute("playsinline", "true");
      video.muted = true;
      await video.play();
      const track = stream.getVideoTracks()[0];
      const caps = (track.getCapabilities?.() ?? {}) as MediaTrackCapabilities & { torch?: boolean };
      setTorch({ available: caps.torch === true, on: false });
      lastRef.current = { text: "", seenAt: 0 };
      setState("running");
      loop(detector);
    } catch (e) {
      streamRef.current?.getTracks().forEach((t) => t.stop());
      streamRef.current = null;
      const name = (e as Error).name;
      if (name === "NotAllowedError" || name === "SecurityError") {
        setState("denied");
        setError("Accès à la caméra refusé. Autorise-le dans Réglages › Safari › Caméra, puis réessaie.");
      } else {
        setState("error");
        setError((e as Error).message || "La caméra n'a pas pu démarrer.");
      }
    }
  }, [loop, stop]);

  const toggleTorch = useCallback(async () => {
    const track = streamRef.current?.getVideoTracks()[0];
    if (!track) return;
    const on = !torch.on;
    try {
      await track.applyConstraints({ advanced: [{ torch: on } as MediaTrackConstraintSet] });
      setTorch({ available: true, on });
    } catch {
      setTorch({ available: false, on: false });
    }
  }, [torch.on]);

  // Une page en arrière-plan rend la caméra : on la coupe proprement et on la relance au retour.
  useEffect(() => {
    const onVisibility = () => { if (document.hidden) stop(); };
    document.addEventListener("visibilitychange", onVisibility);
    return () => { document.removeEventListener("visibilitychange", onVisibility); stop(); };
  }, [stop]);

  return { videoRef, state, error, start, stop, torch, toggleTorch };
}
