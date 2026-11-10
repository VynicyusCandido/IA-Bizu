"use client";

import { Clock, PauseCircle } from "lucide-react";
import { useEffect, useState } from "react";
import useSWR from "swr";
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from "@/components/ui/alert-dialog";
import { buscar, type Conta } from "@/lib/api";
import { cn } from "@/lib/utils";

const CHAVE_DO_AVISO = "bizu.aviso";

function hoje(): string {
  return new Date().toISOString().slice(0, 10);
}

/** O modal aparece uma vez por dia e por situação; a faixa fica visível enquanto houver o que avisar. */
function jaMostradoHoje(estado: string): boolean {
  try {
    return localStorage.getItem(CHAVE_DO_AVISO) === `${hoje()}:${estado}`;
  } catch {
    return false;
  }
}

function marcarComoMostrado(estado: string): void {
  try {
    localStorage.setItem(CHAVE_DO_AVISO, `${hoje()}:${estado}`);
  } catch {
    // sem armazenamento: o modal pode reaparecer, sem prejuízo
  }
}

function textos(conta: Conta): { titulo: string; descricao: string } | null {
  if (conta.pausaPor === "LIMITE_MENSAL") {
    return {
      titulo: "Limite mensal da IA atingido",
      descricao: `Você usou ${conta.respostasNoMes} de ${conta.limiteDoMes} respostas neste mês. A IA volta no mês que vem ou quando o limite for ampliado.`,
    };
  }
  const fim = conta.assinaturaAte
    ? new Date(conta.assinaturaAte).toLocaleString("pt-BR", { dateStyle: "short", timeStyle: "short" })
    : "";
  switch (conta.estadoAssinatura) {
    case "AVISO":
      return {
        titulo: "Sua assinatura está perto de vencer",
        descricao: `Ela vence em ${fim}. Renove para a IA continuar respondendo os seus clientes.`,
      };
    case "CARENCIA":
      return {
        titulo: "Sua assinatura venceu",
        descricao: `Venceu em ${fim}. A IA continua respondendo por mais 24 horas e depois pausa. Renove para evitar a pausa.`,
      };
    case "VENCIDA":
      return {
        titulo: "IA pausada: assinatura vencida",
        descricao: "O painel continua disponível e as mensagens dos clientes aparecem aqui. Renove para a IA voltar a responder.",
      };
    default:
      return null;
  }
}

export function AvisoDeAssinatura() {
  const { data: conta } = useSWR<Conta>("/api/conta", buscar, { refreshInterval: 60000 });
  const [aberto, setAberto] = useState(false);
  const mensagem = conta ? textos(conta) : null;
  const estado = conta?.estadoAssinatura ?? "ATIVA";

  // VENCIDA não abre modal (a faixa já avisa); AVISO e CARENCIA abrem uma vez por dia.
  useEffect(() => {
    if ((estado === "AVISO" || estado === "CARENCIA") && !jaMostradoHoje(estado)) {
      marcarComoMostrado(estado);
      // eslint-disable-next-line react-hooks/set-state-in-effect -- abrir o modal quando o dado chega
      setAberto(true);
    }
  }, [estado]);

  if (!mensagem || !conta) return null;
  const urgente = estado === "VENCIDA" || conta.pausaPor === "LIMITE_MENSAL";
  const Icone = urgente ? PauseCircle : Clock;

  return (
    <>
      <div
        role="status"
        className={cn(
          "flex items-center gap-2 border-b px-4 py-2 text-sm",
          urgente ? "bg-destructive/10 text-destructive" : "bg-amber-500/10 text-amber-700 dark:text-amber-400",
        )}
      >
        <Icone className="size-4 shrink-0" aria-hidden />
        <span className="font-medium">{mensagem.titulo}.</span>
        <span className="hidden sm:inline">{mensagem.descricao}</span>
      </div>

      <AlertDialog open={aberto} onOpenChange={setAberto}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>{mensagem.titulo}</AlertDialogTitle>
            <AlertDialogDescription>{mensagem.descricao}</AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogAction>Entendi</AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </>
  );
}
