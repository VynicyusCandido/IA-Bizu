"use client";

import { MessageSquare, Smartphone, Sparkles } from "lucide-react";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { cn } from "@/lib/utils";

export const ITENS = [
  { href: "/conversas", rotulo: "Conversas", Icone: MessageSquare },
  { href: "/agente", rotulo: "Agente", Icone: Sparkles },
  { href: "/conexao", rotulo: "Conexão", Icone: Smartphone },
];

export function useAtivo() {
  const caminho = usePathname();
  return (href: string) => caminho === href || caminho.startsWith(`${href}/`);
}

/** Barra inferior (celular). No desktop a navegação fica na barra lateral. */
export function NavegacaoInferior() {
  const ativo = useAtivo();
  return (
    <nav aria-label="Principal" className="grid grid-cols-3 border-t bg-background md:hidden">
      {ITENS.map(({ href, rotulo, Icone }) => (
        <Link
          key={href}
          href={href}
          aria-current={ativo(href) ? "page" : undefined}
          className={cn(
            "flex flex-col items-center gap-1 py-2.5 text-[11px] font-medium text-muted-foreground",
            ativo(href) && "text-foreground",
          )}
        >
          <Icone className="size-5" aria-hidden />
          {rotulo}
        </Link>
      ))}
    </nav>
  );
}
