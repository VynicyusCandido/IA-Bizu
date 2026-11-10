"use client";

import Link from "next/link";
import useSWR from "swr";
import { buscar, type SituacaoWhatsapp } from "@/lib/api";
import { cn } from "@/lib/utils";

/** Ponto + frase curta com a situação do WhatsApp; leva à tela de conexão. */
export function EstadoWhatsapp({ className }: { className?: string }) {
  const { data, error } = useSWR<SituacaoWhatsapp>("/api/whatsapp", buscar, { refreshInterval: 15000 });
  const [ponto, texto] = error
    ? ["bg-zinc-400 dark:bg-zinc-500", "WhatsApp sem resposta"]
    : !data
      ? ["bg-zinc-400 dark:bg-zinc-500", "Verificando…"]
      : data.conectado
        ? ["bg-emerald-500", "WhatsApp conectado"]
        : ["bg-amber-500", "WhatsApp desconectado"];
  return (
    <Link
      href="/conexao"
      className={cn(
        "flex items-center gap-2 rounded-lg px-3 py-2 text-xs text-muted-foreground transition-colors hover:bg-muted hover:text-foreground",
        className,
      )}
    >
      <span className={cn("size-2 rounded-full", ponto)} aria-hidden />
      {texto}
    </Link>
  );
}
