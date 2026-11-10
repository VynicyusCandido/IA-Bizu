"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useSyncExternalStore } from "react";
import { AlternarTema } from "@/components/painel/alternar-tema";
import { EstadoWhatsapp } from "@/components/painel/estado-whatsapp";
import { Logo } from "@/components/painel/logo";
import { MenuDoUsuario } from "@/components/painel/menu-do-usuario";
import { NavegacaoInferior, NavegacaoLateral } from "@/components/painel/navegacao";
import { emailDaSessao, lerSessao } from "@/lib/sessao";

/** Área logada: sem sessão válida volta ao login. */
export default function LayoutDoPainel({ children }: { children: React.ReactNode }) {
  const router = useRouter();
  // undefined = ainda no servidor/hidratando; null = sem sessão válida.
  const token = useSyncExternalStore(
    () => () => {},
    () => lerSessao()?.token ?? null,
    () => undefined,
  );

  useEffect(() => {
    if (token === null) router.replace("/login");
  }, [token, router]);

  if (!token) return null;
  const email = emailDaSessao({ token, expiraEm: "" });

  return (
    <div className="flex h-dvh">
      <aside className="hidden w-56 shrink-0 flex-col justify-between border-r bg-sidebar p-3 md:flex">
        <div className="flex flex-col gap-6">
          <Link href="/conversas" className="flex items-center gap-2 px-3 pt-2">
            <Logo />
            <span className="text-base font-semibold tracking-tight">Bizu</span>
          </Link>
          <NavegacaoLateral />
        </div>
        <EstadoWhatsapp />
      </aside>

      <div className="flex min-w-0 flex-1 flex-col">
        <header className="flex h-14 shrink-0 items-center justify-between border-b px-4">
          <Link href="/conversas" className="flex items-center gap-2 md:invisible">
            <Logo className="size-7" />
            <span className="font-semibold tracking-tight">Bizu</span>
          </Link>
          <div className="flex items-center gap-1">
            <EstadoWhatsapp className="md:hidden" />
            <AlternarTema />
            <MenuDoUsuario email={email} />
          </div>
        </header>
        <main className="min-h-0 flex-1">{children}</main>
        <NavegacaoInferior />
      </div>
    </div>
  );
}
