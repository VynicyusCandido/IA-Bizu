"use client";

import { Bot, Hand } from "lucide-react";
import { toast } from "sonner";
import { useSWRConfig } from "swr";
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
  AlertDialogTrigger,
} from "@/components/ui/alert-dialog";
import { Button } from "@/components/ui/button";
import { assumirConversa, devolverConversa, type EstadoConversa } from "@/lib/api";

/** Assumir (a IA para) ou devolver à IA, sempre com uma confirmação curta. */
export function AcaoDaConversa({ id, estado }: { id: string; estado: EstadoConversa }) {
  const { mutate } = useSWRConfig();
  const devolver = estado === "HUMANO";

  async function executar() {
    try {
      await (devolver ? devolverConversa(id) : assumirConversa(id));
      await Promise.all([mutate("/api/conversas"), mutate(`/api/conversas/${id}/mensagens`)]);
      toast.success(devolver ? "Conversa devolvida à IA." : "Você assumiu a conversa. A IA ficou em silêncio.");
    } catch {
      toast.error("Não foi possível concluir. Tente de novo.");
    }
  }

  return (
    <AlertDialog>
      <AlertDialogTrigger asChild>
        <Button variant={devolver ? "outline" : "default"}>
          {devolver ? <Bot /> : <Hand />}
          {devolver ? "Devolver à IA" : "Assumir conversa"}
        </Button>
      </AlertDialogTrigger>
      <AlertDialogContent>
        <AlertDialogHeader>
          <AlertDialogTitle>{devolver ? "Devolver à IA?" : "Assumir esta conversa?"}</AlertDialogTitle>
          <AlertDialogDescription>
            {devolver
              ? "A IA volta a responder as próximas mensagens deste cliente."
              : "A IA para de responder este cliente. Você continua pelo seu WhatsApp."}
          </AlertDialogDescription>
        </AlertDialogHeader>
        <AlertDialogFooter>
          <AlertDialogCancel>Cancelar</AlertDialogCancel>
          <AlertDialogAction onClick={executar}>{devolver ? "Devolver" : "Assumir"}</AlertDialogAction>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  );
}
