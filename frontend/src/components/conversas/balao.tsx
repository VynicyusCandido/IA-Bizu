import type { Mensagem } from "@/lib/api";
import { horaDaMensagem } from "@/lib/formatar";
import { cn } from "@/lib/utils";

const ESTILO = {
  CLIENTE: { lado: "items-start", balao: "bg-card border", rotulo: null },
  IA: { lado: "items-end", balao: "bg-secondary text-secondary-foreground", rotulo: "IA" },
  CORRETOR: { lado: "items-end", balao: "bg-primary text-primary-foreground", rotulo: "Você" },
} as const;

/** Cliente à esquerda; IA e corretor à direita, com um rótulo discreto de quem falou. */
export function Balao({ mensagem }: { mensagem: Mensagem }) {
  const { lado, balao, rotulo } = ESTILO[mensagem.origem];
  return (
    <div className={cn("flex flex-col gap-1", lado)}>
      <div className={cn("max-w-[85%] rounded-2xl px-3.5 py-2 text-sm md:max-w-[70%]", balao)}>
        <p className="whitespace-pre-wrap break-words">{mensagem.texto}</p>
      </div>
      <span className="px-1 text-[11px] text-muted-foreground">
        {rotulo ? `${rotulo} · ` : ""}
        {horaDaMensagem(mensagem.criadaEm)}
      </span>
    </div>
  );
}
