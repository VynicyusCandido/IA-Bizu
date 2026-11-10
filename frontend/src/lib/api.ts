import { lerSessao, limparSessao, salvarSessao } from "@/lib/sessao";

export const API_URL = process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080";

/** SEM_IA: aparece no painel, mas a IA nunca atende (contato salvo ou contato sem número conhecido). */
export type EstadoConversa = "BOT" | "HUMANO" | "ENCERRADA" | "SEM_IA";
export type OrigemMensagem = "CLIENTE" | "IA" | "CORRETOR";

export type ConversaResumo = {
  id: string;
  telefone: string;
  estado: EstadoConversa;
  respostasIa: number;
  ultimaAtividadeEm: string;
  finalidade: string | null;
  regiao: string | null;
  faixaValor: string | null;
  prazo: string | null;
  imovelCitado: string | null;
  ultimaMensagem: string | null;
  ultimaOrigem: OrigemMensagem | null;
  /** Nome que o WhatsApp informa do contato, se houver. */
  nome: string | null;
};

export type EstadoAssinatura = "ATIVA" | "AVISO" | "CARENCIA" | "VENCIDA";
export type MotivoDaPausa = "CONTA_INATIVA" | "ASSINATURA_VENCIDA" | "LIMITE_MENSAL";
export type Conta = {
  nome: string | null;
  email: string;
  assinaturaAte: string | null;
  estadoAssinatura: EstadoAssinatura;
  iaAtiva: boolean;
  /** Nulo quando a IA está respondendo; senão, por que ela parou. */
  pausaPor: MotivoDaPausa | null;
  respostasNoMes: number;
  limiteDoMes: number;
};

export type Mensagem ={ origem: OrigemMensagem; texto: string; criadaEm: string };
export type Prompt = { base: string; estilo: string | null; regrasFixas: string };
export type SituacaoWhatsapp = {
  conectado: boolean;
  qrCodeBase64: string | null;
  codigoDeEmparelhamento: string | null;
};

export class ErroApi extends Error {
  constructor(public status: number) {
    super(`Erro ${status}`);
  }
}

/** Chamada autenticada à API. Em 401 a sessão acabou: limpa e volta ao login. */
export async function chamar<T>(caminho: string, init?: RequestInit): Promise<T> {
  const sessao = lerSessao();
  const resposta = await fetch(`${API_URL}${caminho}`, {
    ...init,
    headers: {
      "Content-Type": "application/json",
      ...(sessao ? { Authorization: `Bearer ${sessao.token}` } : {}),
    },
  });
  if (resposta.status === 401) {
    limparSessao();
    // Navegação completa de propósito: zera o cache do SWR junto com a sessão.
    // eslint-disable-next-line @next/next/no-location-assign-relative-destination
    if (typeof window !== "undefined") window.location.href = "/login";
  }
  if (!resposta.ok) throw new ErroApi(resposta.status);
  const texto = await resposta.text();
  return (texto ? JSON.parse(texto) : undefined) as T;
}

export const buscar = <T>(caminho: string) => chamar<T>(caminho);

/** Cria a instância do corretor na primeira vez e pede o QR code; só a tela Conexão chama. */
export const conectarWhatsapp = () => chamar<SituacaoWhatsapp>("/api/whatsapp/conectar", { method: "POST" });

/** Traz as conversas da última semana que já existiam no WhatsApp. Pode repetir: não duplica nada. */
export const importarConversas = () =>
  chamar<{ conversas: number; mensagens: number }>("/api/conversas/importar", { method: "POST" });

export const assumirConversa = (id: string) => chamar(`/api/conversas/${id}/assumir`, { method: "POST" });
export const devolverConversa = (id: string) => chamar(`/api/conversas/${id}/devolver`, { method: "POST" });
export const salvarPrompt = (base: string) =>
  chamar<Prompt>("/api/prompt", { method: "PUT", body: JSON.stringify({ base }) });
export const gerarEstilo = () => chamar<{ gerado: boolean }>("/api/estilo/gerar", { method: "POST" });

export type ResultadoDoLogin = { ok: true } | { ok: false; status: number; motivo?: string };

/**
 * Troca o ID token do Google pela sessão do Bizu. Conta nova ou ainda não liberada volta 403 com
 * motivo AGUARDANDO_LIBERACAO; outros erros voltam só o status HTTP (0 = sem resposta do servidor).
 */
export async function entrarComGoogle(idToken: string): Promise<ResultadoDoLogin> {
  try {
    const resposta = await fetch(`${API_URL}/api/auth/google`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ idToken }),
    });
    if (!resposta.ok) {
      const corpo = (await resposta.json().catch(() => null)) as { motivo?: string } | null;
      return { ok: false, status: resposta.status, motivo: corpo?.motivo };
    }
    salvarSessao((await resposta.json()) as { token: string; expiraEm: string });
    return { ok: true };
  } catch {
    return { ok: false, status: 0 };
  }
}
