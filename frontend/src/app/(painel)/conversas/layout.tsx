"use client";

import { useParams } from "next/navigation";
import { ListaDeConversas } from "@/components/conversas/lista-de-conversas";
import { cn } from "@/lib/utils";

/** Desktop: lista e conversa lado a lado. Celular: uma de cada vez. */
export default function LayoutDasConversas({ children }: { children: React.ReactNode }) {
  const { id } = useParams<{ id?: string }>();
  return (
    <div className="flex h-full">
      <section className={cn("w-full min-w-0 flex-col border-r md:flex md:w-96 md:shrink-0", id ? "hidden" : "flex")}>
        <ListaDeConversas />
      </section>
      <section className={cn("min-w-0 flex-1 flex-col md:flex", id ? "flex" : "hidden")}>{children}</section>
    </div>
  );
}
