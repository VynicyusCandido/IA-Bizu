import { describe, expect, it } from "vitest";
import { formatarTelefone, previa, tempoRelativo, tituloDaConversa } from "@/lib/formatar";

describe("formatarTelefone", () => {
  it("formata celular e fixo do Brasil", () => {
    expect(formatarTelefone("5511999990001")).toBe("+55 11 99999-0001");
    expect(formatarTelefone("551133334444")).toBe("+55 11 3333-4444");
  });

  it("deixa números de fora do Brasil só com o +", () => {
    expect(formatarTelefone("14155550123")).toBe("+14155550123");
  });
});

describe("tituloDaConversa", () => {
  it("prefere o nome do contato", () => {
    expect(tituloDaConversa("Ana Souza", "5511999990001")).toBe("Ana Souza");
  });

  it("sem nome mostra o telefone, e sem telefone avisa que não há número", () => {
    expect(tituloDaConversa(null, "5511999990001")).toBe("+55 11 99999-0001");
    expect(tituloDaConversa("  ", "5511999990001")).toBe("+55 11 99999-0001");
    expect(tituloDaConversa(null, "98765@lid")).toBe("Contato sem número");
    expect(formatarTelefone("98765@lid")).toBe("Sem número");
  });
});

describe("tempoRelativo", () => {
  const agora = Date.parse("2026-11-09T12:00:00Z");
  const antes = (segundos: number) => new Date(agora - segundos * 1000).toISOString();

  it("usa agora, minutos, horas, ontem e dias", () => {
    expect(tempoRelativo(antes(20), agora)).toBe("agora");
    expect(tempoRelativo(antes(5 * 60), agora)).toBe("5 min");
    expect(tempoRelativo(antes(3 * 3600), agora)).toBe("3 h");
    expect(tempoRelativo(antes(30 * 3600), agora)).toBe("ontem");
    expect(tempoRelativo(antes(4 * 86400), agora)).toBe("4 d");
  });

  it("não mostra tempo negativo se o relógio do servidor estiver à frente", () => {
    expect(tempoRelativo(antes(-30), agora)).toBe("agora");
  });
});

describe("previa", () => {
  it("marca como 'Você:' o que a IA ou o corretor disseram por último", () => {
    expect(previa("Olá", "CLIENTE")).toBe("Olá");
    expect(previa("Já te respondo", "IA")).toBe("Você: Já te respondo");
    expect(previa("Combinado", "CORRETOR")).toBe("Você: Combinado");
  });

  it("avisa quando não há mensagens", () => {
    expect(previa(null, null)).toBe("Sem mensagens");
  });
});
