"use client";

import type { ButtonHTMLAttributes, ReactNode } from "react";

type Variant = "primary" | "secondary" | "ghost" | "danger";
type Size = "sm" | "md" | "lg";

const VARIANT_STYLES: Record<Variant, React.CSSProperties> = {
  primary: {
    background: "var(--brand-500)",
    color: "var(--text-on-brand)",
    border: "1px solid var(--brand-500)",
  },
  secondary: {
    background: "var(--surface-card)",
    color: "var(--text-primary)",
    border: "1px solid var(--border-strong)",
  },
  ghost: {
    background: "transparent",
    color: "var(--text-secondary)",
    border: "1px solid transparent",
  },
  danger: {
    background: "var(--danger-500)",
    color: "white",
    border: "1px solid var(--danger-500)",
  },
};

const SIZE_STYLES: Record<Size, React.CSSProperties> = {
  sm: { padding: "4px 10px", fontSize: 12, fontWeight: 500, borderRadius: "var(--radius-sm)" },
  md: { padding: "6px 14px", fontSize: 13, fontWeight: 500, borderRadius: "var(--radius-md)" },
  lg: { padding: "10px 20px", fontSize: 14, fontWeight: 600, borderRadius: "var(--radius-md)" },
};

export interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: Variant;
  size?: Size;
  icon?: ReactNode;
  iconRight?: ReactNode;
  loading?: boolean;
  fullWidth?: boolean;
}

export function Button({
  variant = "primary",
  size = "md",
  icon,
  iconRight,
  loading,
  fullWidth,
  disabled,
  children,
  style,
  ...rest
}: ButtonProps) {
  return (
    <button
      {...rest}
      disabled={disabled || loading}
      style={{
        display: "inline-flex",
        alignItems: "center",
        justifyContent: "center",
        gap: 6,
        width: fullWidth ? "100%" : undefined,
        transition: "opacity 150ms ease, background 150ms ease",
        ...VARIANT_STYLES[variant],
        ...SIZE_STYLES[size],
        ...style,
      }}
    >
      {loading ? <Spinner size={size === "lg" ? 16 : 14} /> : icon}
      {children}
      {!loading && iconRight}
    </button>
  );
}

function Spinner({ size = 14 }: { size?: number }) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" style={{ animation: "spin 0.8s linear infinite" }}>
      <style>{`@keyframes spin { to { transform: rotate(360deg); } }`}</style>
      <circle cx="12" cy="12" r="10" fill="none" stroke="currentColor" strokeWidth="3" strokeLinecap="round" strokeDasharray="30 70" opacity="0.8" />
    </svg>
  );
}
