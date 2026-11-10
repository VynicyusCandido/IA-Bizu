import { beforeEach, describe, expect, it, vi } from "vitest";
import { emailDaSessao, lerSessao, salvarSessao } from "@/lib/sessao";

function armazenamentoFalso() {
  const dados = new Map<string, string>();
  return {
    getItem: (k: string) => dados.get(k) ?? null,
    setItem: (k: string, v: string) => void dados.set(k, v),
    removeItem: (k: string) => void dados.delete(k),
  };
}

describe("sessão", () => {
  beforeEach(() => {
    vi.stubGlobal("localStorage", armazenamentoFalso());
  });

  it("devolve a sessão enquanto não expirou", () => {
    salvarSessao({ token: "t", expiraEm: new Date(Date.now() + 60_000).toISOString() });
    expect(lerSessao()?.token).toBe("t");
  });

  it("descarta a sessão expirada", () => {
    salvarSessao({ token: "t", expiraEm: new Date(Date.now() - 1000).toISOString() });
    expect(lerSessao()).toBeNull();
  });

  it("não quebra se o armazenamento estiver bloqueado", () => {
    vi.stubGlobal("localStorage", {
      getItem: () => {
        throw new Error("bloqueado");
      },
    });
    expect(lerSessao()).toBeNull();
  });

  it("lê o e-mail do campo sub do token", () => {
    const carga = btoa(JSON.stringify({ sub: "corretor@exemplo.com" })).replace(/=/g, "");
    expect(emailDaSessao({ token: `a.${carga}.c`, expiraEm: "" })).toBe("corretor@exemplo.com");
    expect(emailDaSessao({ token: "invalido", expiraEm: "" })).toBe("");
  });
});
