"use client";

import { LogOut, Smartphone } from "lucide-react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import useSWR from "swr";
import { AvatarDoContato } from "@/components/conversas/avatar-do-contato";
import { Logo } from "@/components/painel/logo";
import { ITENS, useAtivo } from "@/components/painel/navegacao";
import { buscar, type Conta, type ConversaResumo, type SituacaoWhatsapp } from "@/lib/api";
import { limparSessao } from "@/lib/sessao";
import { cn } from "@/lib/utils";

const ROTULO = "px-1 text-[10px] font-semibold uppercase tracking-[0.18em] text-zinc-500";

/** Painel escuro flutuante (só no desktop): marca, situação do WhatsApp, navegação e a conta de quem está logado. */
export function BarraLateral() {
  const router = useRouter();
  const ativo = useAtivo();
  const { data: conta } = useSWR<Conta>("/api/conta", buscar, { refreshInterval: 60000 });
  const { data: whatsapp, error: erroWhatsapp } = useSWR<SituacaoWhatsapp>("/api/whatsapp", buscar, {
    refreshInterval: 15000,
  });
  const { data: conversas } = useSWR<ConversaResumo[]>("/api/conversas", buscar, { refreshInterval: 5000 });

  // Conversas "Com você" são as que esperam o corretor: é o número que vale destacar.
  const comVoce = conversas?.filter((c) => c.estado === "HUMANO").length ?? 0;
  const [ponto, situacao] = erroWhatsapp
    ? ["bg-zinc-500", "Sem resposta"]
    : !whatsapp
      ? ["bg-zinc-500", "Verificando…"]
      : whatsapp.conectado
        ? ["bg-emerald-500", "Conectado"]
        : ["bg-amber-500", "Desconectado"];

  const nome = conta?.nome || conta?.email || "";
  const sair = () => {
    limparSessao();
    router.replace("/login");
  };

  return (
    <aside className="hidden shrink-0 p-6 pr-0 md:flex">
      <div className="flex w-64 flex-col gap-5 rounded-3xl bg-zinc-950 p-4 text-zinc-50 ring-1 ring-white/10 dark:bg-zinc-900">
        <Link href="/conversas" className="flex flex-col gap-2 px-1 pt-1">
          <span className="flex items-center gap-3">
            <Logo className="size-9 bg-zinc-100 text-zinc-900" />
            <span className="text-xl font-semibold tracking-tight">Bizu</span>
          </span>
          <span className="text-[10px] font-semibold uppercase tracking-[0.18em] text-zinc-500">
            Atendimento no WhatsApp
          </span>
        </Link>

        <Link
          href="/conexao"
          className="flex items-center gap-3 rounded-2xl border border-zinc-800 bg-zinc-900/60 p-3 transition-colors hover:bg-zinc-800/60 dark:bg-zinc-800/40"
        >
          <span className="grid size-8 shrink-0 place-items-center rounded-lg bg-zinc-800 text-zinc-100">
            <Smartphone className="size-4" aria-hidden />
          </span>
          <span className="min-w-0 flex-1">
            <span className="block truncate text-sm font-semibold">Meu WhatsApp</span>
            <span className="block truncate text-xs text-zinc-400">{situacao}</span>
          </span>
          <span className={cn("size-2 shrink-0 rounded-full", ponto)} aria-hidden />
        </Link>

        <div className="flex flex-col gap-2">
          <p className={ROTULO}>Atendimento</p>
          <nav aria-label="Principal" className="flex flex-col gap-1">
            {ITENS.map(({ href, rotulo, Icone }) => (
              <Link
                key={href}
                href={href}
                aria-current={ativo(href) ? "page" : undefined}
                className={cn(
                  "flex items-center gap-3 rounded-xl px-3 py-2.5 text-sm font-medium text-zinc-400 transition-colors hover:bg-zinc-800/60 hover:text-zinc-50",
                  ativo(href) && "bg-zinc-800 text-zinc-50",
                )}
              >
                <Icone className="size-5" aria-hidden />
                {rotulo}
                {href === "/conversas" && comVoce > 0 && (
                  <span
                    title="Conversas com você"
                    className="ml-auto grid min-w-5 place-items-center rounded-md bg-zinc-700 px-1.5 text-[11px] font-semibold text-zinc-50"
                  >
                    {comVoce}
                  </span>
                )}
              </Link>
            ))}
          </nav>
        </div>

        <div className="mt-auto flex items-center gap-3 border-t border-zinc-800 pt-4">
          <AvatarDoContato nome={nome} fotoUrl={whatsapp?.fotoUrl ?? null} />
          <span className="min-w-0 flex-1">
            <span className="block truncate text-sm font-semibold">{conta?.nome || "Conta"}</span>
            <span className="block truncate text-xs text-zinc-400">{conta?.email}</span>
          </span>
          <button
            type="button"
            onClick={sair}
            aria-label="Sair"
            className="rounded-lg p-2 text-zinc-400 transition-colors hover:bg-zinc-800 hover:text-zinc-50 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-zinc-500"
          >
            <LogOut className="size-4" aria-hidden />
          </button>
        </div>
      </div>
    </aside>
  );
}
