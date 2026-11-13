"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useSyncExternalStore } from "react";
import { AlternarTema } from "@/components/painel/alternar-tema";
import { AvisoDeAssinatura } from "@/components/painel/aviso-de-assinatura";
import { BarraLateral } from "@/components/painel/barra-lateral";
import { EstadoWhatsapp } from "@/components/painel/estado-whatsapp";
import { Logo } from "@/components/painel/logo";
import { MenuDoUsuario } from "@/components/painel/menu-do-usuario";
import { NavegacaoInferior } from "@/components/painel/navegacao";
import { lerSessao } from "@/lib/sessao";

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

  return (
    <div className="flex h-dvh">
      <BarraLateral />

      <div className="flex min-w-0 flex-1 flex-col">
        <header className="flex h-14 shrink-0 items-center justify-between border-b px-4 md:justify-end md:border-b-0">
          <Link href="/conversas" className="flex items-center gap-2 md:hidden">
            <Logo className="size-7" />
            <span className="font-semibold tracking-tight">Bizu</span>
          </Link>
          <div className="flex items-center gap-1">
            <EstadoWhatsapp className="md:hidden" />
            <div className="md:hidden">
              <AlternarTema />
            </div>
            <div className="md:hidden">
              <MenuDoUsuario />
            </div>
          </div>
        </header>
        <AvisoDeAssinatura />
        <main className="min-h-0 flex-1">{children}</main>
        <NavegacaoInferior />
      </div>
    </div>
  );
}
