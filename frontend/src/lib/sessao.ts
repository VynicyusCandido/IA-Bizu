const CHAVE = "bizu.sessao";

export type Sessao = { token: string; expiraEm: string };

/** Lê a sessão guardada no navegador. Devolve null se não há, se expirou ou se o armazenamento está bloqueado. */
export function lerSessao(): Sessao | null {
  try {
    const bruto = localStorage.getItem(CHAVE);
    if (!bruto) return null;
    const sessao = JSON.parse(bruto) as Sessao;
    if (!sessao.token || !(new Date(sessao.expiraEm).getTime() > Date.now())) {
      localStorage.removeItem(CHAVE);
      return null;
    }
    return sessao;
  } catch {
    return null;
  }
}

export function salvarSessao(sessao: Sessao): void {
  try {
    localStorage.setItem(CHAVE, JSON.stringify(sessao));
  } catch {
    // Sem armazenamento (aba privada): a sessão vale só até recarregar.
  }
}

export function limparSessao(): void {
  try {
    localStorage.removeItem(CHAVE);
  } catch {
    // nada a limpar
  }
}
