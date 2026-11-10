import type { EstadoConversa, OrigemMensagem } from "@/lib/api";

/** 5511999990001 -> +55 11 99999-0001. Números que não são do Brasil só ganham o "+". */
export function formatarTelefone(telefone: string): string {
  const digitos = telefone.replace(/\D/g, "");
  const br = /^55(\d{2})(\d{4,5})(\d{4})$/.exec(digitos);
  return br ? `+55 ${br[1]} ${br[2]}-${br[3]}` : `+${digitos}`;
}

/** "agora", "5 min", "3 h", "ontem", "4 d" ou a data (dd/MM) depois de uma semana. */
export function tempoRelativo(iso: string, agora: number = Date.now()): string {
  const segundos = Math.max(0, Math.floor((agora - new Date(iso).getTime()) / 1000));
  if (segundos < 60) return "agora";
  const minutos = Math.floor(segundos / 60);
  if (minutos < 60) return `${minutos} min`;
  const horas = Math.floor(minutos / 60);
  if (horas < 24) return `${horas} h`;
  const dias = Math.floor(horas / 24);
  if (dias === 1) return "ontem";
  if (dias < 7) return `${dias} d`;
  const data = new Date(iso);
  return `${String(data.getDate()).padStart(2, "0")}/${String(data.getMonth() + 1).padStart(2, "0")}`;
}

/** Prévia da última mensagem; quem falou por último (IA ou corretor) aparece como "Você:". */
export function previa(texto: string | null, origem: OrigemMensagem | null): string {
  if (!texto) return "Sem mensagens";
  return origem && origem !== "CLIENTE" ? `Você: ${texto}` : texto;
}

export function horaDaMensagem(iso: string): string {
  return new Date(iso).toLocaleTimeString("pt-BR", { hour: "2-digit", minute: "2-digit" });
}

export function diaDaMensagem(iso: string): string {
  return new Date(iso).toLocaleDateString("pt-BR", { day: "2-digit", month: "long" });
}

/** Mesmo limite do backend (bizu.conversa.limite-respostas). */
export const LIMITE_RESPOSTAS = 20;

export const ESTADOS: Record<EstadoConversa, { rotulo: string; ponto: string }> = {
  BOT: { rotulo: "IA respondendo", ponto: "bg-emerald-500" },
  HUMANO: { rotulo: "Com você", ponto: "bg-amber-500" },
  ENCERRADA: { rotulo: "Limite atingido", ponto: "bg-zinc-400 dark:bg-zinc-500" },
};
