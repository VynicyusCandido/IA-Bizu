"use client";

import { History, Loader2, MessageSquare } from "lucide-react";
import Link from "next/link";
import { useParams } from "next/navigation";
import { useState } from "react";
import { toast } from "sonner";
import useSWR from "swr";
import { ErroComRetentativa } from "@/components/painel/erro-com-retentativa";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { Tabs, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { buscar, importarConversas, type ConversaResumo } from "@/lib/api";
import { ESTADOS, LIMITE_RESPOSTAS, previa, tempoRelativo, tituloDaConversa } from "@/lib/formatar";
import { cn } from "@/lib/utils";

type Filtro = "todas" | "HUMANO" | "BOT" | "SEM_IA";

export function ListaDeConversas() {
  const { id: aberta } = useParams<{ id?: string }>();
  const [filtro, setFiltro] = useState<Filtro>("todas");
  const { data, error, mutate } = useSWR<ConversaResumo[]>("/api/conversas", buscar, { refreshInterval: 5000 });

  const visiveis = data?.filter((c) => filtro === "todas" || c.estado === filtro);
  const [importando, setImportando] = useState(false);

  async function importar() {
    setImportando(true);
    try {
      const { conversas, mensagens } = await importarConversas();
      await mutate();
      toast.success(
        mensagens === 0 ? "Nada de novo para importar." : `${mensagens} mensagens de ${conversas} conversas importadas.`,
      );
    } catch {
      toast.error("Não consegui importar. Confira se o WhatsApp está conectado.");
    } finally {
      setImportando(false);
    }
  }

  return (
    <>
      <div className="flex flex-col gap-3 border-b p-4">
        <div className="flex items-center justify-between">
          <h1 className="text-lg font-semibold tracking-tight">Conversas</h1>
          <Button
            variant="ghost"
            size="icon-sm"
            onClick={importar}
            disabled={importando}
            aria-label="Importar as conversas da última semana"
            title="Importar as conversas da última semana"
          >
            {importando ? <Loader2 className="animate-spin" /> : <History />}
          </Button>
        </div>
        <Tabs value={filtro} onValueChange={(v) => setFiltro(v as Filtro)}>
          <TabsList className="w-full">
            <TabsTrigger value="todas">Todas</TabsTrigger>
            <TabsTrigger value="HUMANO">Com você</TabsTrigger>
            <TabsTrigger value="BOT">IA</TabsTrigger>
            <TabsTrigger value="SEM_IA">Sem IA</TabsTrigger>
          </TabsList>
        </Tabs>
      </div>

      <div className="min-h-0 flex-1 overflow-y-auto">
        {error ? (
          <div className="p-4">
            <ErroComRetentativa
              titulo="Não consegui carregar as conversas"
              descricao="Tente de novo em instantes."
              aoTentarDeNovo={() => mutate()}
            />
          </div>
        ) : !visiveis ? (
          <div className="flex flex-col gap-1 p-2">
            {[0, 1, 2, 3].map((i) => (
              <Skeleton key={i} className="h-[76px] rounded-lg" />
            ))}
          </div>
        ) : visiveis.length === 0 ? (
          <div className="flex flex-col items-center gap-2 px-6 py-16 text-center">
            <MessageSquare className="size-8 text-muted-foreground" aria-hidden />
            <p className="font-medium">{data?.length ? "Nenhuma conversa neste filtro" : "Nenhuma conversa ainda"}</p>
            <p className="text-sm text-muted-foreground">
              {data?.length
                ? "Troque o filtro para ver as outras."
                : "Quando um cliente novo escrever no seu WhatsApp, a conversa aparece aqui."}
            </p>
            {!data?.length && (
              <Button variant="outline" size="sm" className="mt-2" onClick={importar} disabled={importando}>
                {importando ? <Loader2 className="animate-spin" /> : <History />}
                Importar a última semana
              </Button>
            )}
          </div>
        ) : (
          <ul className="flex flex-col gap-1 p-2">
            {visiveis.map((c) => (
              <li key={c.id}>
                <Link
                  href={`/conversas/${c.id}`}
                  aria-current={aberta === c.id ? "page" : undefined}
                  className={cn(
                    "flex flex-col gap-1 rounded-lg px-3 py-2.5 transition-colors hover:bg-muted",
                    aberta === c.id && "bg-muted",
                  )}
                >
                  <div className="flex items-baseline justify-between gap-2">
                    <span className="truncate text-sm font-medium">{tituloDaConversa(c.nome, c.telefone)}</span>
                    <span className="shrink-0 text-xs text-muted-foreground">{tempoRelativo(c.ultimaAtividadeEm)}</span>
                  </div>
                  <p className="truncate text-sm text-muted-foreground">{previa(c.ultimaMensagem, c.ultimaOrigem)}</p>
                  <div className="flex items-center justify-between text-xs text-muted-foreground">
                    <span className="flex items-center gap-1.5">
                      <span className={cn("size-2 rounded-full", ESTADOS[c.estado].ponto)} aria-hidden />
                      {ESTADOS[c.estado].rotulo}
                    </span>
                    {c.estado !== "SEM_IA" && (
                      <span>
                        {c.respostasIa}/{LIMITE_RESPOSTAS}
                      </span>
                    )}
                  </div>
                </Link>
              </li>
            ))}
          </ul>
        )}
      </div>
    </>
  );
}
