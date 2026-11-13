"use client";

import { Clock, House, MapPin, Target, Wallet, X, type LucideIcon } from "lucide-react";
import Link from "next/link";
import { useParams } from "next/navigation";
import { Fragment, useEffect, useRef } from "react";
import useSWR, { useSWRConfig } from "swr";
import { AcaoDaConversa } from "@/components/conversas/acao-da-conversa";
import { Balao } from "@/components/conversas/balao";
import { CampoDeMensagem } from "@/components/conversas/campo-de-mensagem";
import { ErroComRetentativa } from "@/components/painel/erro-com-retentativa";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { buscar, ErroApi, marcarComoLida, type ConversaResumo, type Mensagem } from "@/lib/api";
import { AvatarDoContato } from "@/components/conversas/avatar-do-contato";
import { diaDaMensagem, ESTADOS, formatarTelefone, LIMITE_RESPOSTAS, tituloDaConversa } from "@/lib/formatar";
import { cn } from "@/lib/utils";

const AVISO = {
  BOT: "A IA está respondendo. Se você enviar uma mensagem, assume a conversa e a IA fica em silêncio.",
  HUMANO: "Você assumiu esta conversa. A IA está em silêncio.",
  ENCERRADA: `A IA chegou ao limite de ${LIMITE_RESPOSTAS} respostas e parou. Você responde por aqui.`,
  SEM_IA: "A IA não atende este contato (por exemplo, contato salvo na sua agenda). Ligue o seletor se quiser que ela responda.",
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

  // Conversa aberta = mensagens vistas: avisa o servidor (e a lista) sempre que chega mensagem nova.
  const { mutate: atualizarLista } = useSWRConfig();
  const ultima = mensagens?.[mensagens.length - 1]?.criadaEm;
  useEffect(() => {
    if (!ultima) return;
    marcarComoLida(id, ultima)
      .then(() => atualizarLista("/api/conversas"))
      .catch(() => {});
  }, [id, ultima, atualizarLista]);

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
        {conversa && <AvatarDoContato nome={conversa.nome} fotoUrl={conversa.fotoUrl} />}
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
              <p className="line-clamp-2 text-xs text-muted-foreground" title={AVISO[conversa.estado]}>
                {AVISO[conversa.estado]}
              </p>
            </>
          ) : (
            <Skeleton className="h-9 w-40" />
          )}
        </div>
        <Button asChild variant="ghost" size="icon" className="-mr-2 -mt-1.5 self-start md:-mr-3">
          <Link href="/conversas" aria-label="Fechar conversa">
            <X />
          </Link>
        </Button>
      </div>

      {conversa && <Qualificacao conversa={conversa} />}

      <div className="relative min-h-0 flex-1">
        {conversa && (
          <div className="pointer-events-none absolute inset-x-0 top-3 z-10 flex justify-center">
            <AcaoDaConversa id={conversa.id} estado={conversa.estado} />
          </div>
        )}
        <div className="h-full overflow-y-auto bg-muted/30">
        {error ? (
          <div className="p-4">
            <ErroComRetentativa
              titulo="Não consegui carregar a conversa"
              descricao="Tente de novo em instantes."
              aoTentarDeNovo={() => mutate()}
            />
          </div>
        ) : !mensagens ? (
          <div className="flex flex-col gap-3 px-4 pb-4 pt-14">
            <Skeleton className="h-10 w-2/3" />
            <Skeleton className="ml-auto h-10 w-1/2" />
            <Skeleton className="h-10 w-3/5" />
          </div>
        ) : (
          <div className="flex flex-col gap-3 px-3 pb-4 pt-14">
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
      </div>

      {conversa && <CampoDeMensagem id={conversa.id} />}
    </>
  );
}
