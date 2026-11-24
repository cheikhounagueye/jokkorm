"use client";

/**
 * Bips du mode magasin. Bonus seulement : sur iPhone le bouton silencieux coupe le son des
 * pages web et Safari ne sait pas faire vibrer. L'écran dit toujours où on en est.
 * Le son ne peut démarrer qu'après un toucher de l'utilisateur : appeler {@link unlockAudio}
 * dans un gestionnaire de clic.
 */

let ctx: AudioContext | null = null;

export function unlockAudio() {
  try {
    ctx ??= new AudioContext();
    if (ctx.state === "suspended") void ctx.resume();
  } catch {
    ctx = null;
  }
}

function tone(freq: number, startIn: number, duration: number, type: OscillatorType = "sine", gain = 0.18) {
  if (!ctx) return;
  const t0 = ctx.currentTime + startIn;
  const osc = ctx.createOscillator();
  const amp = ctx.createGain();
  osc.type = type;
  osc.frequency.value = freq;
  amp.gain.setValueAtTime(0, t0);
  amp.gain.linearRampToValueAtTime(gain, t0 + 0.01);
  amp.gain.exponentialRampToValueAtTime(0.0001, t0 + duration);
  osc.connect(amp).connect(ctx.destination);
  osc.start(t0);
  osc.stop(t0 + duration + 0.02);
}

/** Un scan accepté : un bip court. */
export function beepScan() { tone(1760, 0, 0.09, "square", 0.08); }

/** Geste enregistré : deux notes montantes. */
export function beepDone() { tone(1046, 0, 0.12); tone(1568, 0.12, 0.2); }

/** Refus ou erreur : note grave double. */
export function beepError() { tone(220, 0, 0.16, "sawtooth", 0.12); tone(196, 0.2, 0.22, "sawtooth", 0.12); }
