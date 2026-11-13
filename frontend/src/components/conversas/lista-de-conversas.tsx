"use client";

import { History, Loader2, MessageSquare, Search } from "lucide-react";
import Link from "next/link";
import { useParams } from "next/navigation";
import { useState } from "react";
import { toast } from "sonner";
import useSWR from "swr";
import { ErroComRetentativa } from "@/components/painel/erro-com-retentativa";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Skeleton } from "@/components/ui/skeleton";
import { Tabs, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { AvatarDoContato } from "@/components/conversas/avatar-do-contato";
import { buscar, ErroApi, importarConversas, type ConversaResumo } from "@/lib/api";
import { ESTADOS, LIMITE_RESPOSTAS, previa, tempoRelativo, tituloDaConversa } from "@/lib/formatar";
import { cn } from "@/lib/utils";

/** A aba escolhida leva a cor de marca. */
const ABA = "data-active:bg-primary data-active:text-primary-foreground dark:data-active:border-primary dark:data-active:bg-primary dark:data-active:text-primary-foreground";

const semAcento = (texto: string) =>
  texto.normalize("NFD").replace(/\p{Diacritic}/gu, "").toLowerCase();

type Filtro = "todas" | "HUMANO" | "BOT" | "SEM_IA";

export function ListaDeConversas() {
  const { id: aberta } = useParams<{ id?: string }>();
  const [filtro, setFiltro] = useState<Filtro>("todas");
  const { data, error, mutate } = useSWR<ConversaResumo[]>("/api/conversas", buscar, { refreshInterval: 5000 });

  const [busca, setBusca] = useState("");
  const termo = semAcento(busca.trim());

  // A busca olha o nome, o telefone e a última mensagem, sem diferenciar maiúsculas nem acentos.
  const visiveis = data?.filter(
    (c) =>
      (filtro === "todas" || c.estado === filtro) &&
      (!termo || semAcento(`${c.nome ?? ""} ${c.telefone} ${c.ultimaMensagem ?? ""}`).includes(termo)),
  );
  const [importando, setImportando] = useState(false);

  async function importar() {
    setImportando(true);
    try {
      const { conversas, mensagens } = await importarConversas();
      await mutate();
      toast.success(
        mensagens === 0 ? "Nada de novo para importar." : `${mensagens} mensagens de ${conversas} conversas importadas.`,
      );
    } catch (erro) {
      toast.error(
        erro instanceof ErroApi && erro.status === 409
          ? "Já tem uma importação em andamento. Aguarde terminar."
          : "Não consegui importar. Confira se o WhatsApp está conectado.",
      );
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
            <TabsTrigger value="todas" className={ABA}>Todas</TabsTrigger>
            <TabsTrigger value="HUMANO" className={ABA}>Com você</TabsTrigger>
            <TabsTrigger value="BOT" className={ABA}>IA</TabsTrigger>
            <TabsTrigger value="SEM_IA" className={ABA}>Sem IA</TabsTrigger>
          </TabsList>
        </Tabs>
      </div>

      <div className="relative border-b px-4 py-2.5">
        <Search
          className="pointer-events-none absolute left-7 top-1/2 size-4 -translate-y-1/2 text-muted-foreground"
          aria-hidden
        />
        <Input
          type="search"
          value={busca}
          onChange={(e) => setBusca(e.target.value)}
          placeholder="Buscar conversa"
          aria-label="Buscar conversa"
          className="h-9 pl-8"
        />
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
            <p className="font-medium">
              {!data?.length ? "Nenhuma conversa ainda" : termo ? "Nenhuma conversa encontrada" : "Nenhuma conversa neste filtro"}
            </p>
            <p className="text-sm text-muted-foreground">
              {data?.length
                ? termo
                  ? "Tente outro nome, número ou trecho da mensagem."
                  : "Troque o filtro para ver as outras."
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
                    "flex items-start gap-3 rounded-lg px-3 py-2.5 transition-colors hover:bg-muted",
                    aberta === c.id && "bg-muted shadow-[inset_3px_0_0_var(--primary)]",
                  )}
                >
                  <AvatarDoContato nome={c.nome} fotoUrl={c.fotoUrl} />
                  <div className="flex min-w-0 flex-1 flex-col gap-1">
                  <div className="flex items-baseline justify-between gap-2">
                    <span className={cn("truncate text-sm", c.naoLidas > 0 ? "font-semibold" : "font-medium")}>
                      {tituloDaConversa(c.nome, c.telefone)}
                    </span>
                    <span className="shrink-0 text-xs text-muted-foreground">{tempoRelativo(c.ultimaAtividadeEm)}</span>
                  </div>
                  <div className="flex items-center justify-between gap-2">
                    <p className="truncate text-sm text-muted-foreground">{previa(c.ultimaMensagem, c.ultimaOrigem)}</p>
                    {c.naoLidas > 0 && (
                      <span
                        title={`${c.naoLidas} não lidas`}
                        className="flex shrink-0 items-center gap-1 rounded-md bg-primary px-1.5 py-0.5 text-xs font-semibold text-primary-foreground"
                      >
                        <MessageSquare className="size-3" aria-hidden />
                        {c.naoLidas}
                      </span>
                    )}
                  </div>
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
