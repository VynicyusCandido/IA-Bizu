import { MessageSquare } from "lucide-react";

export default function SelecioneUmaConversa() {
  return (
    <div className="grid h-full place-items-center p-6 text-center">
      <div className="flex flex-col items-center gap-2">
        <MessageSquare className="size-8 text-muted-foreground" aria-hidden />
        <p className="font-medium">Selecione uma conversa</p>
        <p className="max-w-xs text-sm text-muted-foreground">
          Escolha uma conversa da lista para ler o histórico e decidir se assume.
        </p>
      </div>
    </div>
  );
}
