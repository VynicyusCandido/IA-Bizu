"use client";

import { useState } from "react";
import { toast } from "sonner";
import { useSWRConfig } from "swr";
import { assumirConversa, devolverConversa, type EstadoConversa } from "@/lib/api";
import { cn } from "@/lib/utils";

/**
 * Quem cuida da conversa: a IA ou o corretor (assumir/devolver). Seletor flutuante de duas opções, a marcada leva
 * a cor de marca. Contato sem IA conta como "você"; no limite de respostas a IA não pode ser religada.
 */
export function AcaoDaConversa({ id, estado }: { id: string; estado: EstadoConversa }) {
  const { mutate } = useSWRConfig();
  const [trocando, setTrocando] = useState(false);
  const iaCuida = estado === "BOT";
  const limiteAtingido = estado === "ENCERRADA";

  async function escolher(ia: boolean) {
    if (ia === iaCuida || trocando) return;
    setTrocando(true);
    try {
      await (ia ? devolverConversa(id) : assumirConversa(id));
      await Promise.all([mutate("/api/conversas"), mutate(`/api/conversas/${id}/mensagens`)]);
      toast.success(ia ? "A IA voltou a responder." : "Você assumiu a conversa. A IA ficou em silêncio.");
    } catch {
      toast.error("Não foi possível concluir. Tente de novo.");
    } finally {
      setTrocando(false);
    }
  }

  const opcao = (ia: boolean, rotulo: string, desabilitada = false) => (
    <button
      type="button"
      role="radio"
      aria-checked={ia === iaCuida}
      disabled={trocando || desabilitada}
      title={desabilitada ? "A IA chegou ao limite de respostas desta conversa" : undefined}
      onClick={() => escolher(ia)}
      className={cn(
        "rounded-full px-3.5 py-1 text-xs font-medium text-muted-foreground transition-colors hover:text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring disabled:cursor-not-allowed disabled:opacity-50",
        ia === iaCuida && "bg-primary text-primary-foreground hover:text-primary-foreground",
      )}
    >
      {rotulo}
    </button>
  );

  return (
    <div
      role="radiogroup"
      aria-label="Quem cuida da conversa"
      className="pointer-events-auto flex gap-0.5 rounded-full border bg-card/90 p-1 shadow-md backdrop-blur"
    >
      {opcao(true, "IA", limiteAtingido)}
      {opcao(false, "Assumir")}
    </div>
  );
}
