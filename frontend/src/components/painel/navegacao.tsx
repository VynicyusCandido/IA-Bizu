"use client";

import { MessageSquare, Smartphone, Sparkles } from "lucide-react";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { cn } from "@/lib/utils";

const ITENS = [
  { href: "/conversas", rotulo: "Conversas", Icone: MessageSquare },
  { href: "/agente", rotulo: "Agente", Icone: Sparkles },
  { href: "/conexao", rotulo: "Conexão", Icone: Smartphone },
];

function useAtivo() {
  const caminho = usePathname();
  return (href: string) => caminho === href || caminho.startsWith(`${href}/`);
}

/** Barra lateral (telas médias e grandes). */
export function NavegacaoLateral() {
  const ativo = useAtivo();
  return (
    <nav aria-label="Principal" className="flex flex-col gap-1">
      {ITENS.map(({ href, rotulo, Icone }) => (
        <Link
          key={href}
          href={href}
          aria-current={ativo(href) ? "page" : undefined}
          className={cn(
            "flex items-center gap-3 rounded-lg px-3 py-2 text-sm font-medium text-muted-foreground transition-colors hover:bg-muted hover:text-foreground",
            ativo(href) && "bg-muted text-foreground",
          )}
        >
          <Icone className="size-4" aria-hidden />
          {rotulo}
        </Link>
      ))}
    </nav>
  );
}

/** Barra inferior (celular). */
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
