import { User } from "lucide-react";

import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar";

/** Foto do contato no WhatsApp; sem foto (ou se a URL expirou), a inicial do nome ou um ícone. */
export function AvatarDoContato({
  nome,
  fotoUrl,
  size = "lg",
}: {
  nome: string | null;
  fotoUrl: string | null;
  size?: "default" | "sm" | "lg";
}) {
  const inicial = nome?.trim()[0]?.toUpperCase();
  return (
    <Avatar size={size}>
      {fotoUrl && <AvatarImage src={fotoUrl} alt="" referrerPolicy="no-referrer" />}
      <AvatarFallback>{inicial ?? <User className="size-4" aria-hidden />}</AvatarFallback>
    </Avatar>
  );
}
