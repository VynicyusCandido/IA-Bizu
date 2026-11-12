"use client";

import { LogOut } from "lucide-react";
import { useRouter } from "next/navigation";
import useSWR from "swr";
import { AvatarDoContato } from "@/components/conversas/avatar-do-contato";
import { Button } from "@/components/ui/button";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { buscar, type Conta, type SituacaoWhatsapp } from "@/lib/api";
import { limparSessao } from "@/lib/sessao";

export function MenuDoUsuario() {
  const router = useRouter();
  const { data: conta } = useSWR<Conta>("/api/conta", buscar, { refreshInterval: 60000 });
  const { data: whatsapp } = useSWR<SituacaoWhatsapp>("/api/whatsapp", buscar, { refreshInterval: 15000 });
  const nome = conta?.nome || conta?.email || "";
  const sair = () => {
    limparSessao();
    router.replace("/login");
  };
  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <Button variant="ghost" size="icon" aria-label="Menu da conta">
          <AvatarDoContato size="sm" nome={nome} fotoUrl={whatsapp?.fotoUrl ?? null} />
        </Button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end" className="min-w-52">
        <DropdownMenuLabel className="font-normal">
          <p className="text-sm font-medium text-foreground">{conta?.nome || "Conta"}</p>
          <p className="text-xs text-muted-foreground">{conta?.email}</p>
        </DropdownMenuLabel>
        <DropdownMenuSeparator />
        <DropdownMenuItem onClick={sair}>
          <LogOut /> Sair
        </DropdownMenuItem>
      </DropdownMenuContent>
    </DropdownMenu>
  );
}
