"use client";

import { useRef, useState } from "react";
import { Alert, Button } from "@/components/ui";
import {
  downloadTopographyTemplate,
  importTopographyExcel,
  type ImportTopographyReport,
} from "@/lib/api";
import { Icons } from "@/lib/icons";

/** Import Excel de la topographie : boutons (en-tête de page) + rapport. */
export function useTopographyImport(onImported: () => void) {
  const [report, setReport] = useState<ImportTopographyReport | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [uploading, setUploading] = useState(false);
  const [downloading, setDownloading] = useState(false);
  const fileRef = useRef<HTMLInputElement>(null);

  async function handleDownload() {
    setDownloading(true);
    try { await downloadTopographyTemplate(); }
    catch (e) { setError((e as Error).message); }
    finally { setDownloading(false); }
  }

  async function handleUpload(e: React.ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0];
    if (!file) return;
    setUploading(true); setError(null); setReport(null);
    try {
      setReport(await importTopographyExcel(file));
      onImported();
    } catch (err) { setError((err as Error).message); }
    finally {
      setUploading(false);
      e.target.value = "";
    }
  }

  const actions = (
    <>
      <Button variant="secondary" icon={<Icons.excel size={15} />} loading={downloading} onClick={handleDownload}>
        Modèle Excel
      </Button>
      <Button variant="secondary" icon={<Icons.uploadFile size={15} />} loading={uploading}
        onClick={() => fileRef.current?.click()}>
        Importer
      </Button>
      <input ref={fileRef} type="file" accept=".xlsx" onChange={handleUpload} hidden />
    </>
  );

  const feedback = (report || error) && (
    <div style={{ marginBottom: 16 }}>
      {error && (
        <Alert tone="danger" title="Import impossible"
          action={<CloseButton onClick={() => setError(null)} />}>{error}</Alert>
      )}
      {report && (
        <Alert
          tone={report.errors.length ? "warning" : "success"}
          title={`Import terminé : ${report.created} créé${report.created > 1 ? "s" : ""}, ${report.skippedExisting} déjà présent${report.skippedExisting > 1 ? "s" : ""}${report.errors.length ? `, ${report.errors.length} erreur${report.errors.length > 1 ? "s" : ""}` : ""}`}
          action={<CloseButton onClick={() => setReport(null)} />}
        >
          {report.errors.length > 0 && (
            <ul style={{ margin: "4px 0 0 18px", padding: 0 }}>
              {report.errors.slice(0, 5).map((e, i) => <li key={i}>{e}</li>)}
              {report.errors.length > 5 && <li>… et {report.errors.length - 5} autres</li>}
            </ul>
          )}
        </Alert>
      )}
    </div>
  );

  return { actions, feedback };
}

function CloseButton({ onClick }: { onClick: () => void }) {
  return (
    <button type="button" onClick={onClick} aria-label="Fermer"
      style={{ border: "none", background: "transparent", padding: 2, color: "inherit", display: "inline-flex" }}>
      <Icons.close size={14} weight="bold" />
    </button>
  );
}
