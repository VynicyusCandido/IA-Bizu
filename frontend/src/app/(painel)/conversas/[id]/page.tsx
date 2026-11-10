"use client";

import { ArrowLeft, Clock, House, MapPin, Target, Wallet, type LucideIcon } from "lucide-react";
import Link from "next/link";
import { useParams } from "next/navigation";
import { Fragment, useEffect, useRef } from "react";
import useSWR from "swr";
import { AcaoDaConversa } from "@/components/conversas/acao-da-conversa";
import { Balao } from "@/components/conversas/balao";
import { ErroComRetentativa } from "@/components/painel/erro-com-retentativa";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { buscar, ErroApi, type ConversaResumo, type Mensagem } from "@/lib/api";
import { diaDaMensagem, ESTADOS, formatarTelefone, LIMITE_RESPOSTAS, tituloDaConversa } from "@/lib/formatar";
import { cn } from "@/lib/utils";

const AVISO = {
  BOT: "A IA está respondendo. Assuma quando quiser responder você mesmo.",
  HUMANO: "Você assumiu esta conversa. A IA está em silêncio: responda pelo seu WhatsApp.",
  ENCERRADA: `A IA chegou ao limite de ${LIMITE_RESPOSTAS} respostas e parou. Continue pelo seu WhatsApp.`,
  SEM_IA: "A IA não atende este contato (contato salvo ou sem número conhecido). Você conversa pelo seu WhatsApp.",
} as const;

function Qualificacao({ conversa }: { conversa: ConversaResumo }) {
  const itens: [LucideIcon, string, string | null][] = [
    [Target, "Finalidade", conversa.finalidade],
    [MapPin, "Região", conversa.regiao],
    [Wallet, "Valor", conversa.faixaValor],
    [Clock, "Prazo", conversa.prazo],
    [House, "Imóvel", conversa.imovelCitado],
  ];
  const preenchidos = itens.filter(([, , valor]) => valor);
  if (preenchidos.length === 0) return null;
  return (
    <div className="flex flex-wrap gap-2 border-b px-4 py-2.5">
      {preenchidos.map(([Icone, rotulo, valor]) => (
        <Badge key={rotulo} variant="outline" className="gap-1.5 font-normal" title={rotulo}>
          <Icone aria-hidden />
          {valor}
        </Badge>
      ))}
    </div>
  );
}

export default function ConversaAberta() {
  const { id } = useParams<{ id: string }>();
  const { data: lista } = useSWR<ConversaResumo[]>("/api/conversas", buscar, { refreshInterval: 5000 });
  const {
    data: mensagens,
    error,
    mutate,
  } = useSWR<Mensagem[]>(`/api/conversas/${id}/mensagens`, buscar, { refreshInterval: 5000 });
  const fim = useRef<HTMLDivElement>(null);
  const conversa = lista?.find((c) => c.id === id);

  useEffect(() => {
    fim.current?.scrollIntoView({ block: "end" });
  }, [mensagens?.length, id]);

  if (error instanceof ErroApi && error.status === 404) {
    return (
      <div className="grid h-full place-items-center p-6 text-center">
        <div className="flex flex-col items-center gap-3">
          <p className="font-medium">Conversa não encontrada</p>
          <Button asChild variant="outline">
            <Link href="/conversas">Voltar às conversas</Link>
          </Button>
        </div>
      </div>
    );
  }

  return (
    <>
      <div className="flex items-center gap-2 border-b px-3 py-3 md:px-4">
        <Button asChild variant="ghost" size="icon" className="md:hidden">
          <Link href="/conversas" aria-label="Voltar às conversas">
            <ArrowLeft />
          </Link>
        </Button>
        <div className="min-w-0 flex-1">
          {conversa ? (
            <>
              <p className="truncate font-medium">{tituloDaConversa(conversa.nome, conversa.telefone)}</p>
              <p className="flex items-center gap-1.5 text-xs text-muted-foreground">
                <span className={cn("size-2 rounded-full", ESTADOS[conversa.estado].ponto)} aria-hidden />
                <span className="truncate">
                  {[
                    ESTADOS[conversa.estado].rotulo,
                    conversa.estado !== "SEM_IA" && `${conversa.respostasIa}/${LIMITE_RESPOSTAS} respostas`,
                    conversa.nome && !conversa.telefone.includes("@") && formatarTelefone(conversa.telefone),
                  ]
                    .filter(Boolean)
                    .join(" · ")}
                </span>
              </p>
            </>
          ) : (
            <Skeleton className="h-9 w-40" />
          )}
        </div>
        {conversa && conversa.estado !== "SEM_IA" && <AcaoDaConversa id={conversa.id} estado={conversa.estado} />}
      </div>

      {conversa && <Qualificacao conversa={conversa} />}

      <div className="min-h-0 flex-1 overflow-y-auto bg-muted/30">
        {error ? (
          <div className="p-4">
            <ErroComRetentativa
              titulo="Não consegui carregar a conversa"
              descricao="Tente de novo em instantes."
              aoTentarDeNovo={() => mutate()}
            />
          </div>
        ) : !mensagens ? (
          <div className="flex flex-col gap-3 p-4">
            <Skeleton className="h-10 w-2/3" />
            <Skeleton className="ml-auto h-10 w-1/2" />
            <Skeleton className="h-10 w-3/5" />
          </div>
        ) : (
          <div className="mx-auto flex max-w-3xl flex-col gap-3 p-4">
            {mensagens.map((m, i) => {
              const novoDia = i === 0 || diaDaMensagem(m.criadaEm) !== diaDaMensagem(mensagens[i - 1].criadaEm);
              return (
                <Fragment key={`${m.criadaEm}-${i}`}>
                  {novoDia && (
                    <p className="py-1 text-center text-xs text-muted-foreground">{diaDaMensagem(m.criadaEm)}</p>
                  )}
                  <Balao mensagem={m} />
                </Fragment>
              );
            })}
            <div ref={fim} />
          </div>
        )}
      </div>

      {conversa && (
        <p className="border-t px-4 py-2.5 text-center text-xs text-muted-foreground">{AVISO[conversa.estado]}</p>
      )}
    </>
  );
}
