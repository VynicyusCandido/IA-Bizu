"use client";

import { LogOut } from "lucide-react";
import { useRouter } from "next/navigation";
import useSWR from "swr";
import { Avatar, AvatarFallback } from "@/components/ui/avatar";
import { Button } from "@/components/ui/button";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { buscar, type Conta } from "@/lib/api";
import { limparSessao } from "@/lib/sessao";

export function MenuDoUsuario() {
  const router = useRouter();
  const { data: conta } = useSWR<Conta>("/api/conta", buscar, { refreshInterval: 60000 });
  const nome = conta?.nome || conta?.email || "";
  const sair = () => {
    limparSessao();
    router.replace("/login");
  };
  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <Button variant="ghost" size="icon" aria-label="Menu da conta">
          <Avatar size="sm">
            <AvatarFallback>{(nome[0] ?? "?").toUpperCase()}</AvatarFallback>
          </Avatar>
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
