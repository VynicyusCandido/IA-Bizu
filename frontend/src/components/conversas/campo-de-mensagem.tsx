"use client";

import { Paperclip, SendHorizontal, X } from "lucide-react";
import { useRef, useState } from "react";
import { toast } from "sonner";
import { useSWRConfig } from "swr";
import { Button } from "@/components/ui/button";
import { Textarea } from "@/components/ui/textarea";
import { enviarAnexo, ErroApi, responderConversa } from "@/lib/api";

const LIMITE_DO_ANEXO = 10 * 1024 * 1024;

/**
 * Caixa de resposta: Enter envia, Shift+Enter quebra a linha. O clipe anexa imagem, vídeo ou documento (até 10 MB);
 * com um anexo escolhido, o texto vira a legenda. Enviar pelo painel assume a conversa (a IA para).
 */
export function CampoDeMensagem({ id }: { id: string }) {
  const { mutate } = useSWRConfig();
  const [texto, setTexto] = useState("");
  const [anexo, setAnexo] = useState<File | null>(null);
  const [enviando, setEnviando] = useState(false);
  const seletor = useRef<HTMLInputElement>(null);

  function escolher(arquivo: File | undefined) {
    if (!arquivo) return;
    if (arquivo.size > LIMITE_DO_ANEXO) {
      toast.error("O arquivo passa de 10 MB. Escolha um menor.");
      return;
    }
    setAnexo(arquivo);
  }

  async function enviar() {
    const limpo = texto.trim();
    if ((!limpo && !anexo) || enviando) return;
    setEnviando(true);
    try {
      await (anexo ? enviarAnexo(id, anexo, limpo) : responderConversa(id, limpo));
      setTexto("");
      setAnexo(null);
      await Promise.all([mutate("/api/conversas"), mutate(`/api/conversas/${id}/mensagens`)]);
    } catch (erro) {
      toast.error(
        erro instanceof ErroApi && erro.status === 502
          ? "O WhatsApp não aceitou a mensagem. Confira se ele está conectado e tente de novo."
          : "Não foi possível enviar. Tente de novo.",
      );
    } finally {
      setEnviando(false);
    }
  }

  return (
    <div className="flex flex-col gap-2 border-t p-3">
      {anexo && (
        <div className="flex items-center gap-2 self-start rounded-lg border bg-muted px-2.5 py-1 text-xs">
          <Paperclip className="size-3.5 shrink-0 text-muted-foreground" aria-hidden />
          <span className="max-w-60 truncate">{anexo.name}</span>
          <button
            type="button"
            onClick={() => setAnexo(null)}
            aria-label="Remover anexo"
            className="rounded p-0.5 text-muted-foreground transition-colors hover:text-foreground"
          >
            <X className="size-3.5" aria-hidden />
          </button>
        </div>
      )}
      <div className="flex items-end gap-2">
        <Textarea
          value={texto}
          onChange={(e) => setTexto(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === "Enter" && !e.shiftKey) {
              e.preventDefault();
              void enviar();
            }
          }}
          placeholder={anexo ? "Legenda (opcional)…" : "Escreva uma resposta…"}
          aria-label="Mensagem"
          rows={1}
          maxLength={4000}
          className="max-h-32 min-h-10 resize-none"
        />
        <input
          ref={seletor}
          type="file"
          hidden
          accept="image/*,video/*,.pdf,.doc,.docx,.xls,.xlsx,.ppt,.pptx,.txt"
          onChange={(e) => {
            escolher(e.target.files?.[0]);
            e.target.value = "";
          }}
        />
        <Button
          type="button"
          variant="ghost"
          size="icon"
          onClick={() => seletor.current?.click()}
          disabled={enviando}
          aria-label="Anexar arquivo"
          title="Anexar arquivo"
          className="text-muted-foreground hover:text-primary"
        >
          <Paperclip />
        </Button>
        <Button
          onClick={enviar}
          disabled={(!texto.trim() && !anexo) || enviando}
          aria-label="Enviar mensagem"
          size="icon"
        >
          <SendHorizontal />
        </Button>
      </div>
    </div>
  );
}
