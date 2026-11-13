"use client";

import { useParams } from "next/navigation";
import { ListaDeConversas } from "@/components/conversas/lista-de-conversas";
import { cn } from "@/lib/utils";

/** Cada lado é uma caixa com as cores de cartão do shadcn (fundo, borda e texto do tema claro e escuro). */
const CAIXA = "min-w-0 flex-col overflow-hidden rounded-xl border bg-card text-card-foreground shadow-sm";

/** Desktop: lista e conversa lado a lado. Celular: uma de cada vez. */
export default function LayoutDasConversas({ children }: { children: React.ReactNode }) {
  const { id } = useParams<{ id?: string }>();
  return (
    <div className="flex h-full gap-4 px-4 pb-4 md:pr-6 md:pb-6">
      <section className={cn(CAIXA, "w-full md:flex md:w-96 md:shrink-0", id ? "hidden" : "flex")}>
        <ListaDeConversas />
      </section>
      <section className={cn(CAIXA, "flex-1 md:flex", id ? "flex" : "hidden")}>{children}</section>
    </div>
  );
}
