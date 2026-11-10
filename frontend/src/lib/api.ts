import { lerSessao, limparSessao, salvarSessao } from "@/lib/sessao";

export const API_URL = process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080";

export type EstadoConversa = "BOT" | "HUMANO" | "ENCERRADA";
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
};

export type Mensagem = { origem: OrigemMensagem; texto: string; criadaEm: string };
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

export const assumirConversa = (id: string) => chamar(`/api/conversas/${id}/assumir`, { method: "POST" });
export const devolverConversa = (id: string) => chamar(`/api/conversas/${id}/devolver`, { method: "POST" });
export const salvarPrompt = (base: string) =>
  chamar<Prompt>("/api/prompt", { method: "PUT", body: JSON.stringify({ base }) });
export const gerarEstilo = () => chamar<{ gerado: boolean }>("/api/estilo/gerar", { method: "POST" });

/** Troca o ID token do Google pela sessão do Bizu. Devolve o status HTTP quando o backend recusa. */
export async function entrarComGoogle(idToken: string): Promise<{ ok: true } | { ok: false; status: number }> {
  try {
    const resposta = await fetch(`${API_URL}/api/auth/google`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ idToken }),
    });
    if (!resposta.ok) return { ok: false, status: resposta.status };
    salvarSessao((await resposta.json()) as { token: string; expiraEm: string });
    return { ok: true };
  } catch {
    return { ok: false, status: 0 };
  }
}
