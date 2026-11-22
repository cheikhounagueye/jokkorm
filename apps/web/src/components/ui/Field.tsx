"use client";

import type {
  InputHTMLAttributes,
  ReactNode,
  SelectHTMLAttributes,
  TextareaHTMLAttributes,
} from "react";

const control: React.CSSProperties = {
  width: "100%",
  padding: "9px 12px",
  fontSize: 14,
  borderRadius: "var(--radius-md)",
  border: "1px solid var(--border-strong)",
  background: "var(--surface-card)",
  color: "var(--text-primary)",
};

/** Libellé + contrôle + aide/erreur, avec espacement vertical constant. */
export function Field({
  label,
  required,
  hint,
  error,
  children,
}: {
  label: ReactNode;
  required?: boolean;
  hint?: ReactNode;
  error?: ReactNode;
  children: ReactNode;
}) {
  return (
    <label style={{ display: "block", marginBottom: 16 }}>
      <span style={{ display: "block", fontSize: 12, fontWeight: 600, color: "var(--text-secondary)", marginBottom: 6 }}>
        {label}
        {required && <span style={{ color: "var(--danger-500)", marginLeft: 3 }}>*</span>}
      </span>
      {children}
      {error
        ? <span style={{ display: "block", fontSize: 12, color: "var(--danger-500)", marginTop: 4 }}>{error}</span>
        : hint && <span style={{ display: "block", fontSize: 12, color: "var(--text-tertiary)", marginTop: 4 }}>{hint}</span>}
    </label>
  );
}

export function Input({ style, ...rest }: InputHTMLAttributes<HTMLInputElement>) {
  return <input {...rest} style={{ ...control, ...style }} />;
}

export function Select({ style, children, ...rest }: SelectHTMLAttributes<HTMLSelectElement>) {
  return <select {...rest} style={{ ...control, ...style }}>{children}</select>;
}

export function Textarea({ style, ...rest }: TextareaHTMLAttributes<HTMLTextAreaElement>) {
  return <textarea {...rest} style={{ ...control, minHeight: 88, resize: "vertical", ...style }} />;
}
